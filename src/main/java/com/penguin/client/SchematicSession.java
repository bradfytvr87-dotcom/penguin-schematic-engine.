package com.penguin.client;

import com.penguin.core.BlockSpec;
import com.penguin.core.BuildProgress;
import com.penguin.core.JsonSchematicLoader;
import com.penguin.core.LitematicReader;
import com.penguin.core.PlacementChecker;
import com.penguin.core.PlacementChecker.Status;
import com.penguin.core.PlacementSafety;
import com.penguin.core.Schematic;
import com.penguin.core.SchematicTransforms;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * Client-side build session: loaded schematic, anchor, progress, and what the hologram should draw.
 * Read-only with respect to the world. It never places or breaks blocks.
 */
final class SchematicSession {
    static final int RADIUS = 24;
    static final int MAX_OUTLINES = 1500;
    private static final int SCAN_PER_PASS = 256;
    private static final int MAX_CANDIDATES = 20_000;
    private static final long MESSAGE_MS = 6000;

    /** One block to draw, in world coordinates. */
    record Outline(int x, int y, int z, Status status, boolean current) {}

    private static final SchematicSession INSTANCE = new SchematicSession();
    static SchematicSession get() { return INSTANCE; }

    private Schematic schematic;
    private BuildProgress progress;
    private BlockPos anchor;
    private int scanPos;
    private int tick;
    private int fileIndex = -1;
    private int turns;          // clockwise quarter turns applied, for the HUD
    private boolean mirroredX, mirroredZ;
    private boolean hudOn = true, hologramOn = true, layerOnly;
    private volatile List<Outline> outlines = List.of();
    private volatile Schematic pending;
    private volatile String pendingMessage;
    private Status currentStatus = Status.UNLOADED;
    private String lastMessage = "";
    private long lastMessageAt;

    // ---- accessors for rendering --------------------------------------------------------------------------------
    Schematic schematic() { return schematic; }
    BuildProgress progress() { return progress; }
    BlockPos anchor() { return anchor; }
    List<Outline> outlines() { return outlines; }
    boolean hudOn() { return hudOn; }
    boolean hologramOn() { return hologramOn; }
    boolean layerOnly() { return layerOnly; }
    Status currentStatus() { return currentStatus; }
    int turns() { return turns; }
    boolean mirroredX() { return mirroredX; }
    boolean mirroredZ() { return mirroredZ; }
    String recentMessage() { return System.currentTimeMillis() - lastMessageAt < MESSAGE_MS ? lastMessage : ""; }

    Path directory() { return FabricLoader.getInstance().getGameDir().resolve("schematics"); }

    // ---- commands ------------------------------------------------------------------------------------------------
    void toggleHud() { hudOn = !hudOn; }
    void toggleHologram(Minecraft mc) { hologramOn = !hologramOn; say(mc, "Hologram " + (hologramOn ? "on" : "off")); }
    void toggleLayer(Minecraft mc) { layerOnly = !layerOnly; say(mc, layerOnly ? "Showing current layer only" : "Showing all layers"); }

    void loadNext(Minecraft mc) {
        try {
            JsonSchematicLoader.ensureExample(directory());
        } catch (IOException ignored) { /* example is optional */ }
        List<Path> files = listFiles();
        if (files.isEmpty()) { say(mc, "No .litematic or .json files in " + directory()); return; }
        fileIndex = (fileIndex + 1) % files.size();
        Path file = files.get(fileIndex);
        say(mc, "Loading " + file.getFileName() + " (" + (fileIndex + 1) + "/" + files.size() + ")...");
        CompletableFuture.runAsync(() -> {
            try {
                String n = file.getFileName().toString().toLowerCase();
                Schematic s = n.endsWith(".litematic") ? LitematicReader.read(file) : JsonSchematicLoader.load(file);
                pendingMessage = "Loaded " + s.name() + ": " + s.size() + " blocks, "
                        + s.sizeX() + "x" + s.sizeY() + "x" + s.sizeZ() + ". Press K to set the anchor.";
                pending = s;
            } catch (IOException | RuntimeException e) {
                pendingMessage = "Load failed: " + e.getMessage();
            }
        });
    }

