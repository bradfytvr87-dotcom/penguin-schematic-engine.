package com.penguin.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SchematicTransformsTest {
    private static Schematic of(Object... xyzBlock) {
        Schematic.Builder b = Schematic.builder();
        for (int i = 0; i < xyzBlock.length; i += 4)
            b.add((int) xyzBlock[i], (int) xyzBlock[i + 1], (int) xyzBlock[i + 2], BlockSpec.parse((String) xyzBlock[i + 3]));
        return b.build("t");
    }

    private static int indexAt(Schematic s, int x, int y, int z) {
        for (int i = 0; i < s.size(); i++) if (s.x(i) == x && s.y(i) == y && s.z(i) == z) return i;
        return -1;
    }

    @Test void rotateMovesBlocksClockwiseAndRenormalizes() {
        // L shape: (0,0,0) stone, (2,0,0) dirt  (east of origin). Clockwise from above: east -> south.
        Schematic s = of(0, 0, 0, "minecraft:stone", 2, 0, 0, "minecraft:dirt");
        Schematic r = SchematicTransforms.rotateY(s);
        assertEquals(1, r.sizeX());
        assertEquals(3, r.sizeZ());
        int dirt = indexAt(r, 0, 0, 2), stone = indexAt(r, 0, 0, 0);
        assertTrue(dirt >= 0 && stone >= 0);
        assertEquals("minecraft:dirt", r.state(dirt).id());
        assertEquals("minecraft:stone", r.state(stone).id());
    }

    @Test void fourRotationsReturnTheOriginal() {
        Schematic s = of(0, 0, 0, "minecraft:stone", 3, 1, 2, "minecraft:oak_stairs[facing=north,half=bottom,shape=inner_left]",
                1, 0, 1, "minecraft:oak_fence[east=true,north=false,south=false,west=true]");
        Schematic r = s;
        for (int i = 0; i < 4; i++) r = SchematicTransforms.rotateY(r);
        assertEquals(s.size(), r.size());
        for (int i = 0; i < s.size(); i++) {
            int j = indexAt(r, s.x(i), s.y(i), s.z(i));
            assertTrue(j >= 0);
            assertEquals(s.state(i), r.state(j));
        }
    }

    @Test void mirroringTwiceReturnsTheOriginal() {
        Schematic s = of(0, 0, 0, "minecraft:stone", 3, 1, 2, "minecraft:oak_stairs[facing=east,half=top,shape=outer_right]");
        for (var f : java.util.List.<java.util.function.UnaryOperator<Schematic>>of(
                SchematicTransforms::mirrorX, SchematicTransforms::mirrorZ)) {
            Schematic r = f.apply(f.apply(s));
            for (int i = 0; i < s.size(); i++) {
                int j = indexAt(r, s.x(i), s.y(i), s.z(i));
                assertTrue(j >= 0);
                assertEquals(s.state(i), r.state(j));
            }
        }
    }

    @Test void rotatingATurnsStairFacingClockwise() {
        Schematic s = of(0, 0, 0, "minecraft:oak_stairs[facing=north,half=bottom]");
        assertEquals("east", SchematicTransforms.rotateY(s).state(0).props().get("facing"));
        assertEquals("south", SchematicTransforms.rotateY(SchematicTransforms.rotateY(s)).state(0).props().get("facing"));
        assertEquals("bottom", SchematicTransforms.rotateY(s).state(0).props().get("half"));
    }

    @Test void rotationChangesAxisAndSides() {
        Schematic log = of(0, 0, 0, "minecraft:oak_log[axis=x]");
        assertEquals("z", SchematicTransforms.rotateY(log).state(0).props().get("axis"));
        Schematic vertical = of(0, 0, 0, "minecraft:oak_log[axis=y]");
        assertEquals("y", SchematicTransforms.rotateY(vertical).state(0).props().get("axis"));

        Schematic fence = of(0, 0, 0, "minecraft:oak_fence[east=false,north=true,south=false,west=false]");
        var p = SchematicTransforms.rotateY(fence).state(0).props();
        assertEquals("true", p.get("east"));   // the north arm now points east
        assertEquals("false", p.get("north"));
    }

    @Test void signRotationAdvancesByFourSteps() {
        Schematic sign = of(0, 0, 0, "minecraft:oak_sign[rotation=14]");
        assertEquals("2", SchematicTransforms.rotateY(sign).state(0).props().get("rotation")); // (14+4)&15
    }

    @Test void mirrorXSwapsEastWestAndStairHandedness() {
        Schematic s = of(0, 0, 0, "minecraft:oak_stairs[facing=east,half=bottom,shape=inner_left]");
        var p = SchematicTransforms.mirrorX(s).state(0).props();
        assertEquals("west", p.get("facing"));
        assertEquals("inner_right", p.get("shape"));
        Schematic door = of(0, 0, 0, "minecraft:oak_door[facing=north,hinge=left,half=lower]");
        assertEquals("right", SchematicTransforms.mirrorX(door).state(0).props().get("hinge"));
        assertEquals("north", SchematicTransforms.mirrorX(door).state(0).props().get("facing"));
    }

    @Test void mirrorZSwapsNorthSouth() {
        Schematic s = of(0, 0, 0, "minecraft:oak_stairs[facing=north,half=bottom]");
        assertEquals("south", SchematicTransforms.mirrorZ(s).state(0).props().get("facing"));
        Schematic rot = of(0, 0, 0, "minecraft:oak_sign[rotation=2]");
        assertEquals("6", SchematicTransforms.mirrorZ(rot).state(0).props().get("rotation")); // (8-2)&15
    }

    @Test void mirrorMovesPositions() {
        Schematic s = of(0, 0, 0, "minecraft:stone", 2, 0, 0, "minecraft:dirt");
        Schematic m = SchematicTransforms.mirrorX(s);
        assertEquals("minecraft:dirt", m.state(indexAt(m, 0, 0, 0)).id());
        assertEquals("minecraft:stone", m.state(indexAt(m, 2, 0, 0)).id());
    }

    @Test void railShapesRotate() {
        Schematic rail = of(0, 0, 0, "minecraft:rail[shape=ascending_north]");
        assertEquals("ascending_east", SchematicTransforms.rotateY(rail).state(0).props().get("shape"));
        Schematic straight = of(0, 0, 0, "minecraft:rail[shape=north_south]");
        assertEquals("east_west", SchematicTransforms.rotateY(straight).state(0).props().get("shape"));
    }

    @Test void builderDropsAirAndKeepsLastOfDuplicates() {
        Schematic s = Schematic.builder()
                .add(0, 0, 0, BlockSpec.of("minecraft:air"))
                .add(1, 0, 0, BlockSpec.of("minecraft:stone"))
                .add(1, 0, 0, BlockSpec.of("minecraft:dirt"))
                .build("dup");
        assertEquals(1, s.size());
        assertEquals("minecraft:dirt", s.state(0).id());
        assertEquals(0, s.x(0)); // normalized
    }

    @Test void builderRejectsHugeExtent() {
        var b = Schematic.builder().add(0, 0, 0, BlockSpec.of("minecraft:stone")).add(Schematic.MAX_EXTENT, 0, 0, BlockSpec.of("minecraft:stone"));
        assertThrows(IllegalArgumentException.class, () -> b.build("big"));
    }

    @Test void rejectsInvalidBlockIds() {
        assertThrows(IllegalArgumentException.class, () -> BlockSpec.parse("stone"));
        assertThrows(IllegalArgumentException.class, () -> BlockSpec.parse("minecraft:stone[facing=north"));
        assertThrows(IllegalArgumentException.class, () -> BlockSpec.parse("minecraft:stone[bad key=1]"));
    }

    @Test void rejectsEmptySchematic() {
        assertThrows(IllegalArgumentException.class, () -> Schematic.builder().build("empty"));
    }
}
