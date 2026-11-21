package com.penguin.client;

import com.penguin.core.BlockSpec;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.TreeMap;

/**
 * Every call into Minecraft's world/block API lives here, so a version bump only touches this file
 * (plus Hologram, HudPanel and PenguinClient).
 */
final class McBridge {
    private McBridge() {}

    /** Converts a world block state into the mod's own plain block description. */
    static BlockSpec spec(BlockState state) {
        TreeMap<String, String> props = new TreeMap<>();
        for (Property<?> p : state.getProperties()) props.put(p.getName(), valueName(state, p));
        String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        return new BlockSpec(id, props);
    }

    private static <T extends Comparable<T>> String valueName(BlockState state, Property<T> p) {
        return p.getName(state.getValue(p));
    }

    static void message(Minecraft mc, String text) {
        if (mc.player != null) mc.player.displayClientMessage(Component.literal("[Penguin] " + text), true);
    }
}
