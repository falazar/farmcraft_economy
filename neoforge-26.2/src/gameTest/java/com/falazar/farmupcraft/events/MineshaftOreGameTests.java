package com.falazar.farmupcraft.events;

import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import com.falazar.farmupcraft.saveddata.PlacedOreData;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.structures.MineshaftPieces;
import net.minecraft.world.level.levelgen.structure.structures.MineshaftStructure;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

@EventBusSubscriber(modid = FarmUpCraftNeoForge.MOD_ID)
public final class MineshaftOreGameTests {
    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, functions -> functions.register(
                FarmUpCraftNeoForge.prefix("mineshaft_ores"), MineshaftOreGameTests::verify));
    }

    private static void verify(GameTestHelper helper) {
        try {
            verifyInternal(helper);
        } catch (RuntimeException error) {
            throw new IllegalStateException(error + " at " + java.util.Arrays.toString(error.getStackTrace()), error);
        }
    }

    private static void verifyInternal(GameTestHelper helper) {
        var level = helper.getLevel();
        var anchor = helper.absolutePos(new BlockPos(2, 1, 2));
        var center = new BlockPos((anchor.getX() >> 4) * 16 + 4, 65, (anchor.getZ() >> 4) * 16 + 4);
        var chunk = level.getChunkAt(center);
        var structure = level.registryAccess().lookupOrThrow(Registries.STRUCTURE)
                .getValue(Identifier.withDefaultNamespace("mineshaft"));
        var previousStart = chunk.getStartForStructure(structure);
        var previousReferences = new it.unimi.dsi.fastutil.longs.LongOpenHashSet(chunk.getReferencesForStructure(structure));
        var room = new MineshaftPieces.MineShaftRoom(0, net.minecraft.util.RandomSource.create(1),
                center.getX() - 2, center.getZ() - 2, MineshaftStructure.Type.NORMAL);
        room.move(0, center.getY() - 50, 0);
        var start = new StructureStart(structure, chunk.getPos(), 0, new PiecesContainer(List.of(room)));
        var messages = new java.util.ArrayList<String>();
        var player = new net.neoforged.neoforge.common.util.FakePlayer(level,
                new GameProfile(UUID.randomUUID(), "ore-test")) {
            @Override
            public void sendSystemMessage(net.minecraft.network.chat.Component message, boolean actionBar) {
                messages.add(message.getString());
            }
        };
        player.setGameMode(GameType.SURVIVAL);
        player.setPos(center.getX() + 0.5, center.getY() + 1, center.getZ() + 0.5);
        var before = new LinkedHashMap<BlockPos, BlockState>();
        var provenance = PlacedOreData.get(level);
        for (int x = (center.getX() - 49) >> 4; x <= (center.getX() + 49) >> 4; x++) {
            for (int z = (center.getZ() - 49) >> 4; z <= (center.getZ() + 49) >> 4; z++) {
                level.getChunk(x, z);
            }
        }
        for (var pos : BlockPos.betweenClosed(center.offset(-49, -7, -49), center.offset(49, 7, 49))) {
            if (!level.hasChunkAt(pos)) continue;
            before.put(pos.immutable(), level.getBlockState(pos));
            level.setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
        }
        try {
            chunk.setStartForStructure(structure, start);
            chunk.addReferenceForStructure(structure, ChunkPos.pack(center));
            helper.assertTrue(MineshaftOreEvents.insideMineshaft(level, center), "Ore inside actual structure bounds qualifies");
            helper.assertTrue(!MineshaftOreEvents.insideMineshaft(level, center.above(20)),
                    "Same chunk but outside vertical structure bounds does not qualify");
            int wins = 0;
            for (int roll = 0; roll < 10; roll++) if (MineshaftOreEvents.winsRoll(roll)) wins++;
            helper.assertValueEqual(wins, 1, "Exactly ten percent of rolls win");
            var ores = MineshaftOreEvents.orePool();
            helper.assertTrue(ores.contains(Blocks.DIAMOND_ORE) && ores.contains(Blocks.EMERALD_ORE)
                    && ores.contains(Blocks.IRON_ORE) && !ores.contains(Blocks.DIAMOND_BLOCK),
                    "Gem ores and metal ores qualify, storage blocks do not");
            helper.assertValueEqual(ores.stream().distinct().count(), (long) ores.size(),
                    "Uniform pool contains each ore block type exactly once");
            for (int y : new int[]{-6, 6}) {
                helper.assertTrue(MineshaftOreEvents.validDestination(level, center, center.offset(1, y, 0)),
                        "Inclusive six-block vertical boundary");
            }
            helper.assertTrue(!MineshaftOreEvents.validDestination(level, center, center.above(7)),
                    "Seven blocks above is outside range");
            helper.assertTrue(!MineshaftOreEvents.validDestination(level, center, center.below(7)),
                    "Seven blocks below is outside range");
            helper.assertTrue(!MineshaftOreEvents.validDestination(level, center, center.offset(49, 0, 0))
                    && !MineshaftOreEvents.validDestination(level, center, center.offset(40, 0, 40)),
                    "Three-chunk radius excludes distant and diagonal corners");
            level.setBlock(center, Blocks.CHEST.defaultBlockState(), 2);
            helper.assertTrue(!MineshaftOreEvents.validDestination(level, center, center), "Containers never replaced");
            level.setBlock(center, Blocks.AIR.defaultBlockState(), 2);
            helper.assertTrue(!MineshaftOreEvents.validDestination(level, center, center), "Air never replaced");
            level.setBlock(center, Blocks.STONE.defaultBlockState(), 2);
            provenance.mark(center);
            helper.assertTrue(!MineshaftOreEvents.validDestination(level, center, center), "Player-built stone excluded");
            provenance.remove(center);
            helper.assertTrue(!MineshaftOreEvents.touchesAir(level, center), "Solid stone is not exposed");
            for (var direction : net.minecraft.core.Direction.values()) {
                var neighbor = center.relative(direction);
                level.setBlock(neighbor, Blocks.CAVE_AIR.defaultBlockState(), 2);
                helper.assertTrue(MineshaftOreEvents.touchesAir(level, center),
                        "Air contact on each of the six faces counts");
                level.setBlock(neighbor, Blocks.STONE.defaultBlockState(), 2);
            }
            helper.assertTrue(!MineshaftOreEvents.replenish(level, center, true),
                    "Exposed placement cannot fall back to a buried host in solid terrain");
            helper.assertTrue(MineshaftOreEvents.replenish(level, center, false),
                    "Unrestricted placement succeeds in solid terrain");
            var buried = before.keySet().stream().filter(pos -> MineshaftOreEvents.isOre(level.getBlockState(pos))).toList();
            helper.assertValueEqual(buried.size(), 1, "Unrestricted mode creates exactly one ore");
            helper.assertTrue(!MineshaftOreEvents.touchesAir(level, buried.getFirst()), "Buried ore has no air face");
            level.setBlock(buried.getFirst(), Blocks.STONE.defaultBlockState(), 2);
            for (var pos : before.keySet()) {
                if (pos.getY() % 2 == 0) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
            }
            helper.assertTrue(MineshaftOreEvents.replenish(level, center, true), "Exposed placement finds air-facing stone");
            var exposed = before.keySet().stream().filter(pos -> MineshaftOreEvents.isOre(level.getBlockState(pos))).toList();
            helper.assertValueEqual(exposed.size(), 1, "Exposed mode creates exactly one ore");
            helper.assertTrue(MineshaftOreEvents.touchesAir(level, exposed.getFirst()), "Spawned exposed ore touches air");
            level.setBlock(exposed.getFirst(), Blocks.STONE.defaultBlockState(), 2);
            helper.assertTrue(MineshaftOreEvents.replenish(level, center, false),
                    "Unrestricted placement also succeeds when every stone host touches air");
            var unrestricted = before.keySet().stream().filter(pos -> MineshaftOreEvents.isOre(level.getBlockState(pos))).toList();
            helper.assertValueEqual(unrestricted.size(), 1, "Unrestricted air-facing attempt creates exactly one ore");
            helper.assertTrue(MineshaftOreEvents.touchesAir(level, unrestricted.getFirst()),
                    "Unrestricted attempts may place ore next to air");
            for (var pos : before.keySet()) level.setBlock(pos, Blocks.STONE.defaultBlockState(), 2);

            var snapshot = BlockSnapshot.create(level.dimension(), level, center);
            level.setBlock(center, Blocks.DIAMOND_ORE.defaultBlockState(), 2);
            NeoForge.EVENT_BUS.post(new BlockEvent.EntityPlaceEvent(snapshot, Blocks.STONE.defaultBlockState(), player));
            helper.assertTrue(provenance.contains(center), "Player placement event marks ore");
            var encoded = PlacedOreData.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, provenance).getOrThrow();
            helper.assertTrue(PlacedOreData.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, encoded)
                    .getOrThrow().contains(center), "Placement exclusion survives persistence");
            queueBreak(level, center, player);
            level.setBlock(center, Blocks.AIR.defaultBlockState(), 2);
            MineshaftOreEvents.tick(new ServerTickEvent.Post(() -> true, level.getServer()));
            helper.assertTrue(!provenance.contains(center), "Completed mining removes provenance");
            long count = before.keySet().stream().filter(pos -> MineshaftOreEvents.isOre(level.getBlockState(pos))).count();
            helper.assertValueEqual(count, 0L, "Player-placed ore cannot replenish");

            for (var mode : new GameType[]{GameType.CREATIVE, GameType.SPECTATOR}) {
                player.setGameMode(mode);
                level.setBlock(center, Blocks.DIAMOND_ORE.defaultBlockState(), 2);
                queueBreak(level, center, player);
                level.setBlock(center, Blocks.AIR.defaultBlockState(), 2);
                seedWinningRoll(level);
                MineshaftOreEvents.tick(new ServerTickEvent.Post(() -> true, level.getServer()));
            }
            helper.assertValueEqual(before.keySet().stream().filter(pos -> MineshaftOreEvents.isOre(level.getBlockState(pos))).count(),
                    0L, "Creative and spectator cannot replenish");
            player.setGameMode(GameType.SURVIVAL);
            level.setBlock(center, Blocks.DIAMOND_ORE.defaultBlockState(), 2);
            var canceled = new BreakBlockEvent(level, center, level.getBlockState(center), player);
            canceled.setCanceled(true);
            MineshaftOreEvents.mined(canceled);
            level.setBlock(center, Blocks.AIR.defaultBlockState(), 2);
            seedWinningRoll(level);
            MineshaftOreEvents.tick(new ServerTickEvent.Post(() -> true, level.getServer()));
            helper.assertValueEqual(before.keySet().stream().filter(pos -> MineshaftOreEvents.isOre(level.getBlockState(pos))).count(),
                    0L, "Canceled mining cannot replenish");
            helper.assertTrue(messages.isEmpty(), "Ineligible and incomplete mining produce no lucky message");
            for (var mode : new GameType[]{GameType.SURVIVAL, GameType.ADVENTURE}) {
                player.setGameMode(mode);
                level.setBlock(center, Blocks.DIAMOND_ORE.defaultBlockState(), 2);
                queueBreak(level, center, player);
                seedWinningRoll(level);
                MineshaftOreEvents.tick(new ServerTickEvent.Post(() -> true, level.getServer()));
                helper.assertTrue(level.getBlockState(center).is(Blocks.DIAMOND_ORE),
                        "An event without a completed block break does not replace anything");
                queueBreak(level, center, player);
                level.setBlock(center, Blocks.AIR.defaultBlockState(), 2);
                seedWinningRoll(level);
                MineshaftOreEvents.tick(new ServerTickEvent.Post(() -> true, level.getServer()));
            }
            var spawned = before.keySet().stream().filter(pos -> MineshaftOreEvents.isOre(level.getBlockState(pos))).toList();
            helper.assertValueEqual(spawned.size(), 2, "Survival and Adventure each replenish exactly one ore on success");
            for (var pos : spawned) helper.assertTrue(Math.abs(pos.getY() - center.getY()) <= 6
                    && Math.pow(pos.getX() - center.getX(), 2) + Math.pow(pos.getZ() - center.getZ(), 2) <= 48 * 48,
                    "Spawned ores satisfy actual horizontal and vertical ranges");
            helper.assertValueEqual(messages.size(), 2, "Each successful spawn notifies its miner exactly once");
            int totalMessages = com.falazar.farmupcraft.util.LuckyMineMessages.MESSAGES.size();
            for (int i = 2; i < totalMessages + 1; i++) {
                level.setBlock(center, Blocks.DIAMOND_ORE.defaultBlockState(), 2);
                queueBreak(level, center, player);
                level.setBlock(center, Blocks.AIR.defaultBlockState(), 2);
                seedWinningRoll(level);
                MineshaftOreEvents.tick(new ServerTickEvent.Post(() -> true, level.getServer()));
            }
            helper.assertTrue(messages.subList(0, totalMessages).equals(com.falazar.farmupcraft.util.LuckyMineMessages.MESSAGES),
                    "Twenty themed messages alternate without repeats");
            helper.assertTrue(messages.get(totalMessages).equals(messages.getFirst()), "Messages cycle after all twenty are used");
        } finally {
            provenance.remove(center);
            before.forEach((pos, state) -> level.setBlock(pos, state, 2));
            var restoredStarts = new java.util.HashMap<>(chunk.getAllStarts());
            if (previousStart == null) restoredStarts.remove(structure);
            else restoredStarts.put(structure, previousStart);
            chunk.setAllStarts(restoredStarts);
            chunk.getReferencesForStructure(structure).clear();
            chunk.getReferencesForStructure(structure).addAll(previousReferences);
            player.discard();
        }
        helper.succeed();
    }

    private static void queueBreak(net.minecraft.server.level.ServerLevel level, BlockPos pos,
            net.minecraft.server.level.ServerPlayer player) {
        MineshaftOreEvents.mined(new BreakBlockEvent(level, pos, level.getBlockState(pos), player));
    }

    private static void seedWinningRoll(net.minecraft.server.level.ServerLevel level) {
        for (long seed = 0; ; seed++) {
            level.getRandom().setSeed(seed);
            if (MineshaftOreEvents.winsRoll(level.getRandom().nextInt(10))) {
                if (!level.getRandom().nextBoolean()) {
                    level.getRandom().setSeed(seed);
                    return;
                }
            }
        }
    }
}
