// WinLandCraft headless platform replacement for Mocha Doom. GPL-3.0-or-later.
package mochadoom;

import doom.*;
import java.util.*;
import m.Settings;
import wlcp.bridge.Bridge;

public final class Engine {
    private static Engine instance;
    public final CVarManager cvm;
    public final ConfigManager cm;
    private DoomMain<?, ?> game;

    private Engine(String[] args) throws Exception {
        instance = this;
        cvm = new CVarManager(Arrays.asList(args));
        cm = new ConfigManager();
        cm.update(Settings.screenblocks, 10);
        game = new DoomMain<>();
    }
    public static void main(String[] args) throws Exception {
        // Engine diagnostics must never corrupt the framed stdout protocol.
        System.setOut(System.err);
        Bridge.start();
        Engine engine = new Engine(args);
        engine.game.setupLoop();
    }
    public static void updateFrame() {
        Bridge.input(instance.game);
        Bridge.frame(instance.game.graphicSystem.getScreenImage());
    }
    public String getWindowTitle(double fps) { return "DOOM"; }
    public static Engine getEngine() { return instance; }
    public static CVarManager getCVM() { return instance.cvm; }
    public static ConfigManager getConfig() { return instance.cm; }
}
