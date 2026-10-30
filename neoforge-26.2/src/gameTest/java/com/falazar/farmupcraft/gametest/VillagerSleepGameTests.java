package com.falazar.farmupcraft.gametest;

import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

@EventBusSubscriber(modid = FarmUpCraftNeoForge.MOD_ID)
public final class VillagerSleepGameTests {
    private VillagerSleepGameTests() {}

    @SubscribeEvent
    public static void registerFunctions(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, functions -> functions.register(
                FarmUpCraftNeoForge.prefix("villager_sleep_healing"), VillagerSleepGameTests::sleepHealing));
    }

    private static void sleepHealing(GameTestHelper helper) {
        try (var fixture = new ResourcePlotFixture(helper)) {
            var level = fixture.level;
            var bed = fixture.pos;
            var previous = level.getBlockState(bed);
            Villager villager = EntityTypes.VILLAGER.create(level, EntitySpawnReason.COMMAND);
            if (villager == null) throw new IllegalStateException("Could not create test villager");
            try {
                level.setBlock(bed, Blocks.BED.red().defaultBlockState(), 2);
                villager.setPos(bed.getX(), bed.getY(), bed.getZ());
                villager.setHealth(3);
                helper.assertValueEqual(villager.getHealth(), 3F, "Awake villagers must not auto-heal");
                villager.startSleeping(bed);
                helper.assertTrue(villager.isSleeping(), "Villager must actually enter sleep");
                helper.assertValueEqual(villager.getHealth(), villager.getMaxHealth(),
                        "Entering bed must heal immediately, without a tick delay");
                villager.setHealth(7);
                helper.assertValueEqual(villager.getHealth(), 7F, "Being asleep must not grant damage immunity");
                villager.stopSleeping();
                helper.assertValueEqual(villager.getHealth(), 7F, "Waking must not heal");
                var maxHealth = villager.getAttribute(Attributes.MAX_HEALTH);
                if (maxHealth == null) throw new IllegalStateException("Villager has no maximum health attribute");
                maxHealth.setBaseValue(60);
                villager.startSleeping(bed);
                helper.assertValueEqual(villager.getHealth(), 60F, "Boosted villagers must heal to their full maximum");
                villager.stopSleeping();
                villager.setHealth(5);
                villager.setAge(-24000);
                villager.startSleeping(bed);
                helper.assertValueEqual(villager.getHealth(), 60F, "Baby villagers must also heal");
                villager.stopSleeping();

                fixture.player.setHealth(5);
                fixture.player.startSleeping(bed);
                helper.assertValueEqual(fixture.player.getHealth(), 5F, "Player sleeping must not receive villager healing");
                fixture.player.stopSleeping();
            } finally {
                villager.stopSleeping();
                villager.discard();
                fixture.player.stopSleeping();
                level.setBlock(bed, previous, 2);
            }
        }
        helper.succeed();
    }
}
