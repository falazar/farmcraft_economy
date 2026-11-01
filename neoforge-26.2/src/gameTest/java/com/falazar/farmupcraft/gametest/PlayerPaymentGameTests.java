package com.falazar.farmupcraft.gametest;

import com.falazar.farmupcraft.command.PlayerCommand;
import com.falazar.farmupcraft.data.PlayerData;
import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.permissions.PermissionSet;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.UUID;

@EventBusSubscriber(modid = FarmUpCraftNeoForge.MOD_ID)
public final class PlayerPaymentGameTests {
    private PlayerPaymentGameTests() {}

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, functions -> functions.register(
                FarmUpCraftNeoForge.prefix("player_payment"), PlayerPaymentGameTests::verify));
    }

    private static void verify(GameTestHelper helper) {
        var from = new PlayerData(1, UUID.randomUUID(), null, 100);
        var to = new PlayerData(2, UUID.randomUUID(), null, 20);
        PlayerCommand.transferBalances(from, to, 30);
        helper.assertValueEqual(from.getCoins(), 70, "Sender must pay");
        helper.assertValueEqual(to.getCoins(), 50, "Recipient must receive");
        for (int amount : new int[]{0, -1, 71}) {
            try {
                PlayerCommand.transferBalances(from, to, amount);
                helper.fail("Invalid payment accepted: " + amount);
            } catch (IllegalArgumentException expected) {
                helper.assertValueEqual(from.getCoins(), 70, "Rejected payment must not debit");
                helper.assertValueEqual(to.getCoins(), 50, "Rejected payment must not credit");
            }
        }
        to.setCoins(Integer.MAX_VALUE);
        try {
            PlayerCommand.transferBalances(from, to, 1);
            helper.fail("Overflow payment accepted");
        } catch (ArithmeticException expected) {
            helper.assertValueEqual(from.getCoins(), 70, "Overflow must not debit sender");
        }
        try {
            PlayerCommand.transferBalances(from, from, 1);
            helper.fail("Self-payment accepted");
        } catch (IllegalArgumentException expected) {
            helper.assertValueEqual(from.getCoins(), 70, "Self-payment must not mutate wallet");
        }
        var dispatcher = new CommandDispatcher<CommandSourceStack>();
        PlayerCommand.register(dispatcher);
        var ordinary = helper.getLevel().getServer().createCommandSourceStack()
                .withPermission(PermissionSet.NO_PERMISSIONS);
        var root = dispatcher.getRoot().getChild("player");
        helper.assertTrue(root.getChild("givecoins").canUse(ordinary), "Payments available to ordinary players");
        helper.assertFalse(root.getChild("admin").canUse(ordinary), "Admin commands denied to ordinary players");
        helper.succeed();
    }
}
