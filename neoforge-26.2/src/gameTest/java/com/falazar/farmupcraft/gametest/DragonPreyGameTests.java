package com.falazar.farmupcraft.gametest;

import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

@EventBusSubscriber(modid = FarmUpCraftNeoForge.MOD_ID)
public final class DragonPreyGameTests {
    @SubscribeEvent
    public static void registerFunctions(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, functions -> functions.register(
                FarmUpCraftNeoForge.prefix("dragon_prey_bite"), DragonPreyGameTests::bite));
    }

    private static void bite(GameTestHelper helper) {
        var id = Identifier.parse("iceandfire:fire_dragon");
        helper.assertTrue(BuiltInRegistries.ENTITY_TYPE.containsKey(id), "Run with the installed Ice and Fire");
        Entity dragon = BuiltInRegistries.ENTITY_TYPE.getValue(id).create(helper.getLevel(), EntitySpawnReason.TRIGGERED);
        var prey = EntityTypes.COW.create(helper.getLevel(), EntitySpawnReason.TRIGGERED);
        helper.assertTrue(dragon != null && prey != null, "Create dragon and prey");
        try {
            Class<?> base = Class.forName("com.iafenvoy.iceandfire.entity.DragonBaseEntity");
            boolean injected = java.util.Arrays.stream(base.getDeclaredMethods())
                    .anyMatch(method -> method.getName().contains("farmupcraft$serverOnlyPreyBite"));
            helper.assertTrue(injected, "Mixin is actually applied to the installed dragon class");
            Object animation = base.getField("ANIMATION_SHAKEPREY").get(null);
            base.getMethod("setAnimation", animation.getClass()).invoke(dragon, animation);
            var tick = base.getMethod("setAnimationTick", int.class);
            var update = base.getDeclaredMethod("updatePreyInMouth", Entity.class);
            update.setAccessible(true);
            prey.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
            prey.setHealth(1000);
            base.getMethod("setHunger", int.class).invoke(dragon, 20);
            helper.assertTrue(prey.startRiding(dragon, true, true), "Prey is held by the dragon");

            tick.invoke(dragon, 30);
            update.invoke(dragon, prey);
            helper.assertValueEqual(prey.getHealth(), 1000.0F, "Earlier animation does not bite");
            helper.assertTrue(prey.isPassenger(), "Earlier animation keeps prey held");

            tick.invoke(dragon, 56);
            update.invoke(dragon, prey);
            helper.assertTrue(prey.getHealth() < 1000, "Server bite damage still applies");
            helper.assertTrue(!prey.isPassenger(), "Server bite still releases the passenger");
            helper.assertValueEqual(base.getMethod("getHunger").invoke(dragon), 21, "Server hunger gain is preserved");
            helper.succeed();
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Installed Ice and Fire dragon-prey regression failed", error);
        } finally {
            prey.discard();
            dragon.discard();
        }
    }
}
