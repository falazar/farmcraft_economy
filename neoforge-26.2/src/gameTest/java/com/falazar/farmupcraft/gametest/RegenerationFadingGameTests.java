package com.falazar.farmupcraft.gametest;

import com.falazar.farmupcraft.CropsManager;
import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.UUID;
import java.util.function.Consumer;

@EventBusSubscriber(modid = FarmUpCraftNeoForge.MOD_ID)
public final class RegenerationFadingGameTests {
    private RegenerationFadingGameTests() {}

    @SubscribeEvent
    public static void registerFunctions(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, functions -> functions.register(
                FarmUpCraftNeoForge.prefix("regeneration_fading"), RegenerationFadingGameTests::verify));
    }

    private static void verify(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "regen-fade-test"));
        var other = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "regen-other-test"));
        Consumer<MobEffectEvent.Remove> protect = event -> {
            if (event.getEntity() == player || event.getEntity() == other) event.setCanceled(true);
        };
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, true, protect);
        try {
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, -1));
            player.addEffect(new MobEffectInstance(MobEffects.SPEED, -1));
            other.addEffect(new MobEffectInstance(MobEffects.REGENERATION, -1));
            helper.assertFalse(player.removeEffect(MobEffects.REGENERATION),
                    "Ordinary removal must respect earned-effect protection");
            helper.assertTrue(CropsManager.fadeRegeneration(player),
                    "Random fade must remove protected infinite regeneration");
            helper.assertFalse(player.hasEffect(MobEffects.REGENERATION),
                    "Regeneration must actually disappear, not just announce removal");
            helper.assertTrue(player.hasEffect(MobEffects.SPEED), "Other benefits must remain");
            helper.assertFalse(other.removeEffect(MobEffects.REGENERATION),
                    "Other players must keep effect protection");
            helper.assertFalse(player.removeEffect(MobEffects.SPEED),
                    "Protection must remain after the scoped fade");
            helper.assertFalse(CropsManager.fadeRegeneration(player),
                    "An already absent effect must not fade twice");
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200));
            helper.assertFalse(player.removeEffect(MobEffects.REGENERATION),
                    "Protection must be restored even after an absent-effect fade");
            helper.assertTrue(CropsManager.fadeRegeneration(player), "Finite regeneration must also fade");

            var recipes = level.getServer().getRecipeManager();
            helper.assertTrue(recipes.byKey(ResourceKey.create(Registries.RECIPE,
                    Identifier.parse("minecraft:bundle"))).isEmpty(), "Uncolored bundle recipe must be absent");
            helper.assertTrue(recipes.byKey(ResourceKey.create(Registries.RECIPE,
                    Identifier.parse("minecraft:red_bundle"))).isPresent(), "Colored bundle recipes must remain");
            helper.succeed();
        } finally {
            NeoForge.EVENT_BUS.unregister(protect);
            player.removeAllEffects();
            other.removeAllEffects();
            player.discard();
            other.discard();
        }
    }
}
