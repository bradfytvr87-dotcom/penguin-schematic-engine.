package com.penguin.core;

import java.util.Map;
import java.util.TreeMap;

/**
 * Rotates and mirrors block-state properties so a rotated stair still faces the right way.
 * Handled: facing, axis, rotation (signs/banners/heads), north/east/south/west side flags (fences, walls,
 * panes, vines), stair shape, rail shape, door hinge and chest type.
 * Not handled: bed/banner-wall specifics, orientation (jigsaw/crafter), and any modded property.
 */
final class StateTransform {
    private StateTransform() {}

    enum Op { ROTATE_CW, MIRROR_X, MIRROR_Z }

    private static final Map<String, String> FACING_CW = Map.of(
            "north", "east", "east", "south", "south", "west", "west", "north");
    private static final Map<String, String> FACING_MX = Map.of("east", "west", "west", "east");
    private static final Map<String, String> FACING_MZ = Map.of("north", "south", "south", "north");

    private static final Map<String, String> SHAPE_CW = Map.ofEntries(
            Map.entry("north_south", "east_west"), Map.entry("east_west", "north_south"),
            Map.entry("ascending_east", "ascending_south"), Map.entry("ascending_south", "ascending_west"),
            Map.entry("ascending_west", "ascending_north"), Map.entry("ascending_north", "ascending_east"),
            Map.entry("south_east", "south_west"), Map.entry("south_west", "north_west"),
            Map.entry("north_west", "north_east"), Map.entry("north_east", "south_east"));
    private static final Map<String, String> SHAPE_MX = Map.ofEntries(
            Map.entry("inner_left", "inner_right"), Map.entry("inner_right", "inner_left"),
            Map.entry("outer_left", "outer_right"), Map.entry("outer_right", "outer_left"),
            Map.entry("ascending_east", "ascending_west"), Map.entry("ascending_west", "ascending_east"),
            Map.entry("north_east", "north_west"), Map.entry("north_west", "north_east"),
            Map.entry("south_east", "south_west"), Map.entry("south_west", "south_east"));
    private static final Map<String, String> SHAPE_MZ = Map.ofEntries(
            Map.entry("inner_left", "inner_right"), Map.entry("inner_right", "inner_left"),
            Map.entry("outer_left", "outer_right"), Map.entry("outer_right", "outer_left"),
            Map.entry("ascending_north", "ascending_south"), Map.entry("ascending_south", "ascending_north"),
            Map.entry("north_east", "south_east"), Map.entry("south_east", "north_east"),
            Map.entry("north_west", "south_west"), Map.entry("south_west", "north_west"));
    private static final Map<String, String> LEFT_RIGHT = Map.of("left", "right", "right", "left");

    static BlockSpec apply(BlockSpec spec, Op op) {
        if (spec.props().isEmpty()) return spec;
        TreeMap<String, String> p = new TreeMap<>(spec.props());
        switch (op) {
            case ROTATE_CW -> {
                swapValue(p, "facing", FACING_CW);
                swapValue(p, "shape", SHAPE_CW);
                String axis = p.get("axis");
                if ("x".equals(axis)) p.put("axis", "z");
                else if ("z".equals(axis)) p.put("axis", "x");
                rotationAdd(p, 4, 15);
                String n = p.get("north"), e = p.get("east"), s = p.get("south"), w = p.get("west");
                if (n != null || e != null || s != null || w != null) {
                    put(p, "east", n);
                    put(p, "south", e);
                    put(p, "west", s);
                    put(p, "north", w);
                }
            }
            case MIRROR_X -> {
                swapValue(p, "facing", FACING_MX);
                swapValue(p, "shape", SHAPE_MX);
                swapValue(p, "hinge", LEFT_RIGHT);
                swapValue(p, "type", LEFT_RIGHT);
                swapKeys(p, "east", "west");
                rotationMirror(p, 16);
            }
            case MIRROR_Z -> {
                swapValue(p, "facing", FACING_MZ);
                swapValue(p, "shape", SHAPE_MZ);
                swapValue(p, "hinge", LEFT_RIGHT);
                swapValue(p, "type", LEFT_RIGHT);
                swapKeys(p, "north", "south");
                rotationMirror(p, 8);
            }
        }
        return new BlockSpec(spec.id(), p);
    }

    private static void put(TreeMap<String, String> p, String key, String value) {
        if (value == null) p.remove(key);
        else p.put(key, value);
    }

    private static void swapValue(TreeMap<String, String> p, String key, Map<String, String> table) {
        String v = p.get(key);
        if (v != null) p.put(key, table.getOrDefault(v, v));
    }

    private static void swapKeys(TreeMap<String, String> p, String a, String b) {
        String va = p.get(a), vb = p.get(b);
        if (va == null && vb == null) return;
        put(p, a, vb);
        put(p, b, va);
    }

    private static void rotationAdd(TreeMap<String, String> p, int add, int mask) {
        Integer r = rotation(p);
        if (r != null) p.put("rotation", Integer.toString((r + add) & mask));
    }

    private static void rotationMirror(TreeMap<String, String> p, int base) {
        Integer r = rotation(p);
        if (r != null) p.put("rotation", Integer.toString((base - r) & 15));
    }

    private static Integer rotation(Map<String, String> p) {
        String r = p.get("rotation");
        if (r == null) return null;
        try {
            return Integer.parseInt(r);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
