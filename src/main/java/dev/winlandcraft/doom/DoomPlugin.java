package dev.winlandcraft.doom;

import dev.winlandcraft.api.v2.*;

public final class DoomPlugin implements WinLandCraftPlugin {
    @Override public void register(PluginRegistry registry) {
        registry.register(AppDefinition.builder("wlcp_doom:doom", "DOOM", AppKind.SURFACE,
            DoomApp::new).size(800, 600).extensions("wad").build());
    }
}
