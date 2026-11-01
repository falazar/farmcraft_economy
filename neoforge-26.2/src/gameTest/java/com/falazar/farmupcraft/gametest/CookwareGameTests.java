package com.falazar.farmupcraft.gametest;

import com.falazar.farmupcraft.CookwareManager;
import com.falazar.farmupcraft.data.ChunkData;
import com.falazar.farmupcraft.events.ModEvents;
import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

import java.util.List;
import java.util.UUID;

@EventBusSubscriber(modid = FarmUpCraftNeoForge.MOD_ID)
public final class CookwareGameTests {
    private CookwareGameTests() {
    }

    @SubscribeEvent
    public static void registerFunctions(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, functions -> {
            functions.register(FarmUpCraftNeoForge.prefix("cookware_menu_exhaustion"),
                    helper -> crafting(helper, false));
            functions.register(FarmUpCraftNeoForge.prefix("cookware_menu_kitchen_budget"),
                    helper -> crafting(helper, true));
        });
    }

    private static void crafting(GameTestHelper helper, boolean kitchen) {
        var level = helper.getLevel();
        BlockPos table = helper.absolutePos(new BlockPos(1, 1, 1));
        level.setBlock(table, Blocks.CRAFTING_TABLE.defaultBlockState(), 2);
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "cookware-test"));
        player.setPos(table.getX() + 0.5, table.getY() + 1, table.getZ() + 0.5);
        var plots = ModEvents.getChunkDataDatabase(level);
        long key = ChunkPos.pack(player.blockPosition());
        var previous = plots.getData(key);
        if (kitchen) {
            plots.putData(key, new ChunkData("kitchen", -1, new UUID(0, 0)));
        } else {
            plots.removeDataBatch(List.of(key));
        }
        try {
            var menu = new CraftingMenu(1, player.getInventory(), ContainerLevelAccess.create(level, table));
            var tool = new ItemStack(BuiltInRegistries.ITEM.getValue(
                    net.minecraft.resources.Identifier.fromNamespaceAndPath("pamhc2foodcore", "juiceritem")));
            CustomData.update(DataComponents.CUSTOM_DATA, tool, tag -> {
                tag.putString("acceptance_marker", "preserved");
                if (!kitchen) {
                    tag.putInt("farmupcraft_cookware_uses", 2);
                }
            });
            menu.getSlot(1).set(tool);
            menu.getSlot(2).set(new ItemStack(Items.APPLE, 2));
            menu.getSlot(3).set(new ItemStack(Items.APPLE, 2));
            takeJuice(helper, menu, player);
            var returned = menu.getSlot(1).getItem();
            helper.assertTrue(CookwareManager.isCookware(returned), "The first craft must return its actual tool");
            helper.assertValueEqual(CookwareManager.getRemainingUses(returned), kitchen ? 9999 : 1,
                    "real menu cookware budget");
            helper.assertValueEqual(CookwareManager.getMaxUsesForDisplay(returned), kitchen ? 10000 : 500,
                    "Original capacity must survive recipe remainders");
            helper.assertTrue(CookwareManager.isCapacityEstimated(returned) == !kitchen,
                    "Only ambiguous old tools have estimated capacity");
            helper.assertValueEqual(returned.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag()
                    .getStringOr("acceptance_marker", ""), "preserved", "returned tool custom components");
            takeJuice(helper, menu, player);
            if (kitchen) {
                helper.assertValueEqual(CookwareManager.getRemainingUses(menu.getSlot(1).getItem()), 9998,
                        "kitchen counter survives the second crafting remainder");
                var kitchenTool = menu.getSlot(1).getItem();
                CustomData.update(DataComponents.CUSTOM_DATA, kitchenTool,
                        tag -> tag.putInt("farmupcraft_cookware_uses", 501));
                menu = new CraftingMenu(2, player.getInventory(), ContainerLevelAccess.create(level, table));
                menu.getSlot(1).set(kitchenTool);
                menu.getSlot(2).set(new ItemStack(Items.APPLE, 1));
                menu.getSlot(3).set(new ItemStack(Items.APPLE, 1));
                menu.slotsChanged(menu.getSlot(1).container);
                plots.removeDataBatch(List.of(key));
                takeJuice(helper, menu, player);
                var worn = menu.getSlot(1).getItem();
                helper.assertValueEqual(CookwareManager.getRemainingUses(worn), 500, "Kitchen tool crosses 500");
                helper.assertValueEqual(CookwareManager.getMaxUsesForDisplay(worn), 10000,
                        "Kitchen capacity must not shrink below 501 uses, even outside kitchen");
                helper.assertValueEqual(CookwareManager.getWarningThreshold(worn), 500,
                        "Kitchen warning threshold follows tool capacity");
                helper.assertValueEqual(worn.getBarWidth(), 1, "Kitchen bar shows five percent remaining");
                helper.assertTrue(worn.getTooltipLines(net.minecraft.world.item.Item.TooltipContext.of(level),
                        player, net.minecraft.world.item.TooltipFlag.NORMAL).stream()
                        .anyMatch(line -> line.getString().equals("Uses remaining: 500 / 10000")),
                        "Actual kitchen tooltip must display exact remaining uses and capacity");

                var old = new ItemStack(BuiltInRegistries.ITEM.getValue(
                        net.minecraft.resources.Identifier.parse("pamhc2foodcore:juiceritem")));
                CustomData.update(DataComponents.CUSTOM_DATA, old, tag -> tag.putInt("farmupcraft_cookware_uses", 457));
                menu = new CraftingMenu(3, player.getInventory(), ContainerLevelAccess.create(level, table));
                menu.getSlot(1).set(old);
                menu.getSlot(2).set(new ItemStack(Items.APPLE, 1));
                menu.getSlot(3).set(new ItemStack(Items.APPLE, 1));
                menu.slotsChanged(menu.getSlot(1).container);
                plots.putData(key, new ChunkData("kitchen", -1, new UUID(0, 0)));
                takeJuice(helper, menu, player);
                var migrated = menu.getSlot(1).getItem();
                helper.assertValueEqual(CookwareManager.getRemainingUses(migrated), 456, "Old uses must not reset");
                helper.assertValueEqual(CookwareManager.getMaxUsesForDisplay(migrated), 500, "Old capacity estimate");
                helper.assertValueEqual(CookwareManager.getWarningThreshold(migrated), 50,
                        "Using ordinary cookware in kitchen must not trigger the kitchen threshold");
                helper.assertTrue(CookwareManager.isCapacityEstimated(migrated), "Estimate label survives migration");
                helper.assertTrue(migrated.getTooltipLines(net.minecraft.world.item.Item.TooltipContext.of(level),
                        player, net.minecraft.world.item.TooltipFlag.NORMAL).stream()
                        .anyMatch(line -> line.getString().equals(
                                "Uses remaining: 456 / 500 (legacy capacity estimated)")),
                        "Actual legacy tooltip must display uses and disclose estimated capacity");
            }
            if (!kitchen) {
                helper.assertTrue(menu.getSlot(1).getItem().isEmpty(), "The final use must consume the exhausted tool");
            }
            helper.assertTrue(menu.getSlot(2).getItem().isEmpty() && menu.getSlot(3).getItem().isEmpty(),
                    "Both successful crafts must consume their real recipe ingredients");
        } finally {
            if (previous == null) {
                plots.removeDataBatch(List.of(key));
            } else {
                plots.putData(key, previous);
            }
        }
        helper.succeed();
    }

    private static void takeJuice(GameTestHelper helper, CraftingMenu menu, net.minecraft.world.entity.player.Player player) {
        var result = menu.getSlot(0);
        helper.assertValueEqual(BuiltInRegistries.ITEM.getKey(result.getItem().getItem()).toString(),
                "pamhc2foodcore:applejuiceitem", "actual HarvestCraft recipe output in menu " + menu.containerId);
        var crafted = result.remove(1);
        result.onTake(player, crafted);
        helper.assertValueEqual(crafted.getCount(), 1, "one crafted juice");
    }
}
