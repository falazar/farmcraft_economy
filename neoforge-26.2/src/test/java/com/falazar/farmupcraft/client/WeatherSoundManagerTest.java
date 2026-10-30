package com.falazar.farmupcraft.client;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(EphemeralTestServerProvider.class)
class WeatherSoundManagerTest {
    @Test
    void taggedCropsReceiveBothHintsWithoutAPlayer(MinecraftServer server) {
        var lines = new ArrayList<Component>();
        WeatherSoundManager.onItemTooltip(new ItemTooltipEvent(new ItemStack(Items.CARROT), null, lines,
                TooltipFlag.NORMAL, Item.TooltipContext.EMPTY, TooltipDisplay.DEFAULT));
        assertEquals(2, lines.size());
        assertEquals("Requires: farm plot", lines.getFirst().getString());
        assertEquals(Component.empty().withStyle(ChatFormatting.DARK_GREEN).getStyle(), lines.getFirst().getStyle());
        assertEquals("Biome rules apply — use /show cropbiomes or /plot info", lines.getLast().getString());
    }

    @Test
    void unrelatedItemsKeepTheirExistingTooltip(MinecraftServer server) {
        var lines = new ArrayList<Component>();
        lines.add(Component.literal("Existing tooltip"));
        WeatherSoundManager.onItemTooltip(new ItemTooltipEvent(new ItemStack(Items.STONE), null, lines,
                TooltipFlag.NORMAL, Item.TooltipContext.EMPTY, TooltipDisplay.DEFAULT));
        assertEquals(1, lines.size());
        assertEquals("Existing tooltip", lines.getFirst().getString());
    }

    @Test
    void repeatedPausePreservesOriginalDoublePrecisionAndResetsAfterClosing() {
        var volume = new WeatherSoundManager.VolumeState();
        double original = 0.3333333333333333;
        assertEquals(0.0, volume.pause(original));
        assertEquals(0.0, volume.pause(0.0));
        assertEquals(original, volume.resume(0.0));
        assertEquals(0.7, volume.resume(0.7));
        assertEquals(0.0, volume.pause(0.2));
        assertEquals(0.2, volume.resume(0.0));
    }

    @Test
    void zeroWeatherVolumeRemainsMutedAfterClosing() {
        var volume = new WeatherSoundManager.VolumeState();
        assertEquals(0.0, volume.pause(0.0));
        assertEquals(0.0, volume.resume(0.0));
    }
}