    private List<Path> listFiles() {
        try (Stream<Path> st = Files.list(directory())) {
            return st.filter(Files::isRegularFile)
                    .filter(p -> {
                        String n = p.getFileName().toString().toLowerCase();
                        return n.endsWith(".litematic") || n.endsWith(".json");
                    })
                    .sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase()))
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    /** Sets the schematic's min corner to the empty space next to the block the player is looking at. */
    void setAnchor(Minecraft mc) {
        if (schematic == null) { say(mc, "Load a schematic first (O)."); return; }
        if (mc.player == null) return;
        if (mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
            anchor = hit.getBlockPos().relative(hit.getDirection());
        } else {
            anchor = mc.player.blockPosition();
        }
        resetProgress();
        say(mc, "Anchor set at " + anchor.getX() + ", " + anchor.getY() + ", " + anchor.getZ());
    }

    void rotate(Minecraft mc) {
        if (schematic == null) return;
        schematic = SchematicTransforms.rotateY(schematic);
        turns = (turns + 1) & 3;
        resetProgress();
        say(mc, "Rotated 90 degrees clockwise");
    }

    void mirrorX(Minecraft mc) {
        if (schematic == null) return;
        schematic = SchematicTransforms.mirrorX(schematic);
        mirroredX = !mirroredX;
        resetProgress();
        say(mc, "Mirrored X");
    }

    void mirrorZ(Minecraft mc) {
        if (schematic == null) return;
        schematic = SchematicTransforms.mirrorZ(schematic);
        mirroredZ = !mirroredZ;
        resetProgress();
        say(mc, "Mirrored Z");
    }

    void skip(Minecraft mc) {
        if (progress == null) return;
        progress.skipCurrent();
        say(mc, "Skipped. It will come back at the end.");
    }

    void verifyAllNow(Minecraft mc) {
        if (schematic == null || anchor == null || mc.level == null) { say(mc, "Load a schematic and set an anchor first."); return; }
        for (int i = 0; i < schematic.size(); i++) verifyOne(mc.level, i);
        rebuildOutlines(mc);
        say(mc, "Verified: " + progress.doneCount() + "/" + schematic.size() + " correct");
    }

