package com.falazar.farmupcraft.gametest;

import com.falazar.farmupcraft.GemContent;
import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

@EventBusSubscriber(modid = FarmUpCraftNeoForge.MOD_ID)
public final class GemContentGameTests {
    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, functions -> functions.register(
                FarmUpCraftNeoForge.prefix("gem_content"), GemContentGameTests::verify));
    }

    private static void verify(GameTestHelper helper) {
        try (var f = new ResourcePlotFixture(helper)) {
            f.player.experienceLevel = 100;
            var bounds = new AABB(f.pos).inflate(2);
            var oldXp = f.level.getEntitiesOfClass(ExperienceOrb.class, bounds).stream()
                    .map(ExperienceOrb::getUUID).collect(java.util.stream.Collectors.toSet());
            try {
                for (String gem : GemContent.TYPES) {
                    helper.assertValueEqual(BuiltInRegistries.ITEM.getValue(
                            Identifier.fromNamespaceAndPath("farmupcraft", gem)), GemContent.gem(gem),
                            "Registered gem identity");
                    for (boolean deepslate : new boolean[]{false, true}) {
                        var ore = GemContent.ore(gem, deepslate);
                        var state = ore.defaultBlockState();
                        helper.assertTrue(ore != Blocks.AIR && ore.asItem() != Items.AIR, "Ore and block item exist");
                        helper.assertTrue(state.is(BlockTags.MINEABLE_WITH_PICKAXE)
                                && state.is(BlockTags.NEEDS_DIAMOND_TOOL), "Diamond-tier pickaxe tags loaded");
                        f.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
                        helper.assertTrue(!f.player.hasCorrectToolForDrops(state), "Iron cannot harvest gem ore");
                        f.level.setBlock(f.pos, state, 2);
                        helper.assertTrue(f.player.gameMode.destroyBlock(f.pos), "Wrong-tier mining still breaks ore");
                        helper.assertTrue(f.newDrops().isEmpty(), "Iron gets no gem");
                        f.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
                        helper.assertTrue(f.player.hasCorrectToolForDrops(state), "Diamond can harvest gem ore");
                        f.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.NETHERITE_PICKAXE));
                        helper.assertTrue(f.player.hasCorrectToolForDrops(state), "Netherite can harvest gem ore");
                        f.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
                        f.level.setBlock(f.pos, state, 2);
                        helper.assertTrue(f.player.gameMode.destroyBlock(f.pos), "Real survival gem mining");
                        helper.assertValueEqual(f.dropCount(GemContent.gem(gem)), 1, "Exactly one base gem");
                        int xp = f.level.getEntitiesOfClass(ExperienceOrb.class, bounds,
                                orb -> !oldXp.contains(orb.getUUID())).stream().mapToInt(ExperienceOrb::getValue).sum();
                        helper.assertTrue(xp >= 1 && xp <= 5, "Real mining produces redstone-like 1-5 XP");
                        f.level.getEntitiesOfClass(ExperienceOrb.class, bounds,
                                orb -> !oldXp.contains(orb.getUUID())).forEach(ExperienceOrb::discard);
                        f.newDrops().forEach(net.minecraft.world.entity.item.ItemEntity::discard);
                        var silk = new ItemStack(Items.DIAMOND_PICKAXE);
                        silk.enchant(f.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                                .getOrThrow(Enchantments.SILK_TOUCH), 1);
                        f.player.setItemInHand(InteractionHand.MAIN_HAND, silk);
                        f.level.setBlock(f.pos, state, 2);
                        helper.assertTrue(f.player.gameMode.destroyBlock(f.pos), "Silk Touch mining succeeds");
                        helper.assertValueEqual(f.dropCount(ore.asItem()), 1, "Silk Touch gives matching ore variant");
                        helper.assertValueEqual(f.dropCount(GemContent.gem(gem)), 0, "Silk Touch gives no gem");
                        helper.assertTrue(f.level.getEntitiesOfClass(ExperienceOrb.class, bounds,
                                orb -> !oldXp.contains(orb.getUUID())).isEmpty(), "Silk Touch gives no XP");
                        f.newDrops().forEach(net.minecraft.world.entity.item.ItemEntity::discard);
                        var fortune = new ItemStack(Items.DIAMOND_PICKAXE);
                        fortune.enchant(f.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                                .getOrThrow(Enchantments.FORTUNE), 3);
                        f.player.setItemInHand(InteractionHand.MAIN_HAND, fortune);
                        int largestDrop = 0;
                        for (int attempt = 0; attempt < 20; attempt++) {
                            f.level.setBlock(f.pos, state, 2);
                            f.level.getRandom().setSeed(1234L + attempt);
                            helper.assertTrue(f.player.gameMode.destroyBlock(f.pos), "Fortune mining succeeds");
                            int count = f.dropCount(GemContent.gem(gem));
                            helper.assertTrue(count >= 1 && count <= 4, "Diamond-style Fortune III bounds");
                            largestDrop = Math.max(count, largestDrop);
                            f.newDrops().forEach(net.minecraft.world.entity.item.ItemEntity::discard);
                            f.level.getEntitiesOfClass(ExperienceOrb.class, bounds,
                                    orb -> !oldXp.contains(orb.getUUID())).forEach(ExperienceOrb::discard);
                        }
                        helper.assertTrue(largestDrop > 1, "Fortune actually increases drops");
                    }
                }
            } finally {
                f.level.getEntitiesOfClass(ExperienceOrb.class, bounds,
                        orb -> !oldXp.contains(orb.getUUID())).forEach(ExperienceOrb::discard);
            }
        }
        helper.succeed();
    }
}
