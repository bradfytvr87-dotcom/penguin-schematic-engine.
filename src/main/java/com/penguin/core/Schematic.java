package com.penguin.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable block list in build order (bottom layer first, then z, then x).
 * Coordinates are relative to the schematic's minimum corner, so the anchor is always that corner.
 */
public final class Schematic {
    public static final int MAX_BLOCKS = 1_000_000;
    public static final int MAX_EXTENT = 4096;

    private final String name;
    private final List<BlockSpec> palette;
    private final int[] xs, ys, zs, states;
    private final int sizeX, sizeY, sizeZ;

    private Schematic(String name, List<BlockSpec> palette, int[] xs, int[] ys, int[] zs, int[] states,
                      int sizeX, int sizeY, int sizeZ) {
        this.name = name;
        this.palette = palette;
        this.xs = xs;
        this.ys = ys;
        this.zs = zs;
        this.states = states;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
    }

    public String name() { return name; }
    public int size() { return xs.length; }
    public int x(int i) { return xs[i]; }
    public int y(int i) { return ys[i]; }
    public int z(int i) { return zs[i]; }
    public BlockSpec state(int i) { return palette.get(states[i]); }
    public int sizeX() { return sizeX; }
    public int sizeY() { return sizeY; }
    public int sizeZ() { return sizeZ; }
    public List<BlockSpec> palette() { return palette; }

    public Schematic withName(String newName) {
        return new Schematic(newName, palette, xs, ys, zs, states, sizeX, sizeY, sizeZ);
    }

    public static Builder builder() { return new Builder(); }

    /** Collects blocks, then normalizes (min corner to 0,0,0), de-duplicates positions and sorts into build order. */
    public static final class Builder {
        private int[] bx = new int[256], by = new int[256], bz = new int[256], bs = new int[256];
        private int count;
        private final Map<BlockSpec, Integer> index = new HashMap<>();
        private final List<BlockSpec> palette = new ArrayList<>();

        public Builder add(int x, int y, int z, BlockSpec spec) {
            if (spec == null) throw new IllegalArgumentException("Block is null");
            if (spec.isAir()) return this;
            if (count >= MAX_BLOCKS) throw new IllegalArgumentException("Too many blocks (max " + MAX_BLOCKS + ")");
            if (count == bx.length) {
                int n = count * 2;
                bx = Arrays.copyOf(bx, n);
                by = Arrays.copyOf(by, n);
                bz = Arrays.copyOf(bz, n);
                bs = Arrays.copyOf(bs, n);
            }
            Integer id = index.get(spec);
            if (id == null) {
                id = palette.size();
                palette.add(spec);
                index.put(spec, id);
            }
            bx[count] = x;
            by[count] = y;
            bz[count] = z;
            bs[count] = id;
            count++;
            return this;
        }

        public Schematic build(String name) {
            if (name == null || name.isBlank()) throw new IllegalArgumentException("Schematic name is required");
            if (count == 0) throw new IllegalArgumentException("Schematic must contain at least one non-air block");
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
            for (int i = 0; i < count; i++) {
                minX = Math.min(minX, bx[i]); maxX = Math.max(maxX, bx[i]);
                minY = Math.min(minY, by[i]); maxY = Math.max(maxY, by[i]);
                minZ = Math.min(minZ, bz[i]); maxZ = Math.max(maxZ, bz[i]);
            }
            long ex = (long) maxX - minX + 1, ey = (long) maxY - minY + 1, ez = (long) maxZ - minZ + 1;
            if (ex > MAX_EXTENT || ey > MAX_EXTENT || ez > MAX_EXTENT)
                throw new IllegalArgumentException("Schematic is larger than " + MAX_EXTENT + " blocks on one axis");

            // key = position in build order, then insertion index (so the last duplicate wins)
            long[] keys = new long[count];
            for (int i = 0; i < count; i++) {
                long pos = (((long) (by[i] - minY)) * MAX_EXTENT + (bz[i] - minZ)) * MAX_EXTENT + (bx[i] - minX);
                keys[i] = (pos << 20) | i;
            }
            Arrays.sort(keys);

            int[] ox = new int[count], oy = new int[count], oz = new int[count], os = new int[count];
            int n = 0;
            for (int k = 0; k < count; k++) {
                int i = (int) (keys[k] & 0xFFFFF);
                long pos = keys[k] >>> 20;
                if (k + 1 < count && (keys[k + 1] >>> 20) == pos) continue; // a later block overwrites this one
                ox[n] = bx[i] - minX;
                oy[n] = by[i] - minY;
                oz[n] = bz[i] - minZ;
                os[n] = bs[i];
                n++;
            }
            return new Schematic(name, List.copyOf(palette), Arrays.copyOf(ox, n), Arrays.copyOf(oy, n),
                    Arrays.copyOf(oz, n), Arrays.copyOf(os, n), (int) ex, (int) ey, (int) ez);
        }
    }
}
