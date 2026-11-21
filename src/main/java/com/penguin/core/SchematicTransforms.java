package com.penguin.core;

/** Rotation and mirroring. Results are re-normalized so the min corner stays at 0,0,0. */
public final class SchematicTransforms {
    private SchematicTransforms() {}

    /** Rotate 90 degrees clockwise when viewed from above (north becomes east). */
    public static Schematic rotateY(Schematic s) {
        return map(s, StateTransform.Op.ROTATE_CW);
    }

    /** Flip along the X axis (east and west swap). */
    public static Schematic mirrorX(Schematic s) {
        return map(s, StateTransform.Op.MIRROR_X);
    }

    /** Flip along the Z axis (north and south swap). */
    public static Schematic mirrorZ(Schematic s) {
        return map(s, StateTransform.Op.MIRROR_Z);
    }

    private static Schematic map(Schematic s, StateTransform.Op op) {
        BlockSpec[] mapped = new BlockSpec[s.palette().size()];
        for (int i = 0; i < mapped.length; i++) mapped[i] = StateTransform.apply(s.palette().get(i), op);
        // palette order is stable, so look the mapped spec up by the source block's spec
        java.util.Map<BlockSpec, BlockSpec> lookup = new java.util.HashMap<>();
        for (int i = 0; i < mapped.length; i++) lookup.put(s.palette().get(i), mapped[i]);

        Schematic.Builder b = Schematic.builder();
        for (int i = 0; i < s.size(); i++) {
            int x = s.x(i), y = s.y(i), z = s.z(i);
            BlockSpec spec = lookup.get(s.state(i));
            switch (op) {
                case ROTATE_CW -> b.add(-z, y, x, spec);
                case MIRROR_X -> b.add(-x, y, z, spec);
                case MIRROR_Z -> b.add(x, y, -z, spec);
            }
        }
        return b.build(s.name());
    }
}
