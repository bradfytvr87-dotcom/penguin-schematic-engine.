package com.penguin.core;

import java.util.Collections;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.regex.Pattern;

/** A block id plus block-state properties, e.g. {@code minecraft:oak_stairs[facing=north,half=bottom]}. */
public record BlockSpec(String id, SortedMap<String, String> props) {
    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    private static final Pattern TOKEN = Pattern.compile("[a-z0-9_]+");

    public BlockSpec {
        if (id == null || !ID.matcher(id).matches())
            throw new IllegalArgumentException("Invalid namespaced block id: " + id);
        TreeMap<String, String> copy = new TreeMap<>();
        if (props != null) {
            for (Map.Entry<String, String> e : props.entrySet()) {
                if (e.getKey() == null || !TOKEN.matcher(e.getKey()).matches()
                        || e.getValue() == null || !TOKEN.matcher(e.getValue()).matches())
                    throw new IllegalArgumentException("Invalid block property: " + e.getKey() + "=" + e.getValue());
                copy.put(e.getKey(), e.getValue());
            }
        }
        props = Collections.unmodifiableSortedMap(copy);
    }

    public static BlockSpec of(String id) {
        return new BlockSpec(id, null);
    }

    public static BlockSpec of(String id, Map<String, String> props) {
        return new BlockSpec(id, props == null ? null : new TreeMap<>(props));
    }

    /** Parses {@code id} or {@code id[k=v,k2=v2]}. */
    public static BlockSpec parse(String text) {
        if (text == null) throw new IllegalArgumentException("Block text is null");
        int br = text.indexOf('[');
        if (br < 0) return of(text.trim());
        if (!text.endsWith("]")) throw new IllegalArgumentException("Unterminated block properties: " + text);
        String id = text.substring(0, br).trim();
        TreeMap<String, String> p = new TreeMap<>();
        String body = text.substring(br + 1, text.length() - 1).trim();
        if (!body.isEmpty()) {
            for (String pair : body.split(",")) {
                int eq = pair.indexOf('=');
                if (eq <= 0) throw new IllegalArgumentException("Bad property: " + pair);
                p.put(pair.substring(0, eq).trim(), pair.substring(eq + 1).trim());
            }
        }
        return new BlockSpec(id, p);
    }

    public boolean isAir() {
        return id.equals("minecraft:air") || id.equals("minecraft:cave_air") || id.equals("minecraft:void_air");
    }

    /** True when {@code actual} is the same block and agrees on every property this spec names. */
    public boolean matches(BlockSpec actual) {
        if (actual == null || !id.equals(actual.id)) return false;
        for (Map.Entry<String, String> e : props.entrySet()) {
            if (!e.getValue().equals(actual.props.get(e.getKey()))) return false;
        }
        return true;
    }

    @Override public String toString() {
        if (props.isEmpty()) return id;
        StringBuilder sb = new StringBuilder(id).append('[');
        boolean first = true;
        for (Map.Entry<String, String> e : props.entrySet()) {
            if (!first) sb.append(',');
            sb.append(e.getKey()).append('=').append(e.getValue());
            first = false;
        }
        return sb.append(']').toString();
    }
}
