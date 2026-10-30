package com.falazar.farmupcraft.client;

import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import com.falazar.farmupcraft.util.FUCTags;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

@EventBusSubscriber(modid = FarmUpCraftNeoForge.MOD_ID, value = Dist.CLIENT)
public final class WeatherSoundManager {
    private WeatherSoundManager() {
    }

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        var stack = event.getItemStack();
        if (stack.is(FUCTags.MODDED_CROPS) || stack.is(FUCTags.MODDED_SEEDS) || stack.is(FUCTags.VANILLA_CROPS)) {
            event.getToolTip().add(Component.literal("Requires: farm plot").withStyle(ChatFormatting.DARK_GREEN));
            event.getToolTip().add(Component.literal("Biome rules apply — use /show cropbiomes or /plot info")
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    static final class VolumeState {
        private Double previous;

        double pause(double current) {
            if (previous == null) {
                previous = current;
            }
            return 0.0;
        }

        double resume(double current) {
            if (previous == null) {
                return current;
            }
            double restored = previous;
            previous = null;
            return restored;
        }
    }

    @EventBusSubscriber(modid = FarmUpCraftNeoForge.MOD_ID, value = Dist.CLIENT)
    public static final class ScreenEvents {
        private static final VolumeState VOLUME = new VolumeState();

        private ScreenEvents() {
        }

        @SubscribeEvent
        public static void onScreenOpen(ScreenEvent.Opening event) {
            if (!(event.getNewScreen() instanceof PauseScreen)) {
                return;
            }
            var minecraft = Minecraft.getInstance();
            var weather = minecraft.options.getSoundSourceOptionInstance(SoundSource.WEATHER);
            weather.set(VOLUME.pause(weather.get()));
            minecraft.options.save();
        }

        @SubscribeEvent
        public static void onScreenClose(ScreenEvent.Closing event) {
            if (!(event.getScreen() instanceof PauseScreen)) {
                return;
            }
            var minecraft = Minecraft.getInstance();
            var weather = minecraft.options.getSoundSourceOptionInstance(SoundSource.WEATHER);
            weather.set(VOLUME.resume(weather.get()));
            minecraft.options.save();
        }
    }
}
