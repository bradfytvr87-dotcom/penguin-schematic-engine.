package com.penguin.core;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

/** Tiny NBT writer for building test fixtures. Mirrors the value types the reader returns. */
final class NbtTestWriter {
    private NbtTestWriter() {}

    static byte[] gzip(Map<String, Object> root) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(bytes))) {
            out.writeByte(10);
            writeString(out, "");
            writePayload(out, root);
        }
        return bytes.toByteArray();
    }

    private static int typeOf(Object v) {
        if (v instanceof Byte) return 1;
        if (v instanceof Short) return 2;
        if (v instanceof Integer) return 3;
        if (v instanceof Long) return 4;
        if (v instanceof byte[]) return 7;
        if (v instanceof String) return 8;
        if (v instanceof List) return 9;
        if (v instanceof Map) return 10;
        if (v instanceof long[]) return 12;
        throw new IllegalArgumentException("Unsupported: " + v.getClass());
    }

    @SuppressWarnings("unchecked")
    private static void writePayload(DataOutputStream out, Object v) throws IOException {
        switch (typeOf(v)) {
            case 1 -> out.writeByte((Byte) v);
            case 2 -> out.writeShort((Short) v);
            case 3 -> out.writeInt((Integer) v);
            case 4 -> out.writeLong((Long) v);
            case 7 -> { byte[] a = (byte[]) v; out.writeInt(a.length); out.write(a); }
            case 8 -> writeString(out, (String) v);
            case 9 -> {
                List<Object> l = (List<Object>) v;
                out.writeByte(l.isEmpty() ? 0 : typeOf(l.get(0)));
                out.writeInt(l.size());
                for (Object o : l) writePayload(out, o);
            }
            case 10 -> {
                for (Map.Entry<String, Object> e : ((Map<String, Object>) v).entrySet()) {
                    out.writeByte(typeOf(e.getValue()));
                    writeString(out, e.getKey());
                    writePayload(out, e.getValue());
                }
                out.writeByte(0);
            }
            case 12 -> { long[] a = (long[]) v; out.writeInt(a.length); for (long x : a) out.writeLong(x); }
            default -> throw new IllegalStateException();
        }
    }

    private static void writeString(DataOutputStream out, String s) throws IOException {
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        out.writeShort(b.length);
        out.write(b);
    }
}
