package com.penguin.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/**
 * Penguin Schematic Engine: a manual schematic guide. It shows where blocks go and checks your work;
 * the player places every block. Nothing here sends placement packets or automates input.
 */
public final class PenguinClient implements ClientModInitializer {
    public static final String MOD_ID = "penguin_schematic_engine";

    private static KeyMapping load, anchor, hud, hologram, layer, rotate, mirrorX, mirrorZ, skip, verify;

    @Override
    public void onInitializeClient() {
        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
        load = key("load", InputConstants.KEY_O, category);
        anchor = key("anchor", InputConstants.KEY_K, category);
        hud = key("hud", InputConstants.KEY_H, category);
        hologram = key("hologram", InputConstants.KEY_V, category);
        layer = key("layer", InputConstants.KEY_L, category);
        rotate = key("rotate", InputConstants.KEY_R, category);
        mirrorX = key("mirror_x", InputConstants.KEY_M, category);
        mirrorZ = key("mirror_z", InputConstants.KEY_B, category);
        skip = key("skip", InputConstants.KEY_N, category);
        verify = key("verify", InputConstants.KEY_P, category);

        ClientTickEvents.END_CLIENT_TICK.register(PenguinClient::onTick);
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "hud"),
                (graphics, tickCounter) -> HudPanel.render(graphics));
        LevelRenderEvents.BEFORE_GIZMOS.register(context -> Hologram.draw());
    }

    private static KeyMapping key(String name, int code, KeyMapping.Category category) {
        return KeyMappingHelper.registerKeyMapping(
                new KeyMapping("key." + MOD_ID + "." + name, InputConstants.Type.KEYSYM, code, category));
    }

    private static void onTick(Minecraft mc) {
        SchematicSession s = SchematicSession.get();
        while (load.consumeClick()) s.loadNext(mc);
        while (anchor.consumeClick()) s.setAnchor(mc);
        while (hud.consumeClick()) s.toggleHud();
        while (hologram.consumeClick()) s.toggleHologram(mc);
        while (layer.consumeClick()) s.toggleLayer(mc);
        while (rotate.consumeClick()) s.rotate(mc);
        while (mirrorX.consumeClick()) s.mirrorX(mc);
        while (mirrorZ.consumeClick()) s.mirrorZ(mc);
        while (skip.consumeClick()) s.skip(mc);
        while (verify.consumeClick()) s.verifyAllNow(mc);
        s.tick(mc);
    }
}
