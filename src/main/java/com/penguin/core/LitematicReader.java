package com.penguin.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Reads Litematica {@code .litematic} files (gzip NBT).
 *
 * <p>Layout: root.Regions is a compound of regions. Each region has Position and Size (Size may be negative),
 * BlockStatePalette (list of {Name, Properties}) and BlockStates (packed longs). Indices are packed LSB-first at
 * max(2, ceil(log2(paletteSize))) bits each, an entry may straddle two longs, and the block order is
 * x fastest, then z, then y. Air is skipped and the result is shifted so its min corner is 0,0,0.
 */
public final class LitematicReader {
    private LitematicReader() {}

    public static Schematic read(Path path) throws IOException {
        String file = path.getFileName().toString();
        String fallback = file.toLowerCase().endsWith(".litematic") ? file.substring(0, file.length() - 10) : file;
        try (InputStream in = Files.newInputStream(path)) {
            if (Files.size(path) > Nbt.MAX_FILE_BYTES) throw new IOException("File exceeds " + (Nbt.MAX_FILE_BYTES >> 20) + " MB limit");
            return read(in, fallback);
        }
    }

    public static Schematic read(InputStream in, String fallbackName) throws IOException {
        Map<String, Object> root = Nbt.readGzip(in);
        Map<String, Object> regions = Nbt.compound(root, "Regions");
        if (regions.isEmpty()) throw new IOException("Litematic has no regions");

        String name = fallbackName;
        if (root.get("Metadata") instanceof Map<?, ?> meta && meta.get("Name") instanceof String s && !s.isBlank()) name = s;

        Schematic.Builder builder = Schematic.builder();
        for (Map.Entry<String, Object> e : regions.entrySet()) {
            if (!(e.getValue() instanceof Map<?, ?>)) throw new IOException("Region '" + e.getKey() + "' is not a compound");
            @SuppressWarnings("unchecked") Map<String, Object> region = (Map<String, Object>) e.getValue();
            try {
                readRegion(region, builder);
            } catch (IllegalArgumentException ex) {
                throw new IOException("Region '" + e.getKey() + "': " + ex.getMessage(), ex);
            }
        }
        try {
            return builder.build(name);
        } catch (IllegalArgumentException ex) {
            throw new IOException(ex.getMessage(), ex);
        }
    }

    private static void readRegion(Map<String, Object> region, Schematic.Builder out) throws IOException {
        Map<String, Object> pos = Nbt.compound(region, "Position");
        Map<String, Object> size = Nbt.compound(region, "Size");
        int px = Nbt.intValue(pos, "x"), py = Nbt.intValue(pos, "y"), pz = Nbt.intValue(pos, "z");
        int sx = Nbt.intValue(size, "x"), sy = Nbt.intValue(size, "y"), sz = Nbt.intValue(size, "z");
        if (sx == 0 || sy == 0 || sz == 0) return;
        int ax = Math.abs(sx), ay = Math.abs(sy), az = Math.abs(sz);
        if (ax > Schematic.MAX_EXTENT || ay > Schematic.MAX_EXTENT || az > Schematic.MAX_EXTENT)
            throw new IOException("Region larger than " + Schematic.MAX_EXTENT + " blocks on one axis");
        long total = (long) ax * ay * az;

        // A negative size means the region extends backwards from Position; local index 0 is the min corner.
        int minX = px + (sx < 0 ? sx + 1 : 0);
        int minY = py + (sy < 0 ? sy + 1 : 0);
        int minZ = pz + (sz < 0 ? sz + 1 : 0);

        List<Object> rawPalette = Nbt.list(region, "BlockStatePalette");
        if (rawPalette.isEmpty()) throw new IOException("Empty block palette");
        List<BlockSpec> palette = new ArrayList<>(rawPalette.size());
        for (Object o : rawPalette) {
            if (!(o instanceof Map<?, ?> m)) throw new IOException("Palette entry is not a compound");
            @SuppressWarnings("unchecked") Map<String, Object> entry = (Map<String, Object>) m;
            java.util.TreeMap<String, String> props = new java.util.TreeMap<>();
            if (entry.get("Properties") instanceof Map<?, ?> pm) {
                for (Map.Entry<?, ?> pe : pm.entrySet()) props.put(String.valueOf(pe.getKey()), String.valueOf(pe.getValue()));
            }
            palette.add(new BlockSpec(Nbt.string(entry, "Name"), props));
        }

        long[] packed = Nbt.longArray(region, "BlockStates");
        int bits = Math.max(2, 32 - Integer.numberOfLeadingZeros(palette.size() - 1));
        long needed = (total * bits + 63) / 64;
        if (packed.length < needed)
            throw new IOException("BlockStates too short: need " + needed + " longs, found " + packed.length);
        long mask = (1L << bits) - 1;

        boolean[] airCache = new boolean[palette.size()];
        for (int i = 0; i < airCache.length; i++) airCache[i] = palette.get(i).isAir();

        for (int i = 0; i < total; i++) {
            long bit = (long) i * bits;
            int word = (int) (bit >>> 6);
            int off = (int) (bit & 63);
            long v = packed[word] >>> off;
            if (off + bits > 64) v |= packed[word + 1] << (64 - off);
            int idx = (int) (v & mask);
            if (idx >= palette.size()) throw new IOException("Palette index " + idx + " out of range at block " + i);
            if (airCache[idx]) continue;
            int x = i % ax;
            int z = (i / ax) % az;
            int y = i / (ax * az);
            out.add(minX + x, minY + y, minZ + z, palette.get(idx));
        }
    }
}
