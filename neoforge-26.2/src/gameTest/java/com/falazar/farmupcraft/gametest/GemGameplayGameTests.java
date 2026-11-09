package com.falazar.farmupcraft.gametest;

import com.falazar.farmupcraft.GemContent;
import com.falazar.farmupcraft.gems.*;
import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.*;

@EventBusSubscriber(modid = "farmupcraft")
public final class GemGameplayGameTests {
    @SubscribeEvent public static void register(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, registry -> registry.register(
                FarmUpCraftNeoForge.prefix("gem_gameplay"), GemGameplayGameTests::gameplay));
    }

    private static void gameplay(GameTestHelper helper) {
        var level = helper.getLevel();
        var origin = helper.absolutePos(new BlockPos(1, 1, 1));
        var chunk = level.getChunkSource().getChunkNow(origin.getX() >> 4, origin.getZ() >> 4);
        helper.assertTrue(chunk != null, "Fixture chunk is loaded");
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
            level.getChunk(GemPlatform.chunkX(chunk) + dx, GemPlatform.chunkZ(chunk) + dz);
        var rules = GemRules.create(12345, List.of(
                new GemRules.Biome("minecraft:plains", false, false, false, 64),
                new GemRules.Biome("minecraft:forest", false, false, false, 64),
                new GemRules.Biome("minecraft:desert", false, false, false, 64)),
                List.of("minecraft:plains", "minecraft:forest", "minecraft:desert", "minecraft:swamp"), -64, 320);
        helper.assertValueEqual(rules.gems().size(), 5, "Five deterministic fixture gem assignments");
        UUID identity = new UUID(14, 99);
        var knowledge = GemRules.knowledge(rules, identity);
        helper.assertValueEqual(knowledge, GemRules.knowledge(rules, identity), "Villager knowledge cannot reroll");
        helper.assertValueEqual(GemWorld.enforceGemReply(level,
                new GemWorld.ConversationGem(GemRules.Kind.NONE, ""), "Rubies are north at level 100"),
                "Rubies are north at level 100", "Ignorant replies no longer have a content filter");
        helper.assertValueEqual(GemWorld.generic("Ada", identity).getName(), "Ada", "Generic conversational profile");
        helper.assertTrue(!GemWorld.replaceable(Blocks.CHEST.defaultBlockState()), "Containers are protected");
        helper.assertTrue(!GemWorld.replaceable(Blocks.DIAMOND_ORE.defaultBlockState()), "Existing ores are protected");
        helper.assertTrue(!GemWorld.replaceable(Blocks.OAK_PLANKS.defaultBlockState()), "Crafted blocks are protected");
        helper.assertTrue(GemWorld.replaceable(Blocks.DIRT.defaultBlockState()), "Player dirt permitted");
        helper.assertTrue(GemWorld.replaceable(Blocks.SAND.defaultBlockState()), "Player sand permitted");
        int y = -32;
        String biome = GemPlatform.biomeId(level.getBiome(new BlockPos(origin.getX(), y, origin.getZ())));
        Map<String, List<GemRules.Band>> gems = new LinkedHashMap<>();
        for (String gem : GemRules.GEMS)
            gems.put(gem, List.of(new GemRules.Band(biome, y, y - 15, y + 15)));
        var fixture = new GemRules.Rules(1, 12345, level.getMinY(), level.getMaxY(), 1, 2, 6, 15,
                gems, List.of(biome), Map.of(biome, 64));
        for (var target : GemRules.candidates(fixture, GemPlatform.chunkX(chunk), GemPlatform.chunkZ(chunk))) {
            var pos = new BlockPos(target.x(), target.y(), target.z());
            level.setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
            level.setBlock(new BlockPos(target.x(), y + 24, target.z()), Blocks.STONE.defaultBlockState(), 2);
        }
        int count = GemWorld.generateOwned(level, chunk, fixture, Heightmap.Types.OCEAN_FLOOR);
        helper.assertTrue(count > 0, "Actual owned pass must place ores in eligible hosts/biomes/bands");
        helper.assertValueEqual(GemWorld.generateOwned(level, chunk, fixture, Heightmap.Types.OCEAN_FLOOR), 0,
                "Restarting owned pass cannot duplicate or replace gem deposits");
        var exposed = GemRules.candidates(fixture, GemPlatform.chunkX(chunk), GemPlatform.chunkZ(chunk)).stream()
                .filter(p -> chunk.getBlockState(new BlockPos(p.x(), p.y(), p.z())).is(GemContent.ore(p.gem(), true)))
                .findFirst().orElseThrow();
        var ground = new BlockPos(exposed.x(), exposed.y(), exposed.z());
        level.setBlock(ground, Blocks.STONE.defaultBlockState(), 2);
        for (int above = ground.getY() + 1; above < level.getMaxY(); above++)
            level.setBlock(new BlockPos(ground.getX(), above, ground.getZ()), Blocks.AIR.defaultBlockState(), 2);
        for (int above = 1; above <= 10; above++) level.setBlock(ground.above(above), Blocks.OAK_LEAVES.defaultBlockState(), 2);
        GemWorld.generateOwned(level, chunk, fixture, Heightmap.Types.OCEAN_FLOOR);
        helper.assertTrue(level.getBlockState(ground).is(Blocks.STONE),
                "Leaf canopies do not count as terrain burial; surface ore must not be placed");
        GemEvents.complete(chunk);
        GemWorld.enqueue(level, chunk, false);
        helper.assertValueEqual(GemEvents.version(chunk), GemRules.VERSION, "Completed old chunks stay marked");
        var chestPos = origin.offset(4, 0, 4);
        level.setBlock(chestPos, Blocks.CHEST.defaultBlockState(), 2);
        var chest = (net.minecraft.world.level.block.entity.ChestBlockEntity) level.getBlockEntity(chestPos);
        chest.setItem(2, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND));
        GemWorld.newRuinChest(level, chestPos, 18);
        helper.assertTrue(GemEvents.chestRolled(chest), "New special chest persists outcome including ignorance");
        GemWorld.newRuinChest(level, chestPos, 18);
        helper.assertTrue(chest.getItem(2).is(net.minecraft.world.item.Items.DIAMOND), "Chest hooks preserve contents");
        chest.setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND));
        int noteSlot = GemWorld.threatNoteSlot(chest, chest);
        helper.assertTrue(noteSlot > 0 && chest.getItem(noteSlot).isEmpty(), "Late threat books must not overwrite player or gem notes");
        for (int slot = 0; slot < chest.getContainerSize(); slot++)
            chest.setItem(slot, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND));
        helper.assertValueEqual(GemWorld.threatNoteSlot(chest, chest), -1, "Full new chest contents are never overwritten");
        helper.succeed();
    }
}
