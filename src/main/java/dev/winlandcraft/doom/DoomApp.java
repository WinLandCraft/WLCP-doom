package dev.winlandcraft.doom;

import dev.winlandcraft.api.v2.*;
import java.nio.file.Path;
import java.util.*;

public final class DoomApp implements App {
    private WindowContext window;
    private FrameSurface frames;
    private AudioOutput audio;
    private DoomSession session;
    private Path selected;
    private String message = "Starting DOOM...";
    private boolean focused, closed;
    private int mouseX = -1, buttons;
    private final Map<Integer, Integer> pressed = new HashMap<>();

    public void onOpen(WindowContext window) {
        this.window = window; frames = window.frames(); audio = window.audio();
        window.title("DOOM");
        restart();
    }
    private void restart() {
        DoomSession previous = session;
        if (previous != null) previous.close();
        frames.clear(); audio.clear(); pressed.clear(); buttons = 0; mouseX = -1;
        message = "Starting DOOM...";
        DoomSession[] current = new DoomSession[1];
        current[0] = new DoomSession(window.dataDirectory(), selected, frames, audio,
            text -> window.execute(() -> {
                if (!closed && session == current[0]) message = text;
            }));
        session = current[0]; session.startAfter(previous);
    }
    public void openFile(Path path) { selected = path; restart(); }
    public void onClose() { closed = true; if (session != null) session.close(); }
    public void render(Canvas c) {
        if (!message.isEmpty()) {
            c.rectangle(0, 0, c.width(), c.height(), 0xFF100E0D);
            c.text("D O O M", 24, 28, 0xFFEFC479, 3f);
            c.text(message.equals("Starting DOOM...") ? message : "Doom needs attention", 24, 78, 0xFFFFFFFF, 1.2f);
            drawWrapped(c, message.equals("Starting DOOM...") ? "Loading the engine and game data." : message, 24, 110);
            drawWrapped(c, "Open DOOM1.WAD, DOOM.WAD or DOOM2.WAD with this app to play classic Doom. Freedoom is included as the default campaign.", 24, c.height() - 105);
        } else if (!focused) {
            c.rectangle(0, c.height() - 48, c.width(), 48, 0xE6100E0D);
            c.text("Click to play | F12: menu | Esc: release", 12, c.height() - 39, 0xFFEFC479, 1f);
            c.text("WASD / arrows | Ctrl: fire | Space: use", 12, c.height() - 21, 0xFFFFFFFF, 1f);
        }
    }
    private void drawWrapped(Canvas c, String text, int x, int y) {
        int columns = Math.max(20, (c.width() - x * 2) / 7);
        String line = "";
        for (String word : text.replace('\n', ' ').split(" ")) {
            if (!line.isEmpty() && line.length() + word.length() > columns) {
                c.text(line, x, y, 0xFFCDC8BE, 1f); y += 16; line = "";
            }
            line += word + " ";
        }
        c.text(line, x, y, 0xFFCDC8BE, 1f);
    }
    public void onPointerDown(int x, int y, int button) {
        if (!insideFrame(x, y)) return;
        if (!message.isEmpty() && !message.equals("Starting DOOM...")) { restart(); return; }
        if (!focused) { window.requestKeyboard(true); mouseX = x; return; }
        if (button >= 0 && button <= 2) { buttons |= 1 << button; session.input(2, buttons, 0, 0); }
    }
    public void onPointerUp(int x, int y, int button) {
        if (button >= 0 && button <= 2) { buttons &= ~(1 << button); session.input(2, buttons, 0, 0); }
    }
    public void onPointerMove(int x, int y) {
        if (!focused || !insideFrame(x, y)) { mouseX = -1; return; }
        if (mouseX >= 0) {
            double scale = Math.min(window.width() / 320.0, window.height() / 240.0);
            int dx = Math.clamp((int)Math.round((x - mouseX) / scale * 4), -128, 128);
            if (dx != 0) session.input(2, buttons, dx, 0);
        }
        mouseX = x;
    }
    private boolean insideFrame(int x, int y) {
        double scale = Math.min(window.width() / 320.0, window.height() / 240.0);
        double left = (window.width() - 320 * scale) / 2, top = (window.height() - 240 * scale) / 2;
        return x >= left && x < left + 320 * scale && y >= top && y < top + 240 * scale;
    }
    public void onKey(int key, int scanCode, int action, int modifiers) {
        if (!focused || action == 2) return;
        int mapped = KeyMap.scan(key);
        if (mapped == 0) return;
        if (action == 1) {
            if (pressed.containsKey(key)) return;
            boolean alreadyHeld = pressed.containsValue(mapped);
            pressed.put(key, mapped);
            if (!alreadyHeld) session.input(1, mapped, 1, 0);
        } else {
            Integer released = pressed.remove(key);
            if (released != null && !pressed.containsValue(released)) session.input(1, released, 0, 0);
        }
    }
    public void onFocusChanged(boolean focused) {
        this.focused = focused; mouseX = -1;
        if (!focused) { pressed.clear(); buttons = 0; session.input(3, 0, 0, 0); }
    }
}
