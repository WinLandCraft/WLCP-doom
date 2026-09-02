# Third-party code and assets

* **Mocha Doom**, https://github.com/AXDOOMER/mochadoom, revision
  `c0af1322ee5fd168b5cf8aaaf504cab2d1aabe93`. GPL-3.0-or-later; see `LICENSE`
  and individual source headers. Original source is in `engine/mochadoom/src`.
  Our headless `mochadoom.Engine` replaces the desktop entry point; the build
  substitutes `PcmSound` and `PcmMusic` in `DoomMain`, and fixes two impossible
  ByteBuffer/interface casts in WadLoader using its existing DoomBuffer wrapper
  (required by Java 21's sealed ByteBuffer class). The complete-source ZIP
  contains the engine and adapters.
* **Freedoom 0.13.0**, https://github.com/freedoom/freedoom/releases/tag/v0.13.0.
  Freely licensed replacement game data, not id Software's Doom campaign.
  `freedoom1.wad` SHA-256:
  `7323bcc168c5a45ff10749b339960e98314740a734c30d4b9f3337001f9e703d`.
  License and contributor/music credits are in `licenses/` and bundled in the mod.
* **WinLandCraft plugin API 0.1.84-dev**: compile-only, not included in the mod.
  The plugin uses only the CPU API introduced in 0.1.83-dev.
* **Gradle wrapper 8.12**, https://gradle.org, Apache-2.0. The distribution checksum
  is pinned in `gradle/wrapper/gradle-wrapper.properties`.

No proprietary Doom WADs are included. DOOM is a trademark of id Software.
This is an unofficial plugin. When distributing the binary, also provide the
complete corresponding source ZIP (including the engine) under the GPL.
