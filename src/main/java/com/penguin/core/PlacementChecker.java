package com.penguin.core;

/**
 * Compares the block the schematic wants with the block that is actually in the world.
 * Pure logic: the Minecraft layer converts world blocks into {@link BlockSpec} and feeds them in.
 */
public final class PlacementChecker {
    private PlacementChecker() {}

    public enum Status {
        /** Right block, and every property the schematic names matches. */
        CORRECT,
        /** Right block, wrong state (for example a stair facing the wrong way). */
        WRONG_STATE,
        /** A different, solid block is in the way and must be broken first. */
        WRONG_BLOCK,
        /** Air, water, tall grass, or anything else you can place over. */
        EMPTY,
        /** The chunk is not loaded, so nothing can be said. */
        UNLOADED
    }

    /**
     * @param expected   block wanted by the schematic
     * @param actual     block found in the world, or null if its chunk is not loaded
     * @param replaceable true when the world block can simply be placed over (air, fluid, grass...)
     */
    public static Status check(BlockSpec expected, BlockSpec actual, boolean replaceable) {
        if (actual == null) return Status.UNLOADED;
        if (expected.id().equals(actual.id())) {
            return expected.matches(actual) ? Status.CORRECT : Status.WRONG_STATE;
        }
        if (actual.isAir() || replaceable) return Status.EMPTY;
        return Status.WRONG_BLOCK;
    }

    public static String describe(Status s) {
        return switch (s) {
            case CORRECT -> "correct";
            case WRONG_STATE -> "wrong rotation or state";
            case WRONG_BLOCK -> "wrong block in the way";
            case EMPTY -> "not placed yet";
            case UNLOADED -> "chunk not loaded";
        };
    }
}
