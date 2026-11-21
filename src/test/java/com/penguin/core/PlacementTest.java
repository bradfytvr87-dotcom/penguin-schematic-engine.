package com.penguin.core;

import com.penguin.core.PlacementChecker.Status;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlacementTest {
    private static BlockSpec b(String s) { return BlockSpec.parse(s); }

    @Test void correctBlockIsCorrect() {
        assertEquals(Status.CORRECT, PlacementChecker.check(b("minecraft:stone"), b("minecraft:stone"), false));
    }

    @Test void schematicOnlyConstrainsPropertiesItNames() {
        // schematic says facing=north only; world stair also has half/shape/waterlogged
        BlockSpec want = b("minecraft:oak_stairs[facing=north]");
        BlockSpec have = b("minecraft:oak_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]");
        assertEquals(Status.CORRECT, PlacementChecker.check(want, have, false));
    }

    @Test void wrongFacingIsWrongState() {
        BlockSpec want = b("minecraft:oak_stairs[facing=north,half=bottom]");
        BlockSpec have = b("minecraft:oak_stairs[facing=south,half=bottom]");
        assertEquals(Status.WRONG_STATE, PlacementChecker.check(want, have, false));
    }

    @Test void airAndReplaceablesCountAsNotPlacedYet() {
        BlockSpec want = b("minecraft:stone");
        assertEquals(Status.EMPTY, PlacementChecker.check(want, b("minecraft:air"), true));
        assertEquals(Status.EMPTY, PlacementChecker.check(want, b("minecraft:short_grass"), true));
        assertEquals(Status.EMPTY, PlacementChecker.check(want, b("minecraft:cave_air"), false));
    }

    @Test void solidWrongBlockIsFlagged() {
        assertEquals(Status.WRONG_BLOCK, PlacementChecker.check(b("minecraft:stone"), b("minecraft:dirt"), false));
    }

    @Test void unloadedChunkIsUnknown() {
        assertEquals(Status.UNLOADED, PlacementChecker.check(b("minecraft:stone"), null, false));
    }

    @Test void progressFindsFirstUnfinishedAndSkipsToBack() {
        BuildProgress p = new BuildProgress(4);
        assertEquals(0, p.cursor());
        p.setDone(0, true);
        p.setDone(1, true);
        assertEquals(2, p.cursor());
        p.skipCurrent();
        assertEquals(3, p.cursor());
        p.skipCurrent();
        assertEquals(2, p.cursor()); // everything left is skipped, so fall back to the first unfinished
        p.setDone(2, true);
        p.setDone(3, true);
        assertTrue(p.complete());
        assertEquals(-1, p.cursor());
        assertEquals(4, p.doneCount());
    }

    @Test void brokenBlocksComeBackAsNotDone() {
        BuildProgress p = new BuildProgress(2);
        p.setDone(0, true);
        p.setDone(0, false);
        assertEquals(0, p.cursor());
    }

    @Test void safetyPolicy() {
        assertTrue(PlacementSafety.isSafeManualMode(false, false));
        assertFalse(PlacementSafety.isSafeManualMode(true, false));
        assertFalse(PlacementSafety.isSafeManualMode(false, true));
        assertTrue(PlacementSafety.isWithinPreviewDistance(4, 8));
        assertFalse(PlacementSafety.isWithinPreviewDistance(9, 8));
        assertFalse(PlacementSafety.isWithinPreviewDistance(Double.NaN, 8));
        assertTrue(PlacementSafety.isWithinReach(3.0));
        assertFalse(PlacementSafety.isWithinReach(7.0));
        assertFalse(PlacementSafety.isWithinReach(Double.NaN));
    }

    @Test void jsonLoaderReadsStatesAndRejectsGarbage() throws Exception {
        Schematic s = JsonSchematicLoader.parse("""
            {"name":"j","blocks":[{"x":0,"y":0,"z":0,"block":"minecraft:stone"},
                                  {"x":1,"y":0,"z":0,"block":"minecraft:oak_stairs[facing=east]"}]}""");
        assertEquals(2, s.size());
        assertEquals("east", s.state(1).props().get("facing"));
        assertThrows(java.io.IOException.class, () -> JsonSchematicLoader.parse("{}"));
        assertThrows(java.io.IOException.class, () -> JsonSchematicLoader.parse("[1,2]"));
        assertThrows(java.io.IOException.class, () -> JsonSchematicLoader.parse(
                "{\"name\":\"x\",\"blocks\":[{\"x\":0,\"y\":0,\"z\":0,\"block\":\"stone\"}]}"));
        assertThrows(java.io.IOException.class, () -> JsonSchematicLoader.parse(
                "{\"name\":\"x\",\"blocks\":[{\"x\":0,\"y\":0,\"z\":0}]}"));
    }
}
