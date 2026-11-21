# Penguin Schematic Engine 1.1

A Fabric client mod by PenguinVexx for Minecraft 26.2. It loads schematics, draws a hologram of what is left to build,
and tells you which block to place next. **You place every block yourself.** The mod never sends placement packets,
automates clicks, or moves you, and it does not try to get around any server's anti-cheat. Check Donut SMP's current
rules on client mods before you use it there.

## Status: read this first

| Part | Status |
| --- | --- |
| Core library (`com.penguin.core`): litematic reader, rotate/mirror, placement checks, progress | Compiled and covered by 39 unit tests |
| Minecraft layer (`com.penguin.client`): keys, HUD, hologram, world reads | Written against the 26.2 docs, **not yet compiled against real Minecraft 26.2** |
| `.jar` | **Not built.** Build it on your machine with the steps below |

The core was built and tested without Minecraft. The Minecraft-facing code uses names from the 26.x unobfuscated
(Mojang-named) API and Fabric API 0.156.0+26.2, but the machine that wrote it could not download Minecraft or Fabric.
Expect to fix a few names on the first build (see "If the build fails").

## Build

1. Install a 64-bit JDK 25 (Minecraft 26.x needs Java 25).
2. In this folder run `./gradlew build` (Windows: `gradlew.bat build`). The first run downloads Gradle 9.5.1, Loom,
   Minecraft 26.2 and Fabric API.
3. The mod is `build/libs/penguin-schematic-engine-1.1.0.jar` (not the `-sources` one).
4. Put it in `.minecraft/mods` with Fabric Loader 0.19.3+ and Fabric API for 26.2.

`./gradlew test` runs the unit tests. `./gradlew runClient` starts a dev client.

## Using it

Put `.litematic` or `.json` files in `.minecraft/schematics/`. An `example.json` is created on first use.

| Key | Action |
| --- | --- |
| O | Load the next file in the folder (press again to cycle) |
| K | Set the anchor: the schematic's lowest corner goes in the empty space next to the block you are looking at |
| R | Rotate 90 degrees clockwise |
| M / B | Mirror along X / along Z |
| N | Skip the current block (it comes back at the end) |
| P | Re-check every block now |
| V | Toggle the hologram |
| L | Show only the layer you are working on |
| H | Toggle the HUD |

All keys can be changed in Options > Controls.

**Hologram.** Wireframe boxes in the world: ice blue = still to place, white = next block, amber = right block but wrong
rotation or state, red = a different solid block is in the way. A blue box marks the whole schematic. Only the nearest
1,500 unfinished blocks within 24 blocks are drawn. It is outlines only, not textured ghost blocks.

**Placement checks.** The mod compares the world with the schematic on its own, so there is no confirm key. It checks block
type and every property the schematic names (stair facing, half, axis, and so on), notices blocks you break, skips
unloaded chunks, and warns when the next block is out of reach.

**Rotate and mirror** also fix block states: stairs, logs, fences, walls, panes, doors, rails and signs turn with the build.
Not handled: beds, wall banners, jigsaw and crafter orientation, and modded properties.

**Litematic notes.** Air is skipped. The schematic is shifted so its lowest block sits at the anchor. Multiple regions,
negative region sizes and block-state properties are supported. Block entities (chest contents, sign text) and entities
are ignored. Limits: 1,000,000 blocks, 4,096 blocks per axis, 64 MB file.

## If the build fails

All Minecraft calls are in five small files, so a rename is a one-line fix:

- `client/McBridge.java`: `BlockState.getProperties()`, `getValue`, `Property.getName(value)`, `Player.displayClientMessage`
- `client/SchematicSession.java`: `Minecraft.hitResult`, `BlockHitResult.getDirection()`, `ClientLevel.hasChunkAt`, `BlockState.canBeReplaced()`, `Player.getEyePosition()`
- `client/Hologram.java`: `net.minecraft.gizmos.Gizmos` / `GizmoStyle` (package names), `Gizmos.cuboid(...).setAlwaysOnTop()`
- `client/HudPanel.java`: `GuiGraphicsExtractor.text(...)` and `fill(...)`
- `client/PenguinClient.java`: `LevelRenderEvents.BEFORE_GIZMOS`, `HudElementRegistry.addLast`, `KeyMappingHelper`, `KeyMapping.Category.register`

If `fabric_api_version` is not found, pick a current `26.2` build from https://fabricmc.net/develop and edit `gradle.properties`.

## Known limits

- Not tested against a real Litematica file yet. The reader follows the published format and passes hand-computed fixtures,
  so try a file from your own Litematica and report anything odd.
- No material list or inventory scan yet.
- Checks are client-side only. A server can still reject a placement.
