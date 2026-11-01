package com.falazar.farmupcraft.gametest;

import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

@EventBusSubscriber(modid = FarmUpCraftNeoForge.MOD_ID)
public final class OnionScoringGameTests {
    private OnionScoringGameTests() {}

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, functions -> functions.register(
                FarmUpCraftNeoForge.prefix("onion_scoring"), OnionScoringGameTests::verify));
    }

    private static void verify(GameTestHelper helper) {
        if (!net.neoforged.fml.ModList.get().isLoaded("solonion")) {
            throw new IllegalStateException("onion_scoring requires the real Onion and CreativeCore mods");
        }
        try {
            var type = Class.forName("team.creative.solonion.common.SOLOnionConfig");
            var config = type.getConstructor().newInstance();
            var score = type.getMethod("getDiversity", LivingEntity.class, ItemStack.class);
            var player = helper.makeMockPlayer(GameType.SURVIVAL);
            for (int nutrition : new int[]{0, 1, 5, 8, 10, 20}) {
                for (float modifier : new float[]{0, 0.3F, 0.6F, 1.2F}) {
                    var stack = new ItemStack(Items.BREAD);
                    stack.set(DataComponents.FOOD, new FoodProperties.Builder().nutrition(nutrition)
                            .saturationModifier(modifier).build());
                    double expected = nutrition / 5.0 * (modifier / 0.6);
                    double actual = (double) score.invoke(config, player, stack);
                    helper.assertTrue(Math.abs(actual - expected) < 0.00001,
                            "Legacy score must match at nutrition=" + nutrition + ", modifier=" + modifier
                                    + ": expected=" + expected + ", actual=" + actual);
                    helper.assertTrue(stack.get(DataComponents.FOOD).nutrition() == nutrition,
                            "Scoring must not change actual food properties");
                }
            }
            for (var item : java.util.List.of(Items.GOLDEN_CARROT, Items.GOLDEN_APPLE, Items.ENCHANTED_GOLDEN_APPLE)) {
                double expected = item == Items.ENCHANTED_GOLDEN_APPLE ? 5 : 2;
                helper.assertTrue((double) score.invoke(config, player, new ItemStack(item)) == expected,
                        "Explicit golden-food override must remain");
            }
            var potato = new ItemStack(Items.POISONOUS_POTATO);
            var food = potato.get(DataComponents.FOOD);
            double base = food.saturation() / 6.0;
            var consumable = potato.get(DataComponents.CONSUMABLE);
            double effects = 0;
            for (var effect : consumable.onConsumeEffects()) {
                if (effect instanceof net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect applied) {
                    for (var instance : applied.effects()) {
                        effects += (instance.getAmplifier() + 1) * -0.2 * applied.probability();
                    }
                }
            }
            helper.assertTrue(Math.abs((double) score.invoke(config, player, potato) - (base + effects)) < 0.00001,
                    "Harmful food effect adjustments must not be rescaled");
            var global = Class.forName("team.creative.solonion.common.SOLOnion").getField("CONFIG").get(null);
            int trackCount = type.getField("trackCount").getInt(global);
            var decay = Class.forName("team.creative.solonion.common.food.FoodPlayerDataImpl")
                    .getMethod("calculateDiversity", LivingEntity.class, ItemStack.class, int.class);
            for (int age : new int[]{0, 10, trackCount - 1}) {
                double raw = (double) score.invoke(global, player, new ItemStack(Items.BREAD));
                double expected = raw * (1 - age / (trackCount + 1.0));
                helper.assertTrue(Math.abs((double) decay.invoke(null, player, new ItemStack(Items.BREAD), age)
                        - expected) < 0.00001, "History decay must preserve the legacy multiplier");
            }
            type.getField("disableDiversityCalculations").setBoolean(config, true);
            type.getField("disabledDefaultDiversity").setDouble(config, 3);
            helper.assertTrue((double) score.invoke(config, player, new ItemStack(Items.BREAD)) == 3,
                    "Disabled-diversity mode must remain unchanged");
            helper.succeed();
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot verify installed Onion scoring", error);
        }
    }
}
