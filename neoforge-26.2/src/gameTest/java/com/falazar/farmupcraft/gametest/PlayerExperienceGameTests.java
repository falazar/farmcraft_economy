package com.falazar.farmupcraft.gametest;

import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.UUID;

@EventBusSubscriber(modid = FarmUpCraftNeoForge.MOD_ID)
public final class PlayerExperienceGameTests {
    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, functions -> functions.register(
                FarmUpCraftNeoForge.prefix("player_experience_bonus"), PlayerExperienceGameTests::verify));
    }

    private static void verify(GameTestHelper helper) {
        for (String name : new String[]{"LazzyMars", "BossPanda96366", "ordinary-player"}) {
            var player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
            try {
                int multiplier = name.equals("ordinary-player") ? 1 : 2;
                player.giveExperiencePoints(50);
                helper.assertValueEqual(player.totalExperience, 50 * multiplier, "Actual XP grant for " + name);
                helper.assertValueEqual(player.experienceLevel, multiplier == 2 ? 7 : 4,
                        "Level crossings are not doubled again");
                player.giveExperiencePoints(-10);
                helper.assertValueEqual(player.totalExperience, 50 * multiplier - 10, "Loss remains unchanged");
                player.giveExperiencePoints(0);
                helper.assertValueEqual(player.totalExperience, 50 * multiplier - 10, "Zero remains unchanged");
                int level = player.experienceLevel;
                player.giveExperienceLevels(-1);
                helper.assertValueEqual(player.experienceLevel, level - 1, "Level costs remain unchanged");
            } finally {
                player.discard();
            }
        }
        helper.succeed();
    }
}
