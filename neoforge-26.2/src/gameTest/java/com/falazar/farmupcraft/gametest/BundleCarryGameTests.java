package com.falazar.farmupcraft.gametest;

import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.ArrayList;
import java.util.UUID;

@EventBusSubscriber(modid = FarmUpCraftNeoForge.MOD_ID)
public final class BundleCarryGameTests {
    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, functions -> functions.register(
                FarmUpCraftNeoForge.prefix("bundle_carry"), BundleCarryGameTests::verify));
    }

    private static void verify(GameTestHelper helper) {
        var level = helper.getLevel();
        var messages = new ArrayList<String>();
        var player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "bundle-test")) {
            @Override
            public void sendSystemMessage(Component text, boolean actionBar) {
                if (actionBar) messages.add(text.getString());
            }
        };
        var originalDifficulty = level.getDifficulty();
        var pos = helper.absolutePos(new BlockPos(1, 2, 1));
        player.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        var drops = new ArrayList<ItemEntity>();
        java.util.function.Function<ItemStack, ItemEntity> pickup = stack -> {
            var drop = new ItemEntity(level, player.getX(), player.getY(), player.getZ(), stack);
            drops.add(drop);
            drop.setNoPickUpDelay();
            drop.playerTouch(player);
            return drop;
        };
        try {
            level.getServer().setDifficulty(Difficulty.HARD, true);
            helper.assertTrue(pickup.apply(new ItemStack(Items.BUNDLE)).isRemoved(),
                    "Hard mode allows the first bundle");
            var second = pickup.apply(new ItemStack(Items.BUNDLE));
            helper.assertTrue(!second.isRemoved(), "Second bundle is not picked up");
            helper.assertValueEqual(second.getItem().getCount(), 1, "Second bundle stays intact on the ground");
            helper.assertValueEqual(messages.size(), 1, "Blocked pickup warns the player");
            helper.assertTrue(messages.getFirst().contains("too heavy"), "Warning explains carry limit");
            second.playerTouch(player);
            helper.assertValueEqual(messages.size(), 1, "Repeated contact does not spam messages");
            player.getInventory().clearContent();
            player.getInventory().setItem(Inventory.SLOT_OFFHAND, new ItemStack(Items.DYED_BUNDLE.pick(DyeColor.BLUE)));
            var colored = pickup.apply(new ItemStack(Items.DYED_BUNDLE.pick(DyeColor.RED)));
            helper.assertTrue(!colored.isRemoved() && colored.getItem().getCount() == 1,
                    "Colored bundle in offhand blocks another color");
            helper.assertTrue(pickup.apply(new ItemStack(Items.APPLE)).isRemoved(),
                    "Other item pickups are unaffected");
            player.getInventory().clearContent();
            player.containerMenu.setCarried(new ItemStack(Items.BUNDLE));
            var cursorBlocked = pickup.apply(new ItemStack(Items.BUNDLE));
            helper.assertTrue(!cursorBlocked.isRemoved() && cursorBlocked.getItem().getCount() == 1,
                    "Bundle on cursor counts as carried");
            player.containerMenu.setCarried(ItemStack.EMPTY);
            for (var difficulty : new Difficulty[]{Difficulty.PEACEFUL, Difficulty.EASY, Difficulty.NORMAL}) {
                level.getServer().setDifficulty(difficulty, true);
                player.getInventory().clearContent();
                player.getInventory().setItem(0, new ItemStack(Items.BUNDLE));
                helper.assertTrue(pickup.apply(new ItemStack(Items.BUNDLE)).isRemoved(),
                        "Other difficulties still allow multiple bundles: " + difficulty);
            }
            level.getServer().setDifficulty(Difficulty.HARD, true);
            player.getInventory().clearContent();
            helper.assertTrue(pickup.apply(new ItemStack(Items.BUNDLE)).isRemoved(),
                    "Dropping the old bundle permits a replacement");
            verifyMenus(helper, player);
        } finally {
            drops.forEach(ItemEntity::discard);
            player.getInventory().clearContent();
            player.containerMenu.setCarried(ItemStack.EMPTY);
            player.containerMenu = player.inventoryMenu;
            player.discard();
            level.getServer().setDifficulty(originalDifficulty, true);
        }
        helper.succeed();
    }

    private static void verifyMenus(GameTestHelper helper, FakePlayer player) {
        var inventory = player.getInventory();
        var chest = new SimpleContainer(27);
        var menu = ChestMenu.threeRows(1, inventory, chest);
        player.containerMenu = menu;
        inventory.clearContent();
        inventory.setItem(0, new ItemStack(Items.BUNDLE));
        chest.setItem(0, new ItemStack(Items.DYED_BUNDLE.pick(DyeColor.RED)));
        for (var input : new ContainerInput[]{ContainerInput.PICKUP, ContainerInput.QUICK_MOVE, ContainerInput.SWAP}) {
            menu.clicked(0, input == ContainerInput.SWAP ? 1 : 0, input, player);
            helper.assertTrue(chest.getItem(0).is(Items.DYED_BUNDLE.pick(DyeColor.RED)),
                    "Second bundle must stay in the chest for " + input);
            helper.assertTrue(menu.getCarried().isEmpty(), "Blocked chest transfer must not put a bundle on cursor");
        }
        helper.assertTrue(menu.quickMoveStack(player, 0).isEmpty(),
                "Direct shift-transfer must also reject the second bundle");
        menu.clicked(0, 0, ContainerInput.SWAP, player);
        helper.assertTrue(inventory.getItem(0).is(Items.DYED_BUNDLE.pick(DyeColor.RED))
                        && chest.getItem(0).is(Items.BUNDLE),
                "Exchanging one owned bundle for another must remain allowed");
        // Hotbar slot zero is last in a chest menu; ordinary owned inventory movement must remain allowed.
        menu.clicked(54, 0, ContainerInput.PICKUP, player);
        helper.assertTrue(!menu.getCarried().isEmpty(), "Owned bundle can be moved onto the cursor");
        menu.clicked(0, 0, ContainerInput.QUICK_MOVE, player);
        helper.assertTrue(chest.getItem(0).is(Items.BUNDLE), "Cursor bundle blocks another shift-transfer");
        menu.setCarried(ItemStack.EMPTY);
        inventory.clearContent();
        menu.clicked(0, 0, ContainerInput.QUICK_MOVE, player);
        helper.assertTrue(chest.getItem(0).isEmpty(), "First bundle can be taken from chest");
        chest.setItem(1, new ItemStack(Items.BUNDLE));
        menu.clicked(1, 0, ContainerInput.QUICK_MOVE, player);
        helper.assertTrue(!chest.getItem(1).isEmpty(), "A repeated shift-click cannot acquire a second bundle");
        chest.setItem(2, new ItemStack(Items.APPLE));
        menu.clicked(2, 0, ContainerInput.QUICK_MOVE, player);
        helper.assertTrue(chest.getItem(2).isEmpty(), "Non-bundle container transfers remain unaffected");

        var crafting = new CraftingMenu(2, inventory,
                net.minecraft.world.inventory.ContainerLevelAccess.create(helper.getLevel(), player.blockPosition()));
        player.containerMenu = crafting;
        crafting.getInputGridSlots().getFirst().set(new ItemStack(Items.BUNDLE));
        crafting.getInputGridSlots().get(1).set(new ItemStack(Items.DYE.pick(DyeColor.RED)));
        crafting.slotsChanged(crafting.getInputGridSlots().getFirst().container);
        helper.assertTrue(crafting.getResultSlot().getItem().is(Items.DYED_BUNDLE.pick(DyeColor.RED)),
                "Actual bundle dye recipe must produce a colored bundle");
        for (var input : new ContainerInput[]{ContainerInput.PICKUP, ContainerInput.QUICK_MOVE}) {
            crafting.clicked(0, 0, input, player);
            helper.assertTrue(crafting.getResultSlot().getItem().is(Items.DYED_BUNDLE.pick(DyeColor.RED)),
                    "Blocked crafting must retain its output for " + input);
            helper.assertTrue(crafting.getInputGridSlots().getFirst().getItem().is(Items.BUNDLE)
                            && crafting.getInputGridSlots().get(1).getItem().is(Items.DYE.pick(DyeColor.RED)),
                    "Blocked crafting must not consume ingredients");
        }
        helper.assertTrue(crafting.quickMoveStack(player, 0).isEmpty(),
                "Direct crafting shift-transfer must reject a second bundle");
        inventory.clearContent();
        crafting.clicked(0, 0, ContainerInput.PICKUP, player);
        helper.assertTrue(crafting.getCarried().is(Items.DYED_BUNDLE.pick(DyeColor.RED)),
                "Recoloring the only owned bundle must remain allowed");
        helper.assertTrue(crafting.getInputGridSlots().getFirst().getItem().isEmpty()
                        && crafting.getInputGridSlots().get(1).getItem().isEmpty(),
                "Successful real crafting consumes its ingredients exactly once");
        crafting.setCarried(ItemStack.EMPTY);
        inventory.setItem(0, new ItemStack(Items.BUNDLE));

        var villager = helper.spawn(EntityTypes.VILLAGER, new BlockPos(2, 2, 2));
        try {
            villager.setTradingPlayer(player);
            var offer = new MerchantOffer(new ItemCost(Items.EMERALD), new ItemStack(Items.BUNDLE), 10, 1, 0);
            var offers = villager.getOffers();
            offers.clear();
            offers.add(offer);
            var trading = new MerchantMenu(3, inventory, villager);
            player.containerMenu = trading;
            trading.getSlot(0).set(new ItemStack(Items.EMERALD, 3));
            trading.slotsChanged(trading.getSlot(0).container);
            helper.assertTrue(trading.getSlot(2).getItem().is(Items.BUNDLE), "Trade must offer a real bundle");
            for (var input : new ContainerInput[]{ContainerInput.PICKUP, ContainerInput.QUICK_MOVE}) {
                trading.clicked(2, 0, input, player);
                helper.assertValueEqual(trading.getSlot(0).getItem().getCount(), 3, "Blocked trade retains payment");
                helper.assertValueEqual(offer.getUses(), 0, "Blocked trade must not consume an offer");
            }
            helper.assertTrue(trading.quickMoveStack(player, 2).isEmpty(), "Direct trade shift-transfer is blocked");
            inventory.clearContent();
            trading.clicked(2, 0, ContainerInput.QUICK_MOVE, player);
            helper.assertValueEqual(offer.getUses(), 1, "Shift-trading must allow exactly one first bundle");
            helper.assertValueEqual(trading.getSlot(0).getItem().getCount(), 2,
                    "Repeated shift-trade stops without charging for a second bundle");
        } finally {
            villager.discard();
            player.containerMenu = player.inventoryMenu;
        }
        for (var difficulty : new Difficulty[]{Difficulty.PEACEFUL, Difficulty.EASY, Difficulty.NORMAL}) {
            helper.getLevel().getServer().setDifficulty(difficulty, true);
            inventory.clearContent();
            inventory.setItem(0, new ItemStack(Items.BUNDLE));
            player.containerMenu = menu;
            chest.setItem(0, new ItemStack(Items.BUNDLE));
            menu.clicked(0, 0, ContainerInput.QUICK_MOVE, player);
            helper.assertTrue(chest.getItem(0).isEmpty(), "Other difficulties permit second chest bundles: " + difficulty);
        }
        helper.getLevel().getServer().setDifficulty(Difficulty.HARD, true);
        player.containerMenu = player.inventoryMenu;
    }
}
