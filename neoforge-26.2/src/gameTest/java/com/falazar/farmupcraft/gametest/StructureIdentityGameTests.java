package com.falazar.farmupcraft.gametest;

import com.falazar.farmupcraft.command.StructureCommand;
import com.falazar.farmupcraft.data.GameStructureData;
import com.falazar.farmupcraft.events.ModEvents;
import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.structures.BuriedTreasurePieces;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

@EventBusSubscriber(modid = FarmUpCraftNeoForge.MOD_ID)
public final class StructureIdentityGameTests {
    private StructureIdentityGameTests() {}

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, functions -> functions.register(
                FarmUpCraftNeoForge.prefix("structure_identity"), StructureIdentityGameTests::verify));
    }

    private static void verify(GameTestHelper helper) {
        try (var fixture = new ResourcePlotFixture(helper)) {
            var level = fixture.level;
            var database = ModEvents.getGameStructureDatabase(level);
            var previousRecords = new HashMap<Long, GameStructureData>();
            for (long id : database.getKeys()) previousRecords.put(id, database.getData(id));
            var origin = level.getChunkAt(fixture.pos);
            var neighbor = level.getChunk(origin.getPos().x() - 1, origin.getPos().z());
            var oldStarts = new HashMap<>(origin.getAllStarts());
            var oldOriginReferences = new HashMap<>(origin.getAllReferences());
            var oldNeighborReferences = new HashMap<>(neighbor.getAllReferences());
            long legacyId = origin.getPos().pack();
            var registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
            var surface = registry.getValue(Identifier.withDefaultNamespace("desert_pyramid"));
            var underground = registry.getValue(Identifier.withDefaultNamespace("mineshaft"));
            String surfaceType = "minecraft:desert_pyramid", undergroundType = "minecraft:mineshaft";
            var surfacePos = new BlockPos(origin.getPos().getMinBlockX() + 6, 70,
                    origin.getPos().getMinBlockZ() + 6);
            var cabinPos = new BlockPos(surfacePos.getX() - 16, 39, surfacePos.getZ());
            var legacy = new GameStructureData(legacyId, "Saved grave", surfacePos, surfaceType,
                    false, true, surfacePos, surfacePos, List.of(legacyId));
            legacy.setSpecialChestPos(surfacePos);
            legacy.setSpecialChestOpened(true);
            legacy.setTotalChestCount(3);
            legacy.markChestOpened(surfacePos);
            try {
                database.putData(legacyId, legacy);
                var surfacePiece = new BuriedTreasurePieces.BuriedTreasurePiece(surfacePos);
                var cabinPiece = new BuriedTreasurePieces.BuriedTreasurePiece(cabinPos);
                origin.setStartForStructure(surface, new StructureStart(surface, origin.getPos(), 0,
                        new PiecesContainer(List.of(surfacePiece))));
                origin.setStartForStructure(underground, new StructureStart(underground, origin.getPos(), 0,
                        new PiecesContainer(List.of(cabinPiece))));
                for (var chunk : List.of(origin, neighbor)) {
                    // Copy the sets so fixture cleanup never keeps references mutated by the test.
                    var references = new HashMap<>(chunk.getAllReferences());
                    references.put(surface, new it.unimi.dsi.fastutil.longs.LongOpenHashSet());
                    references.put(underground, new it.unimi.dsi.fastutil.longs.LongOpenHashSet());
                    chunk.setAllReferences(references);
                    chunk.addReferenceForStructure(surface, legacyId);
                    chunk.addReferenceForStructure(underground, legacyId);
                }
                fixture.player.setPos(surfacePos.getX(), 100, surfacePos.getZ());
                var results = StructureCommand.findNearbyStructuresForVillage(surfacePos, level,
                        fixture.player, null, "upper", 1);
                helper.assertTrue(results.stream().noneMatch(result -> result.getType().equals(undergroundType)),
                        "Upper results must still hide underground structures");
                var cabins = database.getValues().stream().filter(data -> data.getType().equals(undergroundType)
                        && data.getStartChunk() == legacyId).toList();
                helper.assertValueEqual(cabins.size(), 1, "Even a surface-filtered scan captures the underground start");
                var cabin = cabins.getFirst();
                long cabinId = cabin.getId();
                helper.assertTrue(cabinId != legacyId, "Two types sharing a start chunk get distinct IDs");
                helper.assertTrue(!cabin.wasVisited(), "Remote underground discoveries stay unvisited");
                helper.assertTrue(database.getData(legacyId) == legacy, "Legacy record keeps its ID and object");
                helper.assertTrue(legacy.wasVisited() && legacy.isSpecialChestOpened()
                        && legacy.getTotalChestCount() == 3 && legacy.isChestOpened(surfacePos),
                        "Recovery preserves visited and chest progress on the existing structure");
                helper.assertValueEqual(cabin.getCenterPos(), cabinPos,
                        "Recovered structure keeps its own location, including the neighboring reference chunk");
                var savedCabin = GameStructureData.CODEC.encodeStart(NbtOps.INSTANCE, cabin).getOrThrow();
                var savedLegacy = GameStructureData.CODEC.encodeStart(NbtOps.INSTANCE, legacy).getOrThrow();
                database.putData(cabinId, GameStructureData.CODEC.parse(NbtOps.INSTANCE, savedCabin).getOrThrow());
                database.putData(legacyId, GameStructureData.CODEC.parse(NbtOps.INSTANCE, savedLegacy).getOrThrow());
                fixture.player.setPos(cabinPos.getX(), cabinPos.getY(), cabinPos.getZ());
                for (int scan = 0; scan < 2; scan++) {
                    var all = StructureCommand.findNearbyStructuresForVillage(surfacePos, level,
                            fixture.player, null, "all", 1);
                    helper.assertValueEqual(all.stream().filter(result -> result.getType().equals(surfaceType)
                            || result.getType().equals(undergroundType)).count(), 2L,
                            "Repeated references/scans return each same-chunk start once");
                    helper.assertTrue(all.stream().anyMatch(result -> result.getId() == cabinId),
                            "Results use the persisted cabin ID, never the raw colliding start ID");
                }
                helper.assertTrue(database.getData(cabinId).wasVisited(),
                        "Scanning while inside the recovered cabin marks it visited without leaving/reentering");
                helper.assertTrue(database.getData(legacyId).isSpecialChestOpened()
                        && database.getData(legacyId).getTotalChestCount() == 3,
                        "Save/reload/rescan preserves the other structure's chest progress");
                helper.assertValueEqual(database.getValues().stream().filter(data ->
                        data.getStartChunk() == legacyId && (data.getType().equals(surfaceType)
                        || data.getType().equals(undergroundType))).count(), 2L,
                        "Recovery is idempotent after persistence");
            } finally {
                origin.setAllStarts(oldStarts);
                origin.setAllReferences(oldOriginReferences);
                neighbor.setAllReferences(oldNeighborReferences);
                var added = new HashSet<>(database.getKeys());
                added.removeAll(previousRecords.keySet());
                database.removeDataBatch(List.copyOf(added));
                previousRecords.forEach(database::putData);
            }
        }
        helper.succeed();
    }
}
