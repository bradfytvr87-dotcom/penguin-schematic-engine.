package com.penguin.client;

import com.penguin.core.PlacementChecker.Status;
import com.penguin.core.Schematic;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.phys.AABB;

/**
 * Draws the schematic as a wireframe hologram in the world using vanilla gizmos.
 * Colours: ice blue = still to place, white = next block, amber = wrong state, red = wrong block in the way.
 */
final class Hologram {
    private static final int ICE = 0xB08DDCFF;
    private static final int NEXT = 0xFFFFFFFF;
    private static final int WRONG_STATE = 0xFFFFC107;
    private static final int WRONG_BLOCK = 0xFFFF3B3B;
    private static final int BOUNDS = 0x806CB8F0;
    private static final double INSET = 0.03;

    private Hologram() {}

    /** Called from LevelRenderEvents.BEFORE_GIZMOS each frame. Reads cached data only; no world queries here. */
    static void draw() {
        SchematicSession s = SchematicSession.get();
        Schematic schem = s.schematic();
        BlockPos anchor = s.anchor();
        if (!s.hologramOn() || schem == null || anchor == null) return;

        AABB bounds = new AABB(anchor.getX(), anchor.getY(), anchor.getZ(),
                anchor.getX() + schem.sizeX(), anchor.getY() + schem.sizeY(), anchor.getZ() + schem.sizeZ());
        Gizmos.cuboid(bounds, GizmoStyle.stroke(BOUNDS));

        for (SchematicSession.Outline o : s.outlines()) {
            AABB box = new AABB(o.x(), o.y(), o.z(), o.x() + 1.0, o.y() + 1.0, o.z() + 1.0).deflate(INSET);
            if (o.current()) {
                Gizmos.cuboid(box, GizmoStyle.stroke(NEXT)).setAlwaysOnTop();
                Gizmos.cuboid(box.inflate(0.05), GizmoStyle.stroke(NEXT)).setAlwaysOnTop();
            } else if (o.status() == Status.WRONG_BLOCK) {
                Gizmos.cuboid(box, GizmoStyle.stroke(WRONG_BLOCK)).setAlwaysOnTop();
            } else if (o.status() == Status.WRONG_STATE) {
                Gizmos.cuboid(box, GizmoStyle.stroke(WRONG_STATE)).setAlwaysOnTop();
            } else {
                Gizmos.cuboid(box, GizmoStyle.stroke(ICE));
            }
        }
    }
}
