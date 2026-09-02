package wlcp.bridge;

import doom.*;
import g.Signals.ScanCode;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.concurrent.ArrayBlockingQueue;

/** Private, bounded binary pipe protocol. No listeners or network ports. */
public final class Bridge {
    private static final DataOutputStream OUT = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(FileDescriptor.out), 320 * 240 * 4 + 16));
    private static final ArrayBlockingQueue<int[]> INPUT = new ArrayBlockingQueue<>(256);
    private static final byte[] PIXELS = new byte[320 * 240 * 4];
    private static final int[] RGB = new int[320 * 200];

    public static void start() {
        Thread.ofPlatform().daemon().name("doom-input").start(() -> {
            try (var in = new DataInputStream(new BufferedInputStream(System.in))) {
                while (true) {
                    int[] e = {in.readInt(), in.readInt(), in.readInt(), in.readInt()};
                    INPUT.put(e);
                }
            } catch (Exception closed) { System.exit(0); }
        });
    }
    public static void input(DoomMain<?, ?> game) {
        // The engine ring buffer has only 64 slots. Drain less than that per frame.
        for (int n = 0; n < 32; n++) {
            int[] e = INPUT.poll();
            if (e == null) break;
            if (e[0] == 1 && e[1] > 0 && e[1] < ScanCode.values().length)
                game.PostEvent(new event_t.keyevent_t(e[2] == 0 ? evtype_t.ev_keyup : evtype_t.ev_keydown, ScanCode.values()[e[1]]));
            else if (e[0] == 2) game.PostEvent(new event_t.mouseevent_t(evtype_t.ev_mouse, e[1], e[2], e[3]));
            else if (e[0] == 3) {
                // Cancel pending presses too: CANCEL_KEYS clears current state immediately,
                // before Doom would otherwise consume those pending presses next tic.
                game.eventtail = game.eventhead;
                game.PostEvent(event_t.CANCEL_KEYS);
                game.PostEvent(event_t.CANCEL_MOUSE);
            }
        }
    }
    public static void frame(Image image) {
        BufferedImage source = (BufferedImage) image;
        if (source.getWidth() != 320 || source.getHeight() != 200) throw new IllegalStateException("Unexpected Doom resolution");
        source.getRGB(0, 0, 320, 200, RGB, 0, 320);
        // Correct Doom's non-square 320x200 pixels to the original 4:3 display.
        for (int y = 0, p = 0; y < 240; y++) for (int x = 0; x < 320; x++) {
            int color = RGB[(y * 200 / 240) * 320 + x];
            PIXELS[p++] = (byte)(color >> 16); PIXELS[p++] = (byte)(color >> 8);
            PIXELS[p++] = (byte)color; PIXELS[p++] = (byte)255;
        }
        packet(1, PIXELS);
    }
    public static synchronized void packet(int kind, byte[] data) {
        try { OUT.writeInt(kind); OUT.writeInt(data.length); OUT.write(data); OUT.flush(); }
        catch (IOException disconnected) { System.exit(0); }
    }
    private Bridge() {}
}
