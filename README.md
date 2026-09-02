# DOOM for WinLandCraft

Classic Doom in a Minecraft panel. A Fabric client plugin using **WinLandCraft API v2**,
with the Mocha Doom engine, 320×200 software rendering corrected to a 4:3 picture,
stereo sound effects, software MIDI music, keyboard/mouse input and persistent saves.

## Install and play

1. Install Minecraft **1.21.4**, Fabric Loader **0.16.9+**, Java **21**, and
   WinLandCraft **0.1.83-dev+** with its normal dependencies.
2. Put `build/libs/wlcp-doom-0.1.0.jar` in your Minecraft instance's `mods` directory.
   Do not install the sources JAR, the engine JAR or the API JAR as mods.
3. Open **DOOM** from WinLandCraft's Apps launcher. The included **Freedoom: Phase 1**
   campaign starts offline. Click the panel, then Enter to open the game menu.
4. For the actual Doom campaign, open your **DOOM1.WAD** (shareware), **DOOM.WAD**
   (registered/Ultimate) or **DOOM2.WAD** from WinLandCraft File Manager using DOOM,
   or drop the WAD on the panel. TNT and Plutonia IWADs are also recognized.
   The launcher remembers your last selected WAD. WADs remain on your computer.

The bundled Freedoom campaign uses the same engine with different freely licensed
levels, graphics and music. Original Doom game data is not included.

## Controls

| Key | Action |
| --- | --- |
| W / S | Forward / backward |
| A / D | Strafe left / right |
| Left / Right arrows | Turn continuously |
| Mouse movement | Turn within panel bounds |
| Ctrl / left mouse | Fire |
| Space | Use doors / switches |
| Shift | Run |
| 1–7 | Weapon |
| Tab | Automap |
| Enter / arrows | Menu selection |
| F12 or backtick | Doom menu / back |
| F2 / F3 | Save / load |
| F6 / F9 | Quicksave / quickload |
| Pause | Pause game |
| Escape | Release input to Minecraft |

The host owns focus and the cursor. Mouse turning stops at the panel edge; use
arrow keys for unlimited turns. The first click focuses the panel without firing.
Losing focus releases all held controls. Doom keeps running when unfocused;
pause before leaving the panel. Resizing preserves the 4:3 game image.

## Saves and diagnostics

Data lives under the Minecraft instance directory:

`config/winlandcraft/plugins/wlcp_doom/doom/`

* `runtime/`: extracted engine and fallback IWAD, keyed by SHA-256.
* `saves/<wad-content-hash>/player-1/`: WAD copy, Doom save slots, configuration
  and `engine.log`. Concurrent windows use player-2, player-3, etc. Reopening
  takes the first unused player slot. Each WAD has a separate save profile.
* `last-wad.txt`: last explicitly selected IWAD. Remove this file to return to
  the bundled Freedoom campaign.

Save from Doom's F2/F6 menus before closing; closing a panel does not autosave a level.
Startup/process errors appear in the panel. Click to retry. Detailed engine
diagnostics are written to the current save folder's `engine.log`.

## Build

The API JAR and pinned engine sources are included for a standalone build.
Java 21 is the only runtime requirement; no native binaries, browser or external
Doom installation is needed. Gradle downloads itself on the first build.

```powershell
# Only needed if assets/freedoom1.wad is missing:
./scripts/fetch-freedoom.ps1
./gradlew.bat -g .gradle-user build
```

On Linux/macOS, use `sh gradlew -g .gradle-user build`; download the pinned
Freedoom release and put `freedoom1.wad` in `assets/` if absent.

`build` compiles against the real API, builds the isolated engine, runs the
headless integration checks and produces the mod plus a complete-source ZIP.
The checks exercise real engine startup, frames, sound/music, gameplay input,
WAD validation, independent save slots, shutdown and packaged API exclusion.
Screenshots are written under `build/verification/`.

## Architecture and limits

Each panel owns a headless Java subprocess launched with the same Java runtime
as Minecraft, with a 256 MB heap limit. The engine runs the original Doom game
logic and software renderer. Bounded stdin input and framed stdout RGBA/PCM carry
input and media; stderr goes to a log file. No network service is opened.
Video uses `FrameSurface.submit`; 10 ms, 48 kHz stereo blocks use `AudioOutput`.
The host handles panel rendering and streaming. A stalled/closed window drops
audio; it never retries old audio. Closing terminates the process and releases
the save-folder lock after it exits. Engine `System.exit` cannot exit Minecraft.

MIDI synthesis uses OpenJDK's `AudioSynthesizer` in the child JVM, with an export
flag confined to that process. If the Java distribution lacks this synthesizer,
music falls back to silence while sound effects continue. It is General MIDI,
not OPL emulation. No desktop audio device is opened by the engine.

Supported inputs are base-game IWADs up to 128 MB. PWAD add-ons, DeHackEd mods,
Boom/GZDoom-specific content, multiplayer and gamepad input are not supported.
The engine's inherited game compatibility limits also apply.

The automated checks do not run Minecraft. In-game release checks still need
Apps/File Manager/drop launch, focus/Escape, resize/Ctrl-scale, close/reopen,
groups/curves, local audio and streamed audio/video on each target OS.

GPL-3.0-or-later. See [THIRD-PARTY.md](THIRD-PARTY.md) for engine provenance,
asset licensing and source distribution details.
