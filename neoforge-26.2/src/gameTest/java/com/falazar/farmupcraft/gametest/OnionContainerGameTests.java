package com.falazar.farmupcraft.gametest;

import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.List;
import java.util.function.Supplier;

@EventBusSubscriber(modid = FarmUpCraftNeoForge.MOD_ID)
public final class OnionContainerGameTests {
    private OnionContainerGameTests() {}

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, functions -> functions.register(
                FarmUpCraftNeoForge.prefix("onion_container"), OnionContainerGameTests::verify));
    }

    private static void verify(GameTestHelper helper) {
        if (!net.neoforged.fml.ModList.get().isLoaded("solonion")) {
            throw new IllegalStateException("onion_container requires the real Onion and CreativeCore mods");
        }
        try {
            var items = Class.forName("team.creative.solonion.common.item.SOLOnionItems");
            var menuType = Class.forName("team.creative.solonion.common.item.foodcontainer.FoodContainer");
            var constructor = menuType.getConstructor(int.class, Inventory.class, Player.class);
            for (String name : new String[]{"LUNCHBAG", "LUNCHBOX", "GOLDEN_LUNCHBOX"}) {
                var supplier = (Supplier<?>) items.getField(name).get(null);
                var item = (Item) supplier.get();
                for (int slot : new int[]{0, 8, 17, Inventory.SLOT_OFFHAND}) {
                    for (int menuId : new int[]{1, 40, 98, 99, 100}) {
                        var player = helper.makeMockPlayer(GameType.SURVIVAL);
                        player.getInventory().setSelectedSlot(slot < 9 ? slot : 0);
                        var bag = new ItemStack(item);
                        bag.set(DataComponents.CONTAINER,
                                ItemContainerContents.fromItems(List.of(new ItemStack(Items.BREAD, 3))));
                        player.getInventory().setItem(slot, bag);
                        var decoy = new ItemStack(item);
                        // A valid menu ID must not select another bag at that inventory index.
                        if (menuId < 36 && menuId != slot && slot != 17) {
                            player.getInventory().setItem(menuId, decoy);
                        }
                        var menu = (AbstractContainerMenu) constructor.newInstance(
                                menuId, player.getInventory(), player);
                        helper.assertTrue(menu.containerId == menuId, "Menu ID must remain unchanged");
                        helper.assertTrue(menuType.getField("containerItem").get(menu) == bag,
                                "Menu must use the intended bag at slot " + slot);
                        helper.assertTrue(menu.getSlot(0).getItem().is(Items.BREAD)
                                && menu.getSlot(0).getItem().getCount() == 3, "Stored food must load");
                        menu.getSlot(0).set(new ItemStack(Items.CARROT, 2));
                        menu.broadcastChanges();
                        helper.assertTrue(bag.get(DataComponents.CONTAINER).getStackInSlot(0).is(Items.CARROT),
                                "Food edits must persist to the intended bag");
                        helper.assertTrue(decoy.getOrDefault(DataComponents.CONTAINER,
                                ItemContainerContents.EMPTY).equals(ItemContainerContents.EMPTY),
                                "Another bag must not be modified");
                        var reopened = (AbstractContainerMenu) constructor.newInstance(
                                menuId, player.getInventory(), player);
                        helper.assertTrue(reopened.getSlot(0).getItem().is(Items.CARROT)
                                && reopened.getSlot(0).getItem().getCount() == 2,
                                "Food edits must survive reopening");
                    }
                }
            }
            helper.succeed();
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot verify installed Onion lunch containers", error);
        }
    }
}
