package com.falazar.farmupcraft.command;

import com.falazar.farmupcraft.data.ChunkData;
import com.falazar.farmupcraft.data.GameStructureData;
import com.falazar.farmupcraft.data.VillageData;
import com.falazar.farmupcraft.database.DataBase;
import com.falazar.farmupcraft.database.serializers.CodecDataSerializer;
import com.falazar.farmupcraft.database.serializers.LongDataSerializer;
import com.falazar.farmupcraft.util.StructureUtils;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class OwnershipCommandsTest {
    private static final UUID OWNER = UUID.fromString("12345678-1234-5678-9876-123456789abc");
    private static final UUID VILLAGE = UUID.fromString("12345678-1234-5678-9876-123456789def");

    @Test
    void visitorOwnershipAndFounderBypassStayDistinct(MinecraftServer server) {
        ChunkData house = new ChunkData("house", 17, VILLAGE);
        assertFalse(PlotCommand.canManageVisitors(false, OWNER, house));
        house.setOwnerUUID(OWNER);
        assertTrue(PlotCommand.canManageVisitors(false, OWNER, house));
        assertFalse(PlotCommand.canManageVisitors(false, VILLAGE, house));
        assertTrue(PlotCommand.canManageVisitors(true, VILLAGE, house));
        assertFalse(PlotCommand.canManageVisitors(false, null, house));
        assertTrue(PlotCommand.canTransferOwnership(false, "Founder", "fOUNDER"));
        assertFalse(PlotCommand.canTransferOwnership(false, "Founder", "Visitor"));
        assertFalse(PlotCommand.canTransferOwnership(false, null, "Visitor"));
        assertTrue(PlotCommand.canTransferOwnership(true, null, "Visitor"));
    }

    @Test
    void costCountsBoughtTypesButNotVillageOrMissingEntries(MinecraftServer server) {
        try (var db = chunks(server)) {
            List<ChunkPos> positions = List.of(new ChunkPos(-2, -2), new ChunkPos(-1, -2),
                    new ChunkPos(0, -2), new ChunkPos(1, -2));
            VillageData village = new VillageData(VILLAGE, "Test", positions.getFirst(), 6,
                    new ArrayList<>(positions), true, 0, "", "", "", "Founder", List.of(OWNER));
            assertEquals(200, PlotCommand.calculatePlotCost(village, "farm", db));
            db.putData(positions.get(0).pack(), new ChunkData("village", 17, VILLAGE));
            db.putData(positions.get(1).pack(), new ChunkData("farm", 17, VILLAGE));
            db.putData(positions.get(2).pack(), new ChunkData("plot", 17, VILLAGE));
            assertEquals(230, PlotCommand.calculatePlotCost(village, "farm", db));
            assertEquals(230, PlotCommand.calculatePlotCost(village, "house", db));
            assertEquals(1, PlotCommand.countPlotType(village, "FARM", db));
            assertEquals(0, PlotCommand.countPlotType(village, "portal", db));
        }
    }

    @Test
    void completeTreesRetainGamemasterRestrictionsAndBounds(MinecraftServer server) throws Exception {
        var dispatcher = new CommandDispatcher<CommandSourceStack>();
        PlotCommand.register(dispatcher);
        StructureCommand.register(dispatcher);
        var ordinary = Commands.createCompilationContext(PermissionSet.NO_PERMISSIONS);
        var admin = Commands.createCompilationContext(PermissionSet.ALL_PERMISSIONS);
        var moderator = Commands.createCompilationContext(permission -> permission.equals(Permissions.COMMANDS_MODERATOR));
        var gamemaster = Commands.createCompilationContext(permission -> permission.equals(Permissions.COMMANDS_GAMEMASTER));
        var plot = dispatcher.getRoot().getChild("plot");
        assertEquals(Set.of("info", "buy", "delete", "reclaim", "visitor", "setowner", "settype", "upgrade", "list"),
                plot.getChildren().stream().map(n -> n.getName()).collect(Collectors.toSet()));
        for (String name : List.of("delete", "reclaim")) {
            assertFalse(plot.getChild(name).canUse(ordinary));
            assertTrue(plot.getChild(name).canUse(admin));
            assertFalse(plot.getChild(name).canUse(moderator));
            assertTrue(plot.getChild(name).canUse(gamemaster));
        }
        for (String name : List.of("buy", "visitor", "setowner", "settype", "upgrade", "info", "list")) {
            assertTrue(plot.getChild(name).canUse(ordinary));
        }
        assertNotNull(plot.getChild("visitor").getChild("add").getChild("playerName").getCommand());
        assertNotNull(plot.getChild("visitor").getChild("remove").getChild("playerName").getCommand());
        assertNotNull(plot.getChild("info").getChild("biome").getCommand());
        var structures = dispatcher.getRoot().getChild("structures");
        assertEquals(Set.of("find", "list", "unmarkvisited", "detail", "inside", "setupspecialchest"),
                structures.getChildren().stream().map(n -> n.getName()).collect(Collectors.toSet()));
        for (var node : structures.getChildren()) {
            assertFalse(node.canUse(ordinary), node.getName());
            assertTrue(node.canUse(admin), node.getName());
            assertFalse(node.canUse(moderator), node.getName());
            assertTrue(node.canUse(gamemaster), node.getName());
        }
        assertNotNull(structures.getChild("detail").getChild("index").getChild("chests").getCommand());
        var radius = (IntegerArgumentType) ((ArgumentCommandNode<?, ?>)
                structures.getChild("find").getChild("filter").getChild("radius")).getType();
        assertEquals(1, radius.getMinimum());
        assertEquals(50, radius.getMaximum());
        assertThrows(com.mojang.brigadier.exceptions.CommandSyntaxException.class,
                () -> radius.parse(new StringReader("51")));
        var count = (IntegerArgumentType) ((ArgumentCommandNode<?, ?>)
                structures.getChild("list").getChild("filter").getChild("count")).getType();
        assertEquals(1, count.getMinimum());
        assertEquals(100, count.getMaximum());
        assertEquals(0, PlotCommand.setPlotVisitor(ordinary, "Guest", true));
        assertEquals(0, PlotCommand.setPlotOwner(ordinary, "Guest"));
        assertEquals(0, StructureCommand.showInsideStructure(ordinary));
        assertEquals(Identifier.fromNamespaceAndPath("farmupcraft", "farm_plots"), PlotCommand.FARM_TICKETS.id());
    }

    @Test
    void structureCentersFiltersAndPieceCoveragePreserveLegacyRules(MinecraftServer server) {
        BoundingBox tall = new BoundingBox(-17, 0, -1, 16, 120, 15);
        assertEquals(50, StructureCommand.structureCenterPosition(tall, "minecraft:other").getY());
        assertEquals(5, StructureCommand.structureCenterPosition(tall, "minecraft:mineshaft").getY());
        assertEquals(100, StructureCommand.structureCenterPosition(tall, "dungeoncrawl:dungeon").getY());
        assertTrue(StructureCommand.matchesHeightFilter(new BlockPos(0, 60, 0), "lower"));
        assertFalse(StructureCommand.matchesHeightFilter(new BlockPos(0, 60, 0), "upper"));
        assertTrue(StructureCommand.matchesHeightFilter(new BlockPos(0, 61, 0), "upper"));
        assertTrue(StructureCommand.matchesHeightFilter(new BlockPos(0, -20, 0), "all"));
        Set<Long> covered = StructureCommand.collectBoundingBoxChunks(tall);
        assertEquals(8, covered.size());
        assertTrue(covered.contains(ChunkPos.pack(-2, -1)));
        assertTrue(covered.contains(ChunkPos.pack(1, 0)));
        assertEquals("tower dungeon", StructureCommand.getCommonNameForStructure("dungeoncrawl:dungeon"));
        assertEquals("cemetery", StructureCommand.getCommonNameForStructure("structory:graveyard"));
        assertEquals("armorers cabin", StructureCommand.getCommonNameForStructure("valhelsia_structures:forge"));
        assertEquals("ruined portal", StructureCommand.getCommonNameForStructure("minecraft:ruined_portal"));
        try (var db = chunks(server)) {
            db.putData(ChunkPos.pack(-2, -1), new ChunkData("village", 17, VILLAGE));
            assertFalse(StructureCommand.isAnyStructureChunkClaimed(db, covered));
            db.putData(ChunkPos.pack(1, 0), new ChunkData("farm", 17, VILLAGE));
            assertTrue(StructureCommand.isAnyStructureChunkClaimed(db, covered));
        }
    }

    @Test
    void structureClaimsRequireVisitsAndBoughtPlotsNotVillageTerritory(MinecraftServer server) {
        BlockPos center = new BlockPos(8, 70, 8);
        long centerChunk = ChunkPos.pack(center);
        long edgeChunk = ChunkPos.pack(1, 0);
        var structure = new GameStructureData(1L, "Test", center, "minecraft:test", true, false);
        try (var db = chunks(server)) {
            db.putData(centerChunk, new ChunkData("village", 17, VILLAGE));
            assertTrue(StructureCommand.refreshStructureClaim(structure, db));
            assertFalse(structure.isOnClaimedPlot(), "stale unvisited claims are cleared");

            structure.setWasVisited(true);
            assertFalse(StructureCommand.refreshStructureClaim(structure, db));
            assertFalse(structure.isOnClaimedPlot(), "visiting ordinary village territory is not a claim");

            db.putData(centerChunk, new ChunkData("village center", 17, VILLAGE));
            assertTrue(StructureCommand.refreshStructureClaim(structure, db));
            assertTrue(structure.isOnClaimedPlot(), "visited city center can be claimed");

            structure.setWasVisited(false);
            assertTrue(StructureCommand.refreshStructureClaim(structure, db));
            assertFalse(structure.isOnClaimedPlot(), "even the city center requires a visit");

            db.putData(centerChunk, new ChunkData("farm", 17, VILLAGE));
            assertFalse(StructureCommand.refreshStructureClaim(structure, db));
            structure.setWasVisited(true);
            assertTrue(StructureCommand.refreshStructureClaim(structure, db));
            assertTrue(structure.isOnClaimedPlot(), "visiting after plot purchase claims the structure");

            db.putData(centerChunk, new ChunkData("village", 17, VILLAGE));
            assertTrue(StructureCommand.refreshStructureClaim(structure, db));
            assertFalse(structure.isOnClaimedPlot(), "ordinary territory clears even visited stale claims");

            structure.getChunkPositions().addAll(List.of(centerChunk, edgeChunk));
            db.putData(edgeChunk, new ChunkData("plot", 17, VILLAGE));
            assertTrue(StructureCommand.refreshStructureClaim(structure, db));
            assertTrue(structure.isOnClaimedPlot(), "a bought plot overlapping a structure piece counts");
            db.putData(edgeChunk, new ChunkData("village", 17, VILLAGE));
            assertTrue(StructureCommand.refreshStructureClaim(structure, db));
            assertFalse(structure.isOnClaimedPlot(), "selling the last overlapping plot clears the claim");
        }
    }

    @Test
    void structureLookupRequiresInclusiveThreeDimensionalBounds(MinecraftServer server) {
        try (var db = new DataBase<>("ownership-structure-test", new LongDataSerializer(),
                new CodecDataSerializer<>(GameStructureData.CODEC), false, 0, false, server.registryAccess())) {
            GameStructureData data = new GameStructureData(1L, "Test", new BlockPos(-8, 60, -8),
                    "minecraft:test", false, false, new BlockPos(-16, 50, -16), new BlockPos(-1, 70, -1));
            db.putData(1L, data);
            assertSame(data, StructureUtils.findStructureAtPos(db, new BlockPos(-16, 50, -16)));
            assertSame(data, StructureUtils.findStructureAtPos(db, new BlockPos(-1, 70, -1)));
            assertNull(StructureUtils.findStructureAtPos(db, new BlockPos(-8, 71, -8)));
            assertNull(StructureUtils.findStructureAtPos(db, new BlockPos(0, 60, -8)));
            assertEquals("structure.minecraft.ruined_portal", StructureUtils.getStructureLanguageKey(
                    ResourceKey.create(Registries.STRUCTURE, Identifier.withDefaultNamespace("ruined_portal"))));
        }
    }

    private static DataBase<Long, ChunkData> chunks(MinecraftServer server) {
        return new DataBase<>("ownership-chunks-test", new LongDataSerializer(),
                new CodecDataSerializer<>(ChunkData.CODEC), false, 0, false, server.registryAccess());
    }
}
