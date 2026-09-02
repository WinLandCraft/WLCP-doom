package dev.winlandcraft.doom;

import dev.winlandcraft.api.v2.*;
import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

final class DoomSession implements AutoCloseable {
    private final Path root, selected;
    private final FrameSurface frames;
    private final AudioOutput audio;
    private final Consumer<String> status;
    private final ArrayBlockingQueue<int[]> inputs = new ArrayBlockingQueue<>(256);
    private final CompletableFuture<Void> stopped = new CompletableFuture<>();
    private volatile boolean closed, receivedFrame;
    private volatile Process process;

    DoomSession(Path root, Path selected, FrameSurface frames, AudioOutput audio, Consumer<String> status) {
        this.root = root; this.selected = selected; this.frames = frames; this.audio = audio; this.status = status;
    }
    void start() { Thread.ofVirtual().name("doom-session").start(this::run); }
    void startAfter(DoomSession previous) {
        Thread.ofVirtual().name("doom-restart").start(() -> {
            if (previous != null) previous.stopped.join();
            run();
        });
    }
    boolean hasFrame() { return receivedFrame; }
    void input(int type, int a, int b, int c) {
        if (closed) return;
        if (!inputs.offer(new int[]{type, a, b, c})) {
            inputs.clear();
            inputs.offer(new int[]{3, 0, 0, 0}); // release held keys rather than sticking on overflow
        }
    }
    private void run() {
        try {
            if (closed) return;
            Path engine = Assets.extract(root, "doom-engine.jar");
            Path selectedWad = selected;
            if (selectedWad == null) {
                Path saved = root.resolve("last-wad.txt");
                if (Files.isRegularFile(saved) && Files.size(saved) < 16_384) {
                    Path last = Path.of(Files.readString(saved).strip());
                    if (Files.isRegularFile(last)) selectedWad = last;
                }
            }
            if (selectedWad == null) selectedWad = Assets.extract(root, "freedoom1.wad");
            Wad wad = Wad.inspect(selectedWad);
            if (closed) return;
            try (var slot = wad.acquire(root)) {
                Path localWad = slot.directory().resolve(wad.canonicalName());
                // Isolate game writes from the user's original WAD.
                if (!Files.isRegularFile(localWad) || !Assets.hash(Files.readAllBytes(localWad)).startsWith(wad.profile()))
                    Files.copy(wad.path(), localWad, StandardCopyOption.REPLACE_EXISTING);
                if (closed) return;
                String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
                Path java = Path.of(System.getProperty("java.home"), "bin", executable);
                Process child = new ProcessBuilder(java.toString(), "-Xms32m", "-Xmx256m", "-Djava.awt.headless=true",
                    "--add-exports=java.desktop/com.sun.media.sound=ALL-UNNAMED", "-jar", engine.toString(),
                    "-iwad", localWad.toAbsolutePath().toString(), "-multiply", "1", "-indexed", "-serialrenderer")
                    .directory(slot.directory().toFile()).redirectError(slot.directory().resolve("engine.log").toFile()).start();
                process = child;
                try {
                    if (closed) return;
                    if (selected != null) Files.writeString(root.resolve("last-wad.txt"), wad.path().toString());
                    Thread.ofVirtual().name("doom-key-writer").start(() -> writeInput(child));
                    Thread.ofVirtual().name("doom-startup-watchdog").start(() -> {
                        try {
                            if (!child.waitFor(45, TimeUnit.SECONDS) && !receivedFrame && !closed) {
                                status.accept("Doom startup timed out. Check engine.log in the save folder.");
                                child.destroyForcibly();
                            }
                        } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
                    });
                    readOutput(child.getInputStream());
                    if (!closed) status.accept("Doom exited. Click to restart; details are in engine.log.");
                } finally {
                    child.destroy();
                    if (!child.waitFor(750, TimeUnit.MILLISECONDS)) child.destroyForcibly().waitFor();
                }
            }
        } catch (Exception error) {
            if (!closed) status.accept("Could not start Doom: " + error.getMessage());
        } finally { closed = true; inputs.clear(); stopped.complete(null); }
    }
    private void writeInput(Process child) {
        try (var out = new DataOutputStream(new BufferedOutputStream(child.getOutputStream()))) {
            while (!closed && child.isAlive()) {
                int[] event = inputs.poll(100, TimeUnit.MILLISECONDS);
                if (event == null) continue;
                for (int value : event) out.writeInt(value);
                out.flush();
            }
        } catch (Exception ignored) { /* output reader reports process failure */ }
    }
    private void readOutput(InputStream stream) throws IOException {
        try (var in = new DataInputStream(new BufferedInputStream(stream, 320 * 240 * 4 + 8))) {
            byte[] pixels = new byte[320 * 240 * 4];
            byte[] pcm = new byte[480 * 2 * 4];
            float[] samples = new float[480 * 2];
            while (!closed) {
                int kind;
                try { kind = in.readInt(); } catch (EOFException end) { break; }
                int length = in.readInt();
                if (kind == 1 && length == pixels.length) {
                    in.readFully(pixels);
                    if (!closed && !frames.submit(ByteBuffer.wrap(pixels), 320, 240, 1280, PixelFormat.RGBA8)) break;
                    if (!receivedFrame) { receivedFrame = true; status.accept(""); }
                } else if (kind == 2 && length == pcm.length) {
                    in.readFully(pcm);
                    ByteBuffer.wrap(pcm).order(ByteOrder.BIG_ENDIAN).asFloatBuffer().get(samples);
                    if (!closed) audio.submit(samples); // rejection drops stale audio; never retry
                } else throw new IOException("Invalid engine media packet: " + kind + "/" + length);
            }
        }
    }
    public void close() {
        closed = true; inputs.clear();
        Process child = process;
        if (child != null) {
            child.destroy();
            Thread.ofVirtual().name("doom-stop").start(() -> {
                try { if (!child.waitFor(750, TimeUnit.MILLISECONDS)) child.destroyForcibly(); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            });
        }
    }
}
