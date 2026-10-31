package com.falazar.farmupcraft.gametest;

import com.falazar.farmupcraft.CropsManager;
import com.falazar.farmupcraft.command.PlotCommand;
import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@EventBusSubscriber(modid = FarmUpCraftNeoForge.MOD_ID)
public final class FarmlandBiomeGameTests {
    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, functions -> functions.register(
                FarmUpCraftNeoForge.prefix("farmland_biome_height"), FarmlandBiomeGameTests::height));
    }

    private static void height(GameTestHelper helper) {
        try (var plot = new ResourcePlotFixture(helper)) {
            var level = plot.level;
            var registry = level.registryAccess().lookupOrThrow(Registries.BIOME);
            var river = registry.getOrThrow(Biomes.RIVER);
            var plains = registry.getOrThrow(Biomes.PLAINS);
            List<Runnable> restores = new ArrayList<>();
            try {
                int cx = plot.pos.getX() >> 4;
                int cz = plot.pos.getZ() >> 4;
                for (int x = cx - 1; x <= cx + 1; x++) {
                    for (int z = cz - 1; z <= cz + 1; z++) {
                        var chunk = level.getChunk(x, z);
                        for (int y = 32; y < 96; y += 16) {
                            LevelChunkSection section = chunk.getSection(level.getSectionIndex(y));
                            List<Holder<Biome>> original = new ArrayList<>();
                            for (int qx = 0; qx < 4; qx++) {
                                for (int qy = 0; qy < 4; qy++) {
                                    for (int qz = 0; qz < 4; qz++) original.add(section.getNoiseBiome(qx, qy, qz));
                                }
                            }
                            int quartX = x * 4;
                            int quartY = y >> 2;
                            int quartZ = z * 4;
                            restores.add(() -> section.fillBiomesFromNoise((a, b, c, sampler) ->
                                    original.get(((a & 3) * 4 + (b & 3)) * 4 + (c & 3)),
                                    null, quartX, quartY, quartZ));
                            section.fillBiomesFromNoise((a, b, c, sampler) -> b < 16 ? river : plains,
                                    null, quartX, quartY, quartZ);
                        }
                    }
                }
                boolean detectsHeightBoundary = false;
                for (int y = 55; y <= 72; y++) {
                    BlockPos farmland = new BlockPos(plot.pos.getX(), y, plot.pos.getZ());
                    Set<Holder<Biome>> expected = new HashSet<>();
                    for (int x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                            expected.add(level.getBiome(new BlockPos(cx * 16 + x, y, cz * 16 + z)));
                        }
                    }
                    helper.assertValueEqual(CropsManager.getDistinctChunkBiomes(farmland, level), expected,
                            "Level-3 check samples farmland Y, not Y-1");
                    var counts = PlotCommand.getChunkBiomes(farmland, level);
                    helper.assertValueEqual(counts.values().stream().mapToInt(Integer::intValue).sum(), 256,
                            "Plot info counts all 256 farmland-height columns");
                    Set<String> names = new HashSet<>();
                    expected.forEach(biome -> names.add(registry.getKey(biome.value()).getPath()));
                    helper.assertValueEqual(counts.keySet(), names, "Plot info agrees with planting biome set");
                    detectsHeightBoundary |= !expected.equals(CropsManager.getDistinctChunkBiomes(farmland.below(), level));
                }
                helper.assertTrue(detectsHeightBoundary, "Fixture must expose a real vertical biome boundary");
                level.setBlock(plot.pos, Blocks.FARMLAND.defaultBlockState(), 2);
                plot.player.setPos(plot.pos.getX() + 0.5, plot.pos.getY() + 0.9375, plot.pos.getZ() + 0.5);
                helper.assertValueEqual(plot.player.getOnPos(), plot.pos,
                        "Player standing on fractional farmland samples that farmland, not the block below");
            } finally {
                restores.forEach(Runnable::run);
            }
        }
        helper.succeed();
    }
}
