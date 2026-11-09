package com.falazar.farmupcraft.gametest;

import com.falazar.farmupcraft.CropsManager;
import com.falazar.farmupcraft.TreeManager;
import com.falazar.farmupcraft.command.MarketCommand;
import com.falazar.farmupcraft.data.ChunkData;
import com.falazar.farmupcraft.data.CropBlockDataJsonManager;
import com.falazar.farmupcraft.events.ModEvents;
import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.List;
import java.util.UUID;

@EventBusSubscriber(modid = FarmUpCraftNeoForge.MOD_ID)
public final class BareNeoForgeGameTests {
    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, functions -> functions.register(
                FarmUpCraftNeoForge.prefix("bare_neoforge"), BareNeoForgeGameTests::verify));
    }

    private static void verify(GameTestHelper helper) {
        for (var mod : ModList.get().getMods()) {
            helper.assertTrue(List.of("minecraft", "neoforge", "farmupcraft").contains(mod.getModId()),
                    "Bare acceptance must have no companion mods, found " + mod.getModId());
        }
        var level = helper.getLevel();
        var origin = helper.absolutePos(new BlockPos(1, 1, 1));
        var pos = new BlockPos(origin.getX(), Math.max(80, origin.getY()), origin.getZ());
        var previous = level.getBlockState(pos);
        var soil = level.getBlockState(pos.below());
        var previousCocoa = level.getBlockState(pos.east());
        long key = ChunkPos.pack(pos);
        var database = ModEvents.getChunkDataDatabase(level);
        var oldPlot = database.getData(key);
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "bare-test"));
        player.setGameMode(GameType.SURVIVAL);
        player.setPos(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        try {
            helper.assertTrue(level.getServer().getCommands().getDispatcher().getRoot().getChild("village") != null,
                    "Village commands register without optional content");
            helper.assertTrue(level.getServer().getCommands().getDispatcher().getRoot().getChild("market") != null,
                    "Market commands register without optional content");
            helper.assertTrue(TreeManager.isSaplingBiomeAllowed("jungle",
                    Identifier.withDefaultNamespace("jungle")),
                    "Vanilla tree rules initialize without HarvestCraft classes");
            helper.assertTrue(ModEvents.getGoodsDataDatabase(level) != null,
                    "Market database initializes on bare NeoForge");
            helper.assertValueEqual(MarketCommand.runDailyTask(level.getServer().createCommandSourceStack()), 1,
                    "All market categories, including native foods, refresh without HarvestCraft");
            helper.assertTrue(CropBlockDataJsonManager.getCropBlockDataEntries().containsKey(Blocks.WHEAT),
                    "Vanilla crop growth data survives optional tag loading");
            helper.assertTrue(level.getServer().getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE,
                    Identifier.withDefaultNamespace("cake"))).isPresent(),
                    "Vanilla crafting remains available without HarvestCraft");
            var plot = new ChunkData("farm", -1, new UUID(0, 0));
            plot.setBoughtY(pos.getY() + 1);
            database.putData(key, plot);
            level.setBlock(pos.below(), Blocks.FARMLAND.defaultBlockState(), 2);
            var crop = (CropBlock) Blocks.WHEAT;
            long seed = 0;
            while (true) {
                level.getRandom().setSeed(seed);
                if (level.getRandom().nextInt(100) < 30) break;
                seed++;
            }
            level.setBlock(pos, crop.getStateForAge(7), 2);
            level.getRandom().setSeed(seed);
            player.gameMode.useItemOn(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
            helper.assertValueEqual(level.getBlockState(pos), crop.getStateForAge(0),
                    "Vanilla farm failure still cancels and resets a mature crop without harvesting mods");
            helper.assertTrue(player.getInventory().isEmpty(), "Bare failed harvest gives no items");
            helper.assertTrue(level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(1)).isEmpty(),
                    "Bare failed harvest spawns no loot");
            helper.assertValueEqual(CropsManager.harvestBonusChance(1), 0, "Vanilla farm-level policy remains available");
            level.setBlock(pos, Blocks.JUNGLE_LOG.defaultBlockState(), 2);
            helper.assertValueEqual(TreeManager.countFruitTrees(level, pos).total(), 0,
                    "Bare nursery scan ignores plain logs");
            level.setBlock(pos.east(), Blocks.COCOA.defaultBlockState(), 2);
            var trees = TreeManager.countFruitTrees(level, pos);
            helper.assertValueEqual(trees.total(), 1, "Bare nursery counts cocoa without HarvestCraft classes");
            helper.assertValueEqual(trees.byType().get(Blocks.COCOA.getName().getString()), 1,
                    "Bare nursery includes cocoa breakdown");
        } finally {
            level.setBlock(pos, previous, 2);
            level.setBlock(pos.below(), soil, 2);
            level.setBlock(pos.east(), previousCocoa, 2);
            if (oldPlot == null) database.removeDataBatch(List.of(key));
            else database.putData(key, oldPlot);
            player.discard();
        }
        helper.succeed();
    }
}
