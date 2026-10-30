package com.falazar.farmupcraft.gametest;

import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

@EventBusSubscriber(modid = FarmUpCraftNeoForge.MOD_ID)
public final class SurfaceBiomeScanGameTests {
    private SurfaceBiomeScanGameTests() {}

    @SubscribeEvent
    public static void registerFunctions(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, functions -> functions.register(
                FarmUpCraftNeoForge.prefix("surface_biome_scan"), SurfaceBiomeScanGameTests::scan));
    }

    private static void scan(GameTestHelper helper) {
        try (var fixture = new ResourcePlotFixture(helper)) {
            var level = fixture.level;
            var registry = level.registryAccess().lookupOrThrow(Registries.BIOME);
            var plains = registry.getOrThrow(ResourceKey.create(Registries.BIOME, Identifier.parse("minecraft:plains")));
            var cave = registry.getOrThrow(ResourceKey.create(Registries.BIOME, Identifier.parse("minecraft:lush_caves")));
            var chunks = new ArrayList<net.minecraft.world.level.chunk.LevelChunk>();
            Map<BlockPos, Holder<Biome>> originals = new HashMap<>();
            Map<BlockPos, net.minecraft.world.level.block.state.BlockState> terrain = new HashMap<>();
            var center = fixture.player.chunkPosition();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    var chunk = level.getChunk(center.x() + dx, center.z() + dz);
                    chunks.add(chunk);
                    int x0 = QuartPos.fromBlock(chunk.getPos().getMinBlockX());
                    int z0 = QuartPos.fromBlock(chunk.getPos().getMinBlockZ());
                    for (int x = x0; x < x0 + 4; x++) {
                        for (int z = z0; z < z0 + 4; z++) {
                            for (int y = QuartPos.fromBlock(level.getMinY()); y <= QuartPos.fromBlock(level.getMaxY()); y++) {
                                originals.put(new BlockPos(x, y, z), chunk.getNoiseBiome(x, y, z));
                            }
                        }
                    }
                }
            }
            var messages = new ArrayList<String>();
            var player = new FakePlayer(level, fixture.player.getGameProfile()) {
                @Override public void sendSystemMessage(Component message) { messages.add(message.getString()); }
            };
            try {
                for (BlockPos pos : BlockPos.betweenClosed(fixture.pos.offset(-10, 0, -10),
                        fixture.pos.offset(10, 0, 10))) {
                    terrain.put(pos.immutable(), level.getBlockState(pos));
                    level.setBlock(pos, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 2);
                }
                var sampler = level.getChunkSource().randomState().sampler();
                chunks.forEach(chunk -> chunk.fillBiomesFromNoise(
                        (x, y, z, climate) -> QuartPos.toBlock(y) < 16 ? cave : plains, sampler));
                player.setPos(fixture.pos.getX(), fixture.pos.getY(), fixture.pos.getZ());
                var source = player.createCommandSourceStack().withPermission(
                        net.minecraft.server.permissions.LevelBasedPermissionSet.GAMEMASTER);
                var dispatcher = level.getServer().getCommands().getDispatcher();
                for (int y : new int[]{0, fixture.pos.getY()}) {
                    messages.clear();
                    dispatcher.execute("show biomes " + fixture.pos.getX() + " " + y + " " + fixture.pos.getZ() + " 8", source);
                    helper.assertTrue(messages.size() == 1 && messages.getFirst().contains("plains")
                                    && !messages.getFirst().contains("lush_caves"),
                            "Surface scan must report plains without disclosing the underlying cave biome, regardless of input Y");
                }
            } finally {
                terrain.forEach((pos, state) -> level.setBlock(pos, state, 2));
                var sampler = level.getChunkSource().randomState().sampler();
                chunks.forEach(chunk -> chunk.fillBiomesFromNoise(
                        (x, y, z, climate) -> originals.get(new BlockPos(x, y, z)), sampler));
                player.discard();
            }
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException failure) {
            throw new IllegalStateException("Surface biome scan command failed", failure);
        }
        helper.succeed();
    }
}
