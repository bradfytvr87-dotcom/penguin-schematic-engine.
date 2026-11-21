package com.penguin.core;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LitematicReaderTest {
    private static Map<String, Object> vec(int x, int y, int z) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("x", x);
        m.put("y", y);
        m.put("z", z);
        return m;
    }

    private static Map<String, Object> entry(String name, String... props) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("Name", name);
        if (props.length > 0) {
            Map<String, Object> p = new LinkedHashMap<>();
            for (int i = 0; i < props.length; i += 2) p.put(props[i], props[i + 1]);
            m.put("Properties", p);
        }
        return m;
    }

    private static Map<String, Object> region(int[] pos, int[] size, List<Object> palette, long[] states) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("Position", vec(pos[0], pos[1], pos[2]));
        r.put("Size", vec(size[0], size[1], size[2]));
        r.put("BlockStatePalette", palette);
        r.put("BlockStates", states);
        return r;
    }

    private static Schematic read(Map<String, Object> regions, String metaName) throws IOException {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("Version", 6);
        if (metaName != null) {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("Name", metaName);
            root.put("Metadata", meta);
        }
        root.put("Regions", regions);
        return LitematicReader.read(new ByteArrayInputStream(NbtTestWriter.gzip(root)), "fallback");
    }

    /** Independent packer: builds the bit string with BigInteger instead of the reader's shift logic. */
    private static long[] pack(int[] indices, int bits) {
        BigInteger all = BigInteger.ZERO;
        for (int i = 0; i < indices.length; i++) all = all.or(BigInteger.valueOf(indices[i]).shiftLeft(i * bits));
        int longs = (indices.length * bits + 63) / 64;
        long[] out = new long[longs];
        BigInteger mask64 = BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE);
        for (int k = 0; k < longs; k++) out[k] = all.shiftRight(64 * k).and(mask64).longValue();
        return out;
    }

    @Test void readsHandComputedTwoBitRegion() throws Exception {
        // palette: air, stone, dirt -> 2 bits. size 2x1x2, order x fastest then z.
        // (0,0,0)=stone(1) (1,0,0)=air(0) (0,0,1)=dirt(2) (1,0,1)=stone(1)
        // packed: 1 | 0<<2 | 2<<4 | 1<<6 = 1 + 32 + 64 = 97
        Map<String, Object> regions = new LinkedHashMap<>();
        regions.put("main", region(new int[]{10, 64, 10}, new int[]{2, 1, 2},
                new ArrayList<>(List.of(entry("minecraft:air"), entry("minecraft:stone"), entry("minecraft:dirt"))),
                new long[]{97L}));
        Schematic s = read(regions, "Hand made");
        assertEquals("Hand made", s.name());
        assertEquals(3, s.size());
    }

    @Test void blocksAreOrderedByLayerThenZThenX() throws Exception {
        Map<String, Object> regions = new LinkedHashMap<>();
        regions.put("main", region(new int[]{0, 0, 0}, new int[]{2, 1, 2},
                new ArrayList<>(List.of(entry("minecraft:air"), entry("minecraft:stone"), entry("minecraft:dirt"))),
                new long[]{97L}));
        Schematic s = read(regions, null);
        assertEquals("fallback", s.name());
        // stone(0,0,0), dirt(0,0,1), stone(1,0,1)
        assertEquals(0, s.x(0)); assertEquals(0, s.z(0)); assertEquals("minecraft:stone", s.state(0).id());
        assertEquals(0, s.x(1)); assertEquals(1, s.z(1)); assertEquals("minecraft:dirt", s.state(1).id());
        assertEquals(1, s.x(2)); assertEquals(1, s.z(2)); assertEquals("minecraft:stone", s.state(2).id());
    }

    @Test void readsEntriesThatStraddleTwoLongs() throws Exception {
        // 5-entry palette -> 3 bits per block; 64 blocks = 192 bits. Entry 21 starts at bit 63 and entry 42 at bit 62,
        // so both straddle a long boundary (one with 1 bit spare, one exactly filling 65 bits).
        List<Object> palette = new ArrayList<>(List.of(entry("minecraft:air"), entry("minecraft:stone"),
                entry("minecraft:dirt"), entry("minecraft:glass"), entry("minecraft:sand")));
        int[] idx = new int[64];
        for (int i = 0; i < idx.length; i++) idx[i] = i % 5;
        idx[21] = 4;   // high bit lands in the next long
        idx[42] = 4;   // starts at bit 62, third bit lands in the next long
        Map<String, Object> regions = new LinkedHashMap<>();
        regions.put("main", region(new int[]{0, 0, 0}, new int[]{64, 1, 1}, palette, pack(idx, 3)));
        Schematic s = read(regions, "wide");
        String[] names = {"air", "stone", "dirt", "glass", "sand"};
        int found = 0;
        for (int i = 0; i < 64; i++) {
            if (idx[i] == 0) continue;
            // blocks come back in x order for a single row
            assertEquals("minecraft:" + names[idx[i]], s.state(found).id(), "block at x=" + i);
            found++;
        }
        assertEquals(found, s.size());
    }

    @Test void uses_max_of_two_bits_and_ceil_log2() throws Exception {
        // 17 palette entries -> 5 bits (not 4)
        List<Object> palette = new ArrayList<>();
        palette.add(entry("minecraft:air"));
        for (int i = 1; i < 17; i++) palette.add(entry("minecraft:block_" + i));
        int[] idx = {16, 1, 0, 9, 16, 2};
        Map<String, Object> regions = new LinkedHashMap<>();
        regions.put("main", region(new int[]{0, 0, 0}, new int[]{6, 1, 1}, palette, pack(idx, 5)));
        Schematic s = read(regions, "five-bit");
        assertEquals(5, s.size());
        assertEquals("minecraft:block_16", s.state(0).id());
        assertEquals("minecraft:block_1", s.state(1).id());
        assertEquals("minecraft:block_9", s.state(2).id());
        assertEquals("minecraft:block_16", s.state(3).id());
        assertEquals("minecraft:block_2", s.state(4).id());
    }

    @Test void twoEntryPaletteStillUsesTwoBits() throws Exception {
        // Litematica never packs below 2 bits. indices 1,0,1,1 at 2 bits = 1 | 1<<4 | 1<<6 = 81 (1-bit packing would give 13)
        Map<String, Object> regions = new LinkedHashMap<>();
        regions.put("main", region(new int[]{0, 0, 0}, new int[]{4, 1, 1},
                new ArrayList<>(List.of(entry("minecraft:air"), entry("minecraft:stone"))), new long[]{81L}));
        Schematic s = read(regions, "two-entry");
        assertEquals(3, s.size());
        assertEquals(0, s.x(0)); assertEquals(2, s.x(1)); assertEquals(3, s.x(2));
    }

    @Test void usesXFastestThenZThenYOnNonSquareRegions() throws Exception {
        // 3x2x2 region: index = x + 3*(z + 2*y). Index 0 = (0,0,0), 4 = (1,0,1), 11 = (2,1,1).
        List<Object> pal = new ArrayList<>(List.of(entry("minecraft:air"), entry("minecraft:stone"),
                entry("minecraft:dirt"), entry("minecraft:glass")));
        int[] idx = new int[12];
        idx[0] = 1;
        idx[4] = 2;
        idx[6] = 2;    // (0,1,0): first block of the second layer
        idx[11] = 3;
        Map<String, Object> regions = new LinkedHashMap<>();
        regions.put("main", region(new int[]{0, 0, 0}, new int[]{3, 2, 2}, pal, pack(idx, 2)));
        Schematic s = read(regions, "axes");
        assertEquals(4, s.size());
        // build order is y, then z, then x
        assertEquals("minecraft:stone", s.state(0).id());
        assertEquals(0, s.x(0)); assertEquals(0, s.y(0)); assertEquals(0, s.z(0));
        assertEquals("minecraft:dirt", s.state(1).id());
        assertEquals(1, s.x(1)); assertEquals(0, s.y(1)); assertEquals(1, s.z(1));
        assertEquals("minecraft:dirt", s.state(2).id());
        assertEquals(0, s.x(2)); assertEquals(1, s.y(2)); assertEquals(0, s.z(2));
        assertEquals("minecraft:glass", s.state(3).id());
        assertEquals(2, s.x(3)); assertEquals(1, s.y(3)); assertEquals(1, s.z(3));
    }

    @Test void readsBlockStateProperties() throws Exception {
        Map<String, Object> regions = new LinkedHashMap<>();
        regions.put("main", region(new int[]{0, 0, 0}, new int[]{1, 1, 1},
                new ArrayList<>(List.of(entry("minecraft:air"),
                        entry("minecraft:oak_stairs", "facing", "north", "half", "bottom"))),
                new long[]{1L}));
        Schematic s = read(regions, "stairs");
        assertEquals(BlockSpec.parse("minecraft:oak_stairs[facing=north,half=bottom]"), s.state(0));
    }

    @Test void negativeSizeExtendsBackwardsFromPosition() throws Exception {
        // size x = -2 at position x=5 covers x = 4..5. Index 0 is the min corner (x=4).
        Map<String, Object> regions = new LinkedHashMap<>();
        regions.put("main", region(new int[]{5, 0, 0}, new int[]{-2, 1, 1},
                new ArrayList<>(List.of(entry("minecraft:air"), entry("minecraft:stone"), entry("minecraft:dirt"))),
                new long[]{1L | (2L << 2)})); // x=4 stone, x=5 dirt
        Schematic s = read(regions, "neg");
        assertEquals(2, s.size());
        assertEquals("minecraft:stone", s.state(0).id());
        assertEquals("minecraft:dirt", s.state(1).id());
        assertEquals(0, s.x(0)); assertEquals(1, s.x(1)); // normalized to min corner
    }

    @Test void negativeYSizeIsPlacedRelativeToOtherRegions() throws Exception {
        // region b: Position y=5, Size y=-2 covers y = 4..5 (index 0 = y 4). Region a pins the origin at y=0.
        List<Object> pal = new ArrayList<>(List.of(entry("minecraft:air"), entry("minecraft:stone"), entry("minecraft:dirt")));
        Map<String, Object> regions = new LinkedHashMap<>();
        regions.put("a", region(new int[]{0, 0, 0}, new int[]{1, 1, 1}, pal, new long[]{1L}));
        regions.put("b", region(new int[]{0, 5, 0}, new int[]{1, -2, 1}, pal, new long[]{2L | (2L << 2)}));
        Schematic s = read(regions, "negy");
        assertEquals(3, s.size());
        assertEquals(0, s.y(0));
        assertEquals(4, s.y(1));
        assertEquals(5, s.y(2));
    }

    @Test void multipleRegionsKeepTheirRelativePositions() throws Exception {
        Map<String, Object> regions = new LinkedHashMap<>();
        List<Object> pal = new ArrayList<>(List.of(entry("minecraft:air"), entry("minecraft:stone")));
        regions.put("a", region(new int[]{0, 0, 0}, new int[]{1, 1, 1}, pal, new long[]{1L}));
        regions.put("b", region(new int[]{3, 2, 0}, new int[]{1, 1, 1}, pal, new long[]{1L}));
        Schematic s = read(regions, "two");
        assertEquals(2, s.size());
        assertEquals(4, s.sizeX());
        assertEquals(3, s.sizeY());
        assertEquals(3, s.x(1)); assertEquals(2, s.y(1));
    }

    @Test void rejectsTruncatedBlockStates() {
        Map<String, Object> regions = new LinkedHashMap<>();
        regions.put("main", region(new int[]{0, 0, 0}, new int[]{40, 1, 1},
                new ArrayList<>(List.of(entry("minecraft:air"), entry("minecraft:stone"))), new long[]{1L}));
        assertThrows(IOException.class, () -> read(regions, "bad"));
    }

    @Test void rejectsFileWithoutRegions() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("Version", 6);
        assertThrows(IOException.class, () ->
                LitematicReader.read(new ByteArrayInputStream(NbtTestWriter.gzip(root)), "x"));
    }

    @Test void rejectsAllAirSchematic() {
        Map<String, Object> regions = new LinkedHashMap<>();
        regions.put("main", region(new int[]{0, 0, 0}, new int[]{2, 1, 1},
                new ArrayList<>(List.of(entry("minecraft:air"), entry("minecraft:stone"))), new long[]{0L}));
        assertThrows(IOException.class, () -> read(regions, "empty"));
    }

    @Test void rejectsNonGzipInput() {
        assertThrows(IOException.class, () ->
                LitematicReader.read(new ByteArrayInputStream("not nbt".getBytes()), "x"));
    }

    @Test void rejectsHostileArrayLengthWithoutAllocating() throws Exception {
        // Hand-built NBT claiming a 2-billion-entry long array in a tiny file.
        var bytes = new java.io.ByteArrayOutputStream();
        try (var out = new java.io.DataOutputStream(new java.util.zip.GZIPOutputStream(bytes))) {
            out.writeByte(10); out.writeShort(0);
            out.writeByte(12); out.writeShort(1); out.writeByte('a');
            out.writeInt(Integer.MAX_VALUE);
        }
        assertThrows(IOException.class, () -> Nbt.readGzip(new ByteArrayInputStream(bytes.toByteArray())));
    }
}
