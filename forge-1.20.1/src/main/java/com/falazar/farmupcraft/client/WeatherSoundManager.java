package com.falazar.farmupcraft.client;

import com.falazar.farmupcraft.FarmUpCraft;
import com.falazar.farmupcraft.util.FUCTags;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = FarmUpCraft.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class WeatherSoundManager {

    private static float savedWeatherVolume = -1f;

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        var stack = event.getItemStack();
        if (stack.is(FUCTags.MODDED_CROPS) || stack.is(FUCTags.MODDED_SEEDS) || stack.is(FUCTags.VANILLA_CROPS)) {
            event.getToolTip().add(Component.literal("Requires: farm plot").withStyle(ChatFormatting.DARK_GREEN));
            event.getToolTip().add(Component.literal("Biome rules apply — use /show cropbiomes or /plot info")
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    @SubscribeEvent
    public static void onScreenOpen(ScreenEvent.Opening event) {
        if (!(event.getScreen() instanceof PauseScreen))
            return;
        Minecraft mc = Minecraft.getInstance();
        var weatherOption = mc.options.getSoundSourceOptionInstance(SoundSource.WEATHER);
        savedWeatherVolume = (float) (double) weatherOption.get();
        if (savedWeatherVolume > 0f) {
            weatherOption.set(0.0);
            mc.options.save();
        }
    }

    @SubscribeEvent
    public static void onScreenClose(ScreenEvent.Closing event) {
        if (!(event.getScreen() instanceof PauseScreen))
            return;
        if (savedWeatherVolume < 0f)
            return;
        Minecraft mc = Minecraft.getInstance();
        mc.options.getSoundSourceOptionInstance(SoundSource.WEATHER).set((double) savedWeatherVolume);
        mc.options.save();
        savedWeatherVolume = -1f;
    }
}
