package com.falazar.farmupcraft.client;

import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = FarmUpCraftNeoForge.MOD_ID, value = Dist.CLIENT)
public final class GeneralMenuEvents {
    private static KeyMapping openMenu;
    private static boolean requested;

    private GeneralMenuEvents() {}

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        openMenu = new KeyMapping("key.farmupcraft.menu", KeyConflictContext.IN_GAME, KeyModifier.ALT,
                InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F, KeyMapping.Category.MISC);
        event.register(openMenu);
    }

    @SubscribeEvent
    public static void registerCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("menu").executes(context -> {
            requested = true;
            return 1;
        }));
        event.getDispatcher().register(Commands.literal("fmap")
                .executes(context -> {
                    context.getSource().sendSystemMessage(net.minecraft.network.chat.Component.literal(
                            "[Map] /fmap lakes: only lakes/islands on both maps; /fmap all: restore all; "
                                    + "/fmap hide: hide lakes/islands on both maps. "
                                    + "Rename saved lakes/islands: /scan rename-lakes confirm (operator)."));
                    return 1;
                })
                .then(Commands.literal("lakes").executes(context ->
                        WaypointPayloadHandler.setMapFilter(true, context.getSource()::sendSystemMessage)))
                .then(Commands.literal("all").executes(context ->
                        WaypointPayloadHandler.setMapFilter(false, context.getSource()::sendSystemMessage)))
                .then(Commands.literal("hide").executes(context ->
                        WaypointPayloadHandler.hideMapFeatures(context.getSource()::sendSystemMessage)))
                .then(Commands.literal("toggle").executes(context ->
                        WaypointPayloadHandler.toggleMapFeatures(context.getSource()::sendSystemMessage))));
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        var minecraft = Minecraft.getInstance();
        boolean pressed = false;
        if (openMenu != null) {
            while (openMenu.consumeClick()) pressed = true;
        }
        boolean shouldOpen = requested || (pressed && minecraft.gui.screen() == null);
        requested = false;
        if (shouldOpen && minecraft.player != null && minecraft.getConnection() != null) {
            minecraft.gui.setScreen(new GeneralMenuScreen());
        }
    }
}
