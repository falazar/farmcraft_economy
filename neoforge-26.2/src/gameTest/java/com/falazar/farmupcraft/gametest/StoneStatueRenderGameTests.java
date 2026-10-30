package com.falazar.farmupcraft.gametest;

import com.falazar.farmupcraft.client.StoneStatueRenderCompat;
import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

@EventBusSubscriber(modid = FarmUpCraftNeoForge.MOD_ID)
public final class StoneStatueRenderGameTests {
    @SubscribeEvent
    public static void registerFunctions(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, functions -> functions.register(
                FarmUpCraftNeoForge.prefix("stone_statue_model_id"), StoneStatueRenderGameTests::modelId));
    }

    private static void modelId(GameTestHelper helper) {
        Entity first = zombie(helper);
        Entity second = zombie(helper);
        Entity cachedModel = zombie(helper);
        // Server-side creation allocates IDs; client-only hollow models retain the zero sentinel.
        cachedModel.setId(0);
        StoneStatueRenderCompat.assignModelId(first, null);
        boolean unassignedIdRejected = false;
        try {
            cachedModel.getId();
        } catch (IllegalStateException expected) {
            unassignedIdRejected = true;
        }
        helper.assertTrue(unassignedIdRejected, "Unspawned zombie reproduces the unassigned-ID failure");
        first.setId(1234);
        second.setId(5678);

        StoneStatueRenderCompat.assignModelId(first, cachedModel);
        helper.assertValueEqual(cachedModel.getId(), 1234, "Model uses its owning statue's ID");
        StoneStatueRenderCompat.assignModelId(second, cachedModel);
        helper.assertValueEqual(cachedModel.getId(), 5678, "Shared cached model updates for the next statue");
        helper.assertValueEqual(first.getId(), 1234, "First owner ID is unchanged");
        helper.assertValueEqual(second.getId(), 5678, "Second owner ID is unchanged");
        helper.assertTrue(helper.getLevel().getEntity(cachedModel.getUUID()) == null,
                "Render-only model is not registered in the world");
        helper.succeed();
    }

    private static Entity zombie(GameTestHelper helper) {
        Entity entity = EntityTypes.ZOMBIE.create(helper.getLevel(), EntitySpawnReason.TRIGGERED);
        helper.assertTrue(entity != null, "Zombie model can be created");
        return entity;
    }
}
