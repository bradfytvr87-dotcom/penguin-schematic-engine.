package com.penguin.client;

import com.penguin.core.BuildProgress;
import com.penguin.core.PlacementChecker;
import com.penguin.core.Schematic;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;

/** On-screen guide: what to place next, where, and how far along the build is. */
final class HudPanel {
    private static final int PANEL = 0xB9101C28;
    private static final int TITLE = 0xFF8DDCFF;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int DIM = 0xFFB9D9EA;
    private static final int RED = 0xFFFF6B6B;
    private static final int BAR_BG = 0xFF24384C;
    private static final int BAR_FG = 0xFF8DDCFF;
    private static final int WIDTH = 262;

    private HudPanel() {}

    static void render(GuiGraphicsExtractor g) {
        SchematicSession s = SchematicSession.get();
        Minecraft mc = Minecraft.getInstance();
        if (!s.hudOn() || mc.player == null || mc.options.hideGui) return;

        Font font = mc.font;
        int x = 8, y = 8, line = 12;
        Schematic schem = s.schematic();
        boolean haveBlock = schem != null && s.anchor() != null;
        int rows = schem == null ? 3 : (haveBlock ? 8 : 5);
        int height = rows * line + 10;
        g.fill(x - 3, y - 3, x + WIDTH, y - 3 + height, PANEL);

        int ty = y + 2;
        text(g, font, "PENGUIN SCHEMATIC ENGINE", x + 4, ty, TITLE);
        ty += line;

        if (schem == null) {
            text(g, font, "No schematic loaded. Press O.", x + 4, ty, TEXT);
            ty += line;
            text(g, font, "Put .litematic or .json files in /schematics", x + 4, ty, DIM);
            return;
        }

        String shape = schem.sizeX() + "x" + schem.sizeY() + "x" + schem.sizeZ();
        String orient = "rot " + (s.turns() * 90) + (s.mirroredX() ? " mirX" : "") + (s.mirroredZ() ? " mirZ" : "");
        text(g, font, schem.name() + "  (" + shape + ", " + orient + ")", x + 4, ty, TEXT);
        ty += line;

        if (!haveBlock) {
            text(g, font, "Look at a block and press K to set the anchor.", x + 4, ty, RED);
            ty += line;
            text(g, font, "O next file | R rotate | M/B mirror | H hide HUD", x + 4, ty, DIM);
            return;
        }

        BuildProgress p = s.progress();
        int done = p.doneCount(), total = p.size();
        int pct = total == 0 ? 0 : (int) (100L * done / total);
        text(g, font, "Progress: " + done + "/" + total + " (" + pct + "%)", x + 4, ty, TITLE);
        ty += line;
        int barW = WIDTH - 14;
        g.fill(x + 4, ty, x + 4 + barW, ty + 4, BAR_BG);
        g.fill(x + 4, ty, x + 4 + (int) ((long) barW * done / Math.max(1, total)), ty + 4, BAR_FG);
        ty += 8;

        if (p.complete()) {
            text(g, font, "Build complete.", x + 4, ty, TITLE);
            return;
        }

        BlockPos cur = s.currentPos();
        text(g, font, "Next: " + s.currentSpec(), x + 4, ty, TEXT);
        ty += line;
        double dist = s.currentDistance(mc);
        String where = cur.getX() + ", " + cur.getY() + ", " + cur.getZ()
                + (Double.isFinite(dist) ? "  (" + Math.round(dist) + " m)" : "");
        text(g, font, "At " + where, x + 4, ty, TEXT);
        ty += line;
        String status = PlacementChecker.describe(s.currentStatus());
        boolean reach = s.currentInReach(mc);
        text(g, font, "Status: " + status + (reach ? "" : " - move closer"), x + 4, ty, reach ? DIM : RED);
        ty += line;
        String msg = s.recentMessage();
        text(g, font, msg.isEmpty() ? "O file | K anchor | R/M/B turn | N skip | L layer | V holo" : msg, x + 4, ty, DIM);
    }

    private static void text(GuiGraphicsExtractor g, Font font, String s, int x, int y, int color) {
        g.text(font, s, x, y, color);
    }
}
