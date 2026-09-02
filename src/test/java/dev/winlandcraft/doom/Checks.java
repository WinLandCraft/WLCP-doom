package dev.winlandcraft.doom;

import dev.winlandcraft.api.v2.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;
import java.util.jar.JarFile;
import javax.imageio.ImageIO;

public final class Checks {
    static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    static void until(BooleanSupplier test, int seconds, String message) throws Exception {
        long end = System.nanoTime() + seconds * 1_000_000_000L;
        while (!test.getAsBoolean() && System.nanoTime() < end) Thread.sleep(30);
        require(test.getAsBoolean(), message);
    }
    public static void main(String[] args) throws Exception {
        try (JarFile jar = new JarFile(args[0])) {
            require(jar.getEntry("runtime/doom-engine.jar") != null, "Engine must be bundled");
            require(jar.getEntry("runtime/freedoom1.wad") != null, "Freedoom must be bundled");
            require(jar.stream().noneMatch(e -> e.getName().startsWith("dev/winlandcraft/api/")), "Do not bundle host API");
            require(jar.stream().noneMatch(e -> e.getName().startsWith("doom/")), "Engine must remain isolated");
        }
        require(KeyMap.scan(256) == 0, "Host Escape must not be forwarded");
        require(KeyMap.scan(301) == 1, "F12 must open Doom menu");
        Path reports = Path.of("build/verification").toAbsolutePath();
        Files.createDirectories(reports);
        Path root = Files.createTempDirectory(reports, "session-");
        Path wadPath = Assets.extract(root, "freedoom1.wad");
        Wad wad = Wad.inspect(wadPath);
        require(wad.canonicalName().equals("freedoom1.wad"), "Identify Freedoom");
        try (var first = wad.acquire(root); var second = wad.acquire(root)) {
            require(!first.directory().equals(second.directory()), "Windows must have separate save slots");
        }
        Path bad = root.resolve("broken.wad");
        Files.write(bad, new byte[]{'P','W','A','D', 0,0,0,0, 12,0,0,0});
        try { Wad.inspect(bad); throw new AssertionError("PWAD accepted"); } catch (IOException expected) {}
        Files.write(bad, new byte[]{'I','W','A','D', 127,127,127,127, 12,0,0,0});
        try { Wad.inspect(bad); throw new AssertionError("Invalid directory accepted"); } catch (IOException expected) {}

        Capture capture = new Capture();
        AtomicReference<String> status = new AtomicReference<>("waiting");
        Set<Long> oldChildren = new HashSet<>();
        ProcessHandle.current().descendants().forEach(p -> oldChildren.add(p.pid()));
        DoomSession session = new DoomSession(root, wadPath, capture, capture, text -> { status.set(text); System.out.println("Engine status: " + text); });
        try {
            session.start();
            until(() -> capture.count.get() >= 3, 45, "No engine frames. Status=" + status.get() + "; logs under " + root);
            capture.save(reports.resolve("title.png"));
            // Open menu -> new game -> episode 1 -> Hurt Me Plenty.
            press(session, 1); press(session, 28); press(session, 28); press(session, 28);
            Thread.sleep(2000);
            capture.save(reports.resolve("gameplay.png"));
            byte[] before = capture.latest.clone();
            session.input(1, 17, 1, 0); // W
            session.input(1, 106, 1, 0); // turn right
            Thread.sleep(800);
            session.input(3, 0, 0, 0);
            capture.save(reports.resolve("moved.png"));
            require(!Arrays.equals(before, capture.latest), "Movement must change the frame");
            until(() -> capture.audible.get() > 10, 10, "No audible PCM produced");
            int audioBlocks = capture.audioCount.get();
            int videoFrames = capture.count.get();
            require(capture.failure.get() == null, "Media validation failed: " + capture.failure.get());
            // Exercise save menu and confirm a real save file, not just animation changes.
            press(session, 60); // F2
            press(session, 28);
            press(session, 20); press(session, 18); press(session, 31); press(session, 20); // TEST
            press(session, 28);
            until(() -> hasSave(root), 5, "Doom save was not created");
            System.out.println("PASS: " + videoFrames + " frames, " + audioBlocks + " stereo PCM blocks, movement and save.");
        } finally { session.close(); }
        until(() -> ProcessHandle.current().descendants().noneMatch(p -> !oldChildren.contains(p.pid())), 5, "Engine process leaked after close");
        int stoppedCount = capture.count.get();
        Thread.sleep(150);
        require(stoppedCount == capture.count.get(), "Frames after shutdown");
        try (var slot = wad.acquire(root)) {
            require(slot.directory().getFileName().toString().equals("player-1"), "Save lock leaked");
        }
        // The same saved profile must start a fresh engine after close.
        Capture reopened = new Capture();
        try (DoomSession next = new DoomSession(root, wadPath, reopened, reopened, status::set)) {
            next.start(); until(() -> reopened.count.get() >= 2, 30, "Reopen failed");
            press(next, 61); press(next, 28); // F3, load the saved slot
            int beforeLoad = reopened.count.get();
            until(() -> reopened.count.get() > beforeLoad + 35, 5, "Engine stopped while loading the save");
            reopened.save(reports.resolve("loaded-save.png"));
        }
        until(() -> ProcessHandle.current().descendants().noneMatch(p -> !oldChildren.contains(p.pid())), 5, "Reopened process leaked");
        System.out.println("PASS: packaging, WAD validation, slot isolation, process cleanup, reopen and load.");
    }
    static boolean hasSave(Path root) {
        try (var files = Files.walk(root.resolve("saves"))) { return files.anyMatch(p -> p.toString().endsWith(".dsg")); }
        catch (IOException e) { return false; }
    }
    static void press(DoomSession session, int scan) throws InterruptedException {
        session.input(1, scan, 1, 0); Thread.sleep(100);
        session.input(1, scan, 0, 0); Thread.sleep(220);
    }
    static final class Capture implements FrameSurface, AudioOutput {
        final AtomicInteger count = new AtomicInteger(), audioCount = new AtomicInteger(), audible = new AtomicInteger();
        final AtomicReference<String> failure = new AtomicReference<>();
        volatile byte[] latest;
        public boolean submit(ByteBuffer data, int width, int height, int stride, PixelFormat format) {
            if (width != 320 || height != 240 || stride != 1280 || format != PixelFormat.RGBA8) failure.set("Wrong frame format");
            byte[] copy = new byte[data.remaining()]; data.duplicate().get(copy);
            for (int i = 3; i < copy.length; i += 4) if ((copy[i] & 255) != 255) failure.set("Non-opaque frame");
            latest = copy; count.incrementAndGet(); return true;
        }
        public boolean submit(float[] data) {
            if (data.length != 960) failure.set("Wrong audio block size");
            boolean sound = false;
            for (float sample : data) {
                if (!Float.isFinite(sample) || Math.abs(sample) > 1) failure.set("Invalid PCM");
                if (Math.abs(sample) > 0.001f) sound = true;
            }
            if (sound) audible.incrementAndGet(); audioCount.incrementAndGet(); return true;
        }
        public void localPlayback(boolean enabled) {}
        public void clear() {}
        void save(Path path) throws IOException {
            byte[] frame = latest;
            BufferedImage image = new BufferedImage(320, 240, BufferedImage.TYPE_INT_RGB);
            for (int y = 0, i = 0; y < 240; y++) for (int x = 0; x < 320; x++, i += 4)
                image.setRGB(x, y, (frame[i] & 255) << 16 | (frame[i+1] & 255) << 8 | frame[i+2] & 255);
            ImageIO.write(image, "PNG", path.toFile());
        }
    }
}
