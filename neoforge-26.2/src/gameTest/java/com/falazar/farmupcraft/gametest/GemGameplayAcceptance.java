package com.falazar.farmupcraft.gametest;

import com.falazar.farmupcraft.gems.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import java.util.*;

/** Disposable native-server probe: unlike GameTestServer, uses a real multi-biome overworld. */
@EventBusSubscriber(modid = "farmupcraft")
public final class GemGameplayAcceptance {
    @SubscribeEvent public static void started(ServerStartedEvent event) {
        if (!Boolean.getBoolean("farmupcraft.gems.acceptance")) return;
        try {
            run(event.getServer().overworld());
            System.out.println("GEM_GAMEPLAY_ACCEPTANCE_PASS");
        } catch (Throwable error) {
            error.printStackTrace();
            System.out.println("GEM_GAMEPLAY_ACCEPTANCE_FAIL");
        } finally {
            event.getServer().halt(false);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private static void run(ServerLevel level) {
        if (Boolean.getBoolean("farmupcraft.gems.acceptance.disabled")) {
            require(!GemWorld.available(level), "Single-biome world disables gem gameplay explicitly");
            require(GemWorld.hint(level, new UUID(14, 99), BlockPos.ZERO, "ruby").contains("unavailable"),
                    "Generic gem inquiries admit unavailable information");
            require(GemWorld.enforceGemReply(level, new GemWorld.ConversationGem(GemRules.Kind.NONE, ""),
                    "Hello, friend.").equals("Hello, friend."),
                    "Ignorant conversations remain available without gem rules");
            level.getChunk(0, 0);
            GemWorld.tick(level);
            require(GemWorld.nativePasses(level) == 0, "Disabled generation performs no gem passes");
            require(GemWorldState.get(level).version() == 0, "Disabled worlds are not marked initialized");
            require(!java.nio.file.Files.exists(level.getServer().getWorldPath(
                    net.minecraft.world.level.storage.LevelResource.ROOT).resolve("farmupcraft/gems/gem_biomes.json")),
                    "Disabled world never invents authoritative biome rules");
            return;
        }
        var rules = GemWorld.rules(level);
        require(rules.gems().size() == 5, "Five authoritative gem assignments");
        require(rules.gems().values().stream().allMatch(b -> b.size() == 3), "Three distinct biomes per gem");
        require(GemWorldState.get(level).version() == 1, "Independent world initialization SavedData is versioned");
        var goods = com.falazar.farmupcraft.events.ModEvents.getGoodsDataDatabase(level);
        for (var gem : GemRules.GEMS) require(goods.getData("farmupcraft:" + gem) != null, "Gem market catalog entry");
        var ruby = goods.getData("farmupcraft:ruby");
        int cost = ruby.getCost();
        boolean active = ruby.isActive();
        ruby.setActive(true);
        ruby.setCost(cost + 1);
        com.falazar.farmupcraft.command.MarketCommand.initializeMarket(level.getServer().createCommandSourceStack());
        require(ruby.getCost() == cost + 1, "Market initialization preserves administrator/dynamic gem prices");
        ruby.setActive(active);
        ruby.setCost(cost);
        var chunk = level.getChunkSource().getChunkNow(0, 0);
        if (chunk == null) {
            var spawn = level.getRespawnData().pos();
            chunk = level.getChunkSource().getChunkNow(spawn.getX() >> 4, spawn.getZ() >> 4);
        }
        if (chunk == null) chunk = level.getChunk(0, 0);
        require(chunk != null, "Explicitly controlled persisted fixture chunk loads");
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
            level.getChunk(GemPlatform.chunkX(chunk) + dx, GemPlatform.chunkZ(chunk) + dz);
        require(GemEvents.version(chunk) == 1, "Normal generation pass marked spawn chunk complete");
        GemEvents.setVersion(chunk, 0);
        GemWorld.enqueue(level, chunk, false);
        GemWorld.tick(level, 1, 1);
        require(GemWorld.lastSamples(level) <= GemWorld.SAMPLE_BUDGET, "Sampling budget bounded");
        require(GemWorld.lastPlacements(level) <= GemWorld.PLACE_BUDGET, "Placement budget bounded");
        require(GemEvents.version(chunk) == 0, "Interrupted pass must not mark completion prematurely");
        GemWorld.unload(level, chunk);
        GemWorld.enqueue(level, chunk, false);
        for (int i = 0; i < 100 && GemEvents.version(chunk) == 0; i++) {
            GemWorld.tick(level);
            require(GemWorld.lastSamples(level) <= GemWorld.SAMPLE_BUDGET, "Every resumed tick bounded");
            require(GemWorld.lastPlacements(level) <= GemWorld.PLACE_BUDGET, "Every resumed placement bounded");
        }
        require(GemEvents.version(chunk) == 1, "Unload/resume old chunk eventually completes");
        UUID identity = new UUID(14, 99);
        BlockPos origin = new BlockPos(chunk.getPos().getMinBlockX() + 8, 72, chunk.getPos().getMinBlockZ() + 8);
        require(GemWorld.enforceGemReply(level, new GemWorld.ConversationGem(GemRules.Kind.NONE, ""),
                "Rubies are north at level 100").equals("Rubies are north at level 100"),
                "Ignorant replies no longer have a content filter");
        require(!GemWorld.replaceable(Blocks.CHEST.defaultBlockState()), "Containers protected");
        require(!GemWorld.replaceable(Blocks.DIAMOND_ORE.defaultBlockState()), "Existing ore protected");
        require(!GemWorld.replaceable(Blocks.OAK_PLANKS.defaultBlockState()), "Crafted blocks protected");
        require(GemWorld.replaceable(Blocks.DIRT.defaultBlockState()), "Player dirt permitted");
        require(GemWorld.replaceable(Blocks.SAND.defaultBlockState()), "Player sand permitted");
        int y = -32;
        String biome = GemPlatform.biomeId(level.getBiome(new BlockPos(origin.getX(), y, origin.getZ())));
        Map<String, List<GemRules.Band>> gems = new LinkedHashMap<>();
        for (String gem : GemRules.GEMS) gems.put(gem, List.of(new GemRules.Band(biome, y, y - 15, y + 15)));
        var fixture = new GemRules.Rules(1, 12345, level.getMinY(), level.getMaxY(), 1, 2, 6, 15,
                gems, List.of(biome), Map.of(biome, 64));
        for (var target : GemRules.candidates(fixture, GemPlatform.chunkX(chunk), GemPlatform.chunkZ(chunk))) {
            level.setBlock(new BlockPos(target.x(), target.y(), target.z()), Blocks.STONE.defaultBlockState(), 2);
            level.setBlock(new BlockPos(target.x(), y + 24, target.z()), Blocks.STONE.defaultBlockState(), 2);
        }
        int count = GemWorld.generateOwned(level, chunk, fixture, Heightmap.Types.OCEAN_FLOOR);
        require(count > 0, "Real block placement in eligible hosts/biomes/bands");
        require(GemWorld.generateOwned(level, chunk, fixture, Heightmap.Types.OCEAN_FLOOR) == 0, "Restart cannot duplicate gem deposits");
        var chestPos = origin.offset(4, 0, 4);
        level.setBlock(chestPos, Blocks.CHEST.defaultBlockState(), 2);
        var chest = (net.minecraft.world.level.block.entity.ChestBlockEntity) level.getBlockEntity(chestPos);
        chest.setItem(2, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND));
        GemWorld.newRuinChest(level, chestPos, 18);
        require(GemEvents.chestRolled(chest), "Chest outcome including ignorance is persisted");
        GemWorld.newRuinChest(level, chestPos, 18);
        require(chest.getItem(2).is(net.minecraft.world.item.Items.DIAMOND), "Player chest contents preserved");
        var newTerrain = level.getChunk(1000, 1000);
        GemWorld.enqueue(level, newTerrain, true);
        require(GemEvents.version(newTerrain) == 1, "Real newly generated chunk owns a completed decoration pass");
        GemPlacementAcceptance.borderAndRetry(level, GemPlatform.chunkX(chunk), GemPlatform.chunkZ(chunk));
        System.out.println("GEM_GAMEPLAY native_passes=" + GemWorld.nativePasses(level) + " fixture_ores=" + count
                + " references=" + rules.referenceElevations());
    }
}
