package com.penguin.core;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Minimal NBT reader (big-endian, gzip). Values: Byte, Short, Integer, Long, Float, Double, byte[], int[], long[],
 * String, List&lt;Object&gt;, Map&lt;String,Object&gt;. Bounded so a hostile file cannot exhaust memory.
 */
public final class Nbt {
    public static final long MAX_FILE_BYTES = 64L * 1024 * 1024;
    public static final long MAX_DECOMPRESSED_BYTES = 256L * 1024 * 1024;
    private static final int MAX_DEPTH = 64;

    private Nbt() {}

    public static Map<String, Object> readGzipFile(Path path) throws IOException {
        if (!Files.isRegularFile(path)) throw new IOException("File not found: " + path);
        if (Files.size(path) > MAX_FILE_BYTES) throw new IOException("File exceeds " + (MAX_FILE_BYTES >> 20) + " MB limit");
        try (InputStream in = Files.newInputStream(path)) {
            return readGzip(in);
        }
    }

    public static Map<String, Object> readGzip(InputStream raw) throws IOException {
        try (var in = new LimitedInputStream(new GZIPInputStream(new BufferedInputStream(raw)), MAX_DECOMPRESSED_BYTES)) {
            return read(new DataInputStream(in), in);
        }
    }

    private static Map<String, Object> read(DataInputStream in, LimitedInputStream budget) throws IOException {
        int type = in.readUnsignedByte();
        if (type != 10) throw new IOException("Not an NBT file (root tag must be a compound)");
        readString(in); // root name, unused
        Object root = readPayload(in, 10, 0, budget);
        @SuppressWarnings("unchecked") Map<String, Object> map = (Map<String, Object>) root;
        return map;
    }

    private static Object readPayload(DataInputStream in, int type, int depth, LimitedInputStream budget) throws IOException {
        if (depth > MAX_DEPTH) throw new IOException("NBT nesting too deep");
        switch (type) {
            case 1: return in.readByte();
            case 2: return in.readShort();
            case 3: return in.readInt();
            case 4: return in.readLong();
            case 5: return in.readFloat();
            case 6: return in.readDouble();
            case 7: {
                int n = checkLen(in.readInt(), 1, budget);
                byte[] a = new byte[n];
                in.readFully(a);
                return a;
            }
            case 8: return readString(in);
            case 9: {
                int elemType = in.readUnsignedByte();
                int n = in.readInt();
                if (n < 0) throw new IOException("Negative list length");
                if (n > 0 && elemType == 0) throw new IOException("List of END tags");
                if (n > budget.remaining()) throw new IOException("List length exceeds file size");
                List<Object> list = new ArrayList<>(Math.min(n, 1024));
                for (int i = 0; i < n; i++) list.add(readPayload(in, elemType, depth + 1, budget));
                return list;
            }
            case 10: {
                Map<String, Object> map = new LinkedHashMap<>();
                while (true) {
                    int t = in.readUnsignedByte();
                    if (t == 0) break;
                    String key = readString(in);
                    map.put(key, readPayload(in, t, depth + 1, budget));
                }
                return map;
            }
            case 11: {
                int n = checkLen(in.readInt(), 4, budget);
                int[] a = new int[n];
                for (int i = 0; i < n; i++) a[i] = in.readInt();
                return a;
            }
            case 12: {
                int n = checkLen(in.readInt(), 8, budget);
                long[] a = new long[n];
                for (int i = 0; i < n; i++) a[i] = in.readLong();
                return a;
            }
            default: throw new IOException("Unknown NBT tag type " + type);
        }
    }

    private static int checkLen(int n, int elemBytes, LimitedInputStream budget) throws IOException {
        if (n < 0) throw new IOException("Negative array length");
        if ((long) n * elemBytes > budget.remaining()) throw new IOException("Array length exceeds file size");
        return n;
    }

    private static String readString(DataInputStream in) throws IOException {
        int n = in.readUnsignedShort();
        byte[] b = new byte[n];
        in.readFully(b);
        return new String(b, StandardCharsets.UTF_8);
    }

    // ---- typed accessors -------------------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    public static Map<String, Object> compound(Map<String, Object> m, String key) throws IOException {
        Object v = m.get(key);
        if (v instanceof Map<?, ?> c) return (Map<String, Object>) c;
        throw new IOException("Missing or wrong-typed compound '" + key + "'");
    }

    @SuppressWarnings("unchecked")
    public static List<Object> list(Map<String, Object> m, String key) throws IOException {
        Object v = m.get(key);
        if (v instanceof List<?> l) return (List<Object>) l;
        throw new IOException("Missing or wrong-typed list '" + key + "'");
    }

    public static long[] longArray(Map<String, Object> m, String key) throws IOException {
        Object v = m.get(key);
        if (v instanceof long[] a) return a;
        throw new IOException("Missing or wrong-typed long array '" + key + "'");
    }

    public static int intValue(Map<String, Object> m, String key) throws IOException {
        Object v = m.get(key);
        if (v instanceof Number n) return n.intValue();
        throw new IOException("Missing or wrong-typed number '" + key + "'");
    }

    public static String string(Map<String, Object> m, String key) throws IOException {
        Object v = m.get(key);
        if (v instanceof String s) return s;
        throw new IOException("Missing or wrong-typed string '" + key + "'");
    }

    /** Counts bytes read and fails once the decompressed size passes a limit. */
    private static final class LimitedInputStream extends FilterInputStream {
        private final long limit;
        private long read;

        LimitedInputStream(InputStream in, long limit) {
            super(in);
            this.limit = limit;
        }

        long remaining() { return Math.max(0, limit - read); }

        private void count(long n) throws IOException {
            if (n > 0) {
                read += n;
                if (read > limit) throw new IOException("Decompressed data exceeds " + (limit >> 20) + " MB limit");
            }
        }

        @Override public int read() throws IOException {
            int b = super.read();
            if (b >= 0) count(1);
            return b;
        }

        @Override public int read(byte[] buf, int off, int len) throws IOException {
            int n = super.read(buf, off, len);
            count(n);
            return n;
        }
    }
}
