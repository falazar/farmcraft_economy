package com.falazar.farmupcraft.gametest;

import com.falazar.farmupcraft.data.PlayerData;
import com.falazar.farmupcraft.data.VillageData;
import com.falazar.farmupcraft.enchantment.EnchantmentPayment;
import com.falazar.farmupcraft.events.ModEvents;
import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.List;
import java.util.UUID;

@EventBusSubscriber(modid = FarmUpCraftNeoForge.MOD_ID)
public final class EnchantmentGameTests {
    private EnchantmentGameTests() {
    }

    @SubscribeEvent
    public static void registerFunctions(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, functions -> {
            functions.register(id("enchantment_payment"), helper -> payment(helper, 137, false));
            functions.register(id("enchantment_insufficient_coins"), helper -> payment(helper, 99, false));
            functions.register(id("enchantment_creative"), helper -> payment(helper, 0, true));
            functions.register(id("enchantment_invalid_requests"), EnchantmentGameTests::invalidRequests);
            functions.register(id("enchantment_village_surcharge"), helper -> villagePayment(helper, false));
            functions.register(id("enchantment_village_debt"), helper -> villagePayment(helper, true));
            functions.register(id("enchantment_reservation_refund"), EnchantmentGameTests::reservationRefund);
        });
    }

    private static Identifier id(String path) {
        return FarmUpCraftNeoForge.prefix(path);
    }

    private static EnchantmentMenu menu(GameTestHelper helper, net.minecraft.world.entity.player.Player player) {
        BlockPos table = helper.absolutePos(new BlockPos(1, 1, 1));
        helper.getLevel().setBlock(table, Blocks.ENCHANTING_TABLE.defaultBlockState(), 2);
        return new EnchantmentMenu(1, player.getInventory(), ContainerLevelAccess.create(helper.getLevel(), table));
    }

    private static void payment(GameTestHelper helper, int coins, boolean creative) {
        var player = helper.makeMockPlayer(creative ? GameType.CREATIVE : GameType.SURVIVAL);
        player.getAbilities().instabuild = creative;
        player.experienceLevel = 30;
        var database = ModEvents.getPlayerDatabase(helper.getLevel());
        database.putData(player.getUUID(), new PlayerData(-1, player.getUUID(), UUID.randomUUID(), coins));
        try {
            var menu = menu(helper, player);
            menu.getSlot(0).set(new ItemStack(Items.DIAMOND_PICKAXE));
            menu.getSlot(1).set(new ItemStack(Items.LAPIS_LAZULI, 3));
            menu.costs[1] = 30;
            boolean result = menu.clickMenuButton(player, 1);
            boolean expected = creative || coins >= 100;
            helper.assertValueEqual(result, expected, "enchantment result");
            helper.assertValueEqual(menu.getSlot(0).getItem().isEnchanted(), expected, "actual applied enchantments");
            helper.assertValueEqual(database.getData(player.getUUID()).getCoins(),
                    expected && !creative ? coins - 100 : coins, "exactly one coin payment");
            helper.assertValueEqual(menu.getSlot(1).getItem().getCount(),
                    expected && !creative ? 1 : 3, "lapis consumption");
            helper.assertValueEqual(player.experienceLevel, expected ? 28 : 30, "vanilla XP consumption");
        } finally {
            database.removeDataBatch(List.of(player.getUUID()));
        }
        helper.succeed();
    }

    private static void invalidRequests(GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        var database = ModEvents.getPlayerDatabase(helper.getLevel());
        database.putData(player.getUUID(), new PlayerData(-1, player.getUUID(), UUID.randomUUID(), 137));
        try {
            var menu = menu(helper, player);
            helper.assertTrue(!menu.clickMenuButton(player, -1), "Negative buttons must fail");
            helper.assertTrue(!menu.clickMenuButton(player, 3), "Out-of-range buttons must fail");
            menu.getSlot(0).set(new ItemStack(Items.DIAMOND_PICKAXE));
            menu.costs[1] = 30;
            helper.assertTrue(!menu.clickMenuButton(player, 1), "Missing lapis must fail");
            menu.getSlot(1).set(new ItemStack(Items.LAPIS_LAZULI, 3));
            menu.costs[1] = 30;
            helper.assertTrue(!menu.clickMenuButton(player, 1), "Insufficient XP must fail");
            helper.assertValueEqual(database.getData(player.getUUID()).getCoins(), 137, "failed requests do not charge");
            helper.assertTrue(!menu.getSlot(0).getItem().isEnchanted(), "Failed requests do not enchant");
            helper.assertValueEqual(menu.getSlot(1).getItem().getCount(), 3, "Failed requests do not consume lapis");
        } finally {
            database.removeDataBatch(List.of(player.getUUID()));
        }
        helper.succeed();
    }

    private static void villagePayment(GameTestHelper helper, boolean debt) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.experienceLevel = 30;
        UUID villageId = UUID.randomUUID();
        var players = ModEvents.getPlayerDatabase(helper.getLevel());
        var villages = ModEvents.getVillageDatabase(helper.getLevel());
        players.putData(player.getUUID(), new PlayerData(-1, player.getUUID(), villageId, 437));
        villages.putData(villageId, new VillageData(villageId, "Enchantment fixture", new ChunkPos(0, 0),
                2, List.of(), true, debt ? -1 : 100, "", "cow", "", player.getUUID().toString(),
                List.of(player.getUUID())));
        try {
            var menu = menu(helper, player);
            menu.getSlot(0).set(new ItemStack(Items.DIAMOND_PICKAXE));
            menu.getSlot(1).set(new ItemStack(Items.LAPIS_LAZULI, 3));
            menu.costs[1] = 30;
            helper.assertValueEqual(menu.clickMenuButton(player, 1), !debt, "village payment result");
            helper.assertValueEqual(menu.getSlot(0).getItem().isEnchanted(), !debt, "village enchantment applied");
            helper.assertValueEqual(players.getData(player.getUUID()).getCoins(), debt ? 437 : 137,
                    "slot fee plus village-level surcharge");
            helper.assertValueEqual(menu.getSlot(1).getItem().getCount(), debt ? 3 : 1, "village request lapis");
            helper.assertValueEqual(player.experienceLevel, debt ? 30 : 28, "village request XP");
            helper.assertValueEqual(villages.getData(villageId).getCoins(), debt ? -1 : 100,
                    "enchanting does not pay off village debt or change its treasury");
        } finally {
            players.removeDataBatch(List.of(player.getUUID()));
            villages.removeDataBatch(List.of(villageId));
        }
        helper.succeed();
    }

    private static void reservationRefund(GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        var database = ModEvents.getPlayerDatabase(helper.getLevel());
        database.putData(player.getUUID(), new PlayerData(-1, player.getUUID(), UUID.randomUUID(), 137));
        try {
            var payment = new EnchantmentPayment(player, 1);
            try (payment) {
                helper.assertTrue(payment.reserve(), "The real player's balance must cover the reserve");
                helper.assertTrue(payment.reserve(), "A repeated reservation must be idempotent");
                helper.assertValueEqual(database.getData(player.getUUID()).getCoins(), 37, "one reserve only");
            }
            payment.close();
            helper.assertValueEqual(database.getData(player.getUUID()).getCoins(), 137, "one uncommitted refund only");
        } finally {
            database.removeDataBatch(List.of(player.getUUID()));
        }
        helper.succeed();
    }
}
