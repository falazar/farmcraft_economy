package com.falazar.farmupcraft.gametest;

import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import com.falazar.farmupcraft.util.CropListChat;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.List;
import java.util.Optional;

@EventBusSubscriber(modid = FarmUpCraftNeoForge.MOD_ID)
public final class CropListColorGameTests {
    @SubscribeEvent
    public static void registerFunctions(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, functions -> functions.register(
                FarmUpCraftNeoForge.prefix("crop_list_colors"), CropListColorGameTests::colors));
    }

    private static void colors(GameTestHelper helper) {
        try (var fixture = new ResourcePlotFixture(helper)) {
            var dispatcher = new com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack>();
            com.falazar.farmupcraft.command.ShowBiomesCommand.register(dispatcher);
            helper.assertTrue(dispatcher.getRoot().getChild("show").getChild("plotcrops").getCommand() != null,
                    "Plot crop command is registered");
            helper.assertValueEqual(com.falazar.farmupcraft.command.ShowBiomesCommand.showPlotCrops(
                    fixture.player.createCommandSourceStack()), 1, "Plot biome sampling and combined list succeed");
            helper.assertValueEqual(com.falazar.farmupcraft.command.ShowBiomesCommand.showPlotCrops(
                    fixture.level.getServer().createCommandSourceStack()), 0, "Console is rejected explicitly");
            for (String crop : List.of("onion", "mustardseeds", "sesameseeds")) {
                fixture.player.getInventory().clearContent();
                Item produce = item(crop + "item");
                Item seed = item(crop + "seeditem");
                helper.assertTrue(produce != Items.AIR && seed != Items.AIR, "Real crop/seed pair exists");
                var allowed = List.of(produce, seed);
                fixture.player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                fixture.player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
                assertColors(helper, CropListChat.crops(allowed, fixture.player), ChatFormatting.YELLOW);
                fixture.player.getInventory().setItem(9, new ItemStack(seed));
                assertColors(helper, CropListChat.crops(allowed, fixture.player), ChatFormatting.GREEN);
                fixture.player.getInventory().setItem(9, ItemStack.EMPTY);
                fixture.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(seed));
                assertColors(helper, CropListChat.crops(allowed, fixture.player), ChatFormatting.GREEN);
                fixture.player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                fixture.player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(seed));
                assertColors(helper, CropListChat.crops(allowed, fixture.player), ChatFormatting.GREEN);
            }
            fixture.player.getInventory().clearContent();
            fixture.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WHEAT_SEEDS));
            assertColors(helper, CropListChat.crops(List.of(Items.WHEAT, Items.WHEAT_SEEDS), fixture.player),
                    ChatFormatting.GREEN);
            assertColors(helper, CropListChat.crops(List.of(Items.CARROT), fixture.player), ChatFormatting.YELLOW);
            assertColors(helper, CropListChat.crops(List.of(Items.CARROT), null), ChatFormatting.YELLOW);
        }
        helper.succeed();
    }

    private static Item item(String path) {
        return BuiltInRegistries.ITEM.getValue(Identifier.parse("pamhc2crops:" + path));
    }

    private static void assertColors(GameTestHelper helper, Component text, ChatFormatting expected) {
        helper.assertTrue(!text.getString().equals("None"), "Crop remains visible");
        text.visit((style, part) -> {
            if (!part.isBlank() && !part.equals(", ")) {
                helper.assertTrue(style.getColor() != null
                        && style.getColor().equals(Style.EMPTY.applyFormat(expected).getColor()),
                        "Expected crop color " + expected);
            }
            return Optional.empty();
        }, Style.EMPTY);
    }
}