    // ---- per-tick work -------------------------------------------------------------------------------------------
    void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null) return;
        Schematic p = pending;
        if (p != null) {
            pending = null;
            schematic = p;
            turns = 0;
            mirroredX = mirroredZ = false;
            resetProgress();
        }
        String m = pendingMessage;
        if (m != null) { pendingMessage = null; say(mc, m); }
        if (schematic == null || anchor == null) return;

        tick++;
        if (tick % 2 == 0) verifyStep(mc.level);
        if (tick % 10 == 0) rebuildOutlines(mc);
    }

    private void resetProgress() {
        progress = schematic == null ? null : new BuildProgress(schematic.size());
        scanPos = 0;
        outlines = List.of();
        currentStatus = Status.UNLOADED;
    }

    private void verifyStep(ClientLevel level) {
        int n = schematic.size();
        int cur = progress.cursor();
        if (cur >= 0) {
            Status s = verifyOne(level, cur);
            if (s != Status.UNLOADED) currentStatus = s;
        }
        for (int k = 0; k < SCAN_PER_PASS && k < n; k++) {
            verifyOne(level, scanPos);
            scanPos = (scanPos + 1) % n;
        }
        int after = progress.cursor();
        if (after != cur && after >= 0) {
            Status s = verifyOne(level, after);
            if (s != Status.UNLOADED) currentStatus = s;
        }
    }

    /** Compares one schematic entry with the world. Done flag follows the result; unloaded chunks change nothing. */
    private Status verifyOne(ClientLevel level, int i) {
        BlockPos pos = worldPos(i);
        if (!level.hasChunkAt(pos)) return Status.UNLOADED;
        BlockState state = level.getBlockState(pos);
        Status s = PlacementChecker.check(schematic.state(i), McBridge.spec(state), state.canBeReplaced());
        progress.setDone(i, s == Status.CORRECT);
        return s;
    }

    BlockPos worldPos(int i) {
        return new BlockPos(anchor.getX() + schematic.x(i), anchor.getY() + schematic.y(i), anchor.getZ() + schematic.z(i));
    }

    /** World position of the next block to place, or null. */
    BlockPos currentPos() {
        if (schematic == null || anchor == null || progress == null) return null;
        int c = progress.cursor();
        return c < 0 ? null : worldPos(c);
    }

    BlockSpec currentSpec() {
        if (schematic == null || progress == null) return null;
        int c = progress.cursor();
        return c < 0 ? null : schematic.state(c);
    }

    /** Distance from the player's eyes to the centre of the next block. */
    double currentDistance(Minecraft mc) {
        BlockPos p = currentPos();
        if (p == null || mc.player == null) return Double.NaN;
        return mc.player.getEyePosition().distanceTo(Vec3.atCenterOf(p));
    }

    boolean currentInReach(Minecraft mc) {
        return PlacementSafety.isWithinReach(currentDistance(mc));
    }

    private void rebuildOutlines(Minecraft mc) {
        if (mc.player == null || mc.level == null) return;
        BlockPos pp = mc.player.blockPosition();
        int cursor = progress.cursor();
        int layerY = cursor >= 0 ? schematic.y(cursor) : -1;

        int n = schematic.size();
        int[] cand = new int[Math.min(n, MAX_CANDIDATES)];
        long[] dist = new long[cand.length];
        int count = 0;
        for (int i = 0; i < n && count < cand.length; i++) {
            if (progress.isDone(i)) continue;
            if (layerOnly && schematic.y(i) != layerY) continue;
            int dx = anchor.getX() + schematic.x(i) - pp.getX();
            int dy = anchor.getY() + schematic.y(i) - pp.getY();
            int dz = anchor.getZ() + schematic.z(i) - pp.getZ();
            if (Math.abs(dx) > RADIUS || Math.abs(dy) > RADIUS || Math.abs(dz) > RADIUS) continue;
            cand[count] = i;
            dist[count] = (long) dx * dx + (long) dy * dy + (long) dz * dz;
            count++;
        }
        Integer[] order = new Integer[count];
        for (int k = 0; k < count; k++) order[k] = k;
        java.util.Arrays.sort(order, Comparator.comparingLong(k -> dist[k]));

        List<Outline> out = new ArrayList<>(Math.min(count, MAX_OUTLINES));
        for (int k = 0; k < count && out.size() < MAX_OUTLINES; k++) {
            int i = cand[order[k]];
            BlockPos pos = worldPos(i);
            Status s = Status.UNLOADED;
            if (mc.level.hasChunkAt(pos)) {
                BlockState st = mc.level.getBlockState(pos);
                s = PlacementChecker.check(schematic.state(i), McBridge.spec(st), st.canBeReplaced());
            }
            if (s == Status.CORRECT || s == Status.UNLOADED) continue;
            out.add(new Outline(pos.getX(), pos.getY(), pos.getZ(), s, i == cursor));
        }
        // the current target is always drawn, even if it is outside the radius
        if (cursor >= 0 && out.stream().noneMatch(Outline::current)) {
            BlockPos pos = worldPos(cursor);
            out.add(new Outline(pos.getX(), pos.getY(), pos.getZ(), currentStatus, true));
        }
        outlines = out;
    }

    private void say(Minecraft mc, String text) {
        lastMessage = text;
        lastMessageAt = System.currentTimeMillis();
        McBridge.message(mc, text);
    }
}
