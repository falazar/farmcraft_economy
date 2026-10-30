package com.falazar.farmupcraft.command;

import com.falazar.farmupcraft.data.ChunkData;
import com.falazar.farmupcraft.data.GameStructureData;
import com.falazar.farmupcraft.data.PlayerData;
import com.falazar.farmupcraft.data.VillageData;
import com.falazar.farmupcraft.database.DataBase;
import com.falazar.farmupcraft.database.DataBaseManager;
import com.falazar.farmupcraft.database.serializers.CodecDataSerializer;
import com.falazar.farmupcraft.database.serializers.LongDataSerializer;
import com.falazar.farmupcraft.database.serializers.UUIDDataSerializer;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class VillageCommandTest {
    private MinecraftServer server;

    @BeforeEach
    void startDatabaseLifecycle(MinecraftServer server) throws Exception {
        this.server = server;
        assertNull(server.overworld(), "The official fixture supplies registries, not a fake world");
        server.submit(() -> DataBaseManager.start(server)).get(10, TimeUnit.SECONDS);
    }

    @AfterEach
    void stopDatabaseLifecycle() throws Exception {
        server.submit(() -> DataBaseManager.shutDownDataBases(server)).get(10, TimeUnit.SECONDS);
    }

    private VillageData village(int level, int coins, ChunkPos... chunks) {
        return new VillageData(UUID.randomUUID(), "Home", ChunkPos.ZERO, level, List.of(chunks), true,
                coins, "2000-01-01", "", "2000-01-01", "Founder", new ArrayList<>());
    }

    private DataBase<Long, ChunkData> chunks() {
        return new DataBase<>("village-command-chunks", new LongDataSerializer(),
                new CodecDataSerializer<>(ChunkData.CODEC), false, 0, false, server.registryAccess());
    }

    private DataBase<Long, GameStructureData> structures() {
        return new DataBase<>("village-command-structures", new LongDataSerializer(),
                new CodecDataSerializer<>(GameStructureData.CODEC), false, 0, false, server.registryAccess());
    }

    @Test
    void allLevelsRetainFeesPopulationStructureThresholdsAndRadius() {
        for (int level = 1; level <= 10; level++) {
            assertEquals(level * 100, VillageCommand.dailyCost(level, 0));
            assertEquals(level * 100 + 45, VillageCommand.dailyCost(level, 3));
            assertEquals(2 + 3 * level, VillageCommand.getVillageRadius(village(level, 0)));
            if (level < 10) {
                assertEquals(level * 300, VillageCommand.levelUpCost(level));
                assertEquals((level + 1) * 2, VillageCommand.requiredVillagers(level + 1));
                assertEquals((level + 1) * 2 - 2, VillageCommand.requiredStructures(level + 1));
            }
        }
        assertEquals(500, VillageCommand.villageBuyCost(0));
        assertEquals(700, VillageCommand.villageBuyCost(1));
        assertEquals(2500, VillageCommand.villageBuyCost(10));
        assertThrows(IllegalArgumentException.class, () -> VillageCommand.levelUpCost(10));
        assertThrows(IllegalArgumentException.class, () -> VillageCommand.dailyCost(0, 0));
        assertThrows(IllegalArgumentException.class, () -> VillageCommand.dailyCost(11, 0));
        assertThrows(IllegalArgumentException.class, () -> VillageCommand.dailyCost(1, -1));
        assertThrows(IllegalArgumentException.class, () -> VillageCommand.villageBuyCost(-1));
        assertThrows(ArithmeticException.class, () -> VillageCommand.villageBuyCost(Integer.MAX_VALUE));
    }

    @Test
    void upkeepPaysExactlyOneHundredPerClaimedStructureAndAllowsDebtWithoutPartialOverflowMutation() {
        VillageData village = village(1, 25);
        assertEquals(-115, VillageCommand.applyDailyBalance(village, 115, 0));
        assertEquals(-90, village.getCoins());
        assertTrue(village.hasRanTodayAlready());
        assertEquals(LocalDate.now().toString(), village.getLastRanDaily());
        assertEquals(85, VillageCommand.applyDailyBalance(village, 115, 2));
        assertEquals(-5, village.getCoins());
        assertEquals(0, VillageCommand.applyDailyBalance(village, 100, 1));
        assertEquals(-5, village.getCoins());
        village.setCoins(Integer.MAX_VALUE);
        village.setLastRanDaily("2000-01-01");
        assertThrows(ArithmeticException.class, () -> VillageCommand.applyDailyBalance(village, 0, 1));
        assertEquals(Integer.MAX_VALUE, village.getCoins());
        assertEquals("2000-01-01", village.getLastRanDaily());
        assertThrows(IllegalArgumentException.class, () -> VillageCommand.applyDailyBalance(village, 0, -1));
    }

    @Test
    void dailySelectionDeduplicatesMembersSkipsOfflineMissingAndAlreadyPaidVillages() {
        try (var players = new DataBase<UUID, PlayerData>("village-command-players", new UUIDDataSerializer(),
                new CodecDataSerializer<>(PlayerData.CODEC), false, 0, false, server.registryAccess());
             var villages = new DataBase<UUID, VillageData>("village-command-villages", new UUIDDataSerializer(),
                     new CodecDataSerializer<>(VillageData.CODEC), false, 0, false, server.registryAccess())) {
            var home = village(1, 0);
            var paid = village(2, 0);
            paid.markDailyRanToday();
            var offline = village(3, 0);
            for (var village : List.of(home, paid, offline)) villages.putData(village.getUUID(), village);
            UUID first = UUID.randomUUID(), second = UUID.randomUUID(), paidPlayer = UUID.randomUUID();
            UUID orphan = UUID.randomUUID(), noHome = UUID.randomUUID(), offlinePlayer = UUID.randomUUID();
            players.putData(first, new PlayerData(1, first, home.getUUID(), 0));
            players.putData(second, new PlayerData(2, second, home.getUUID(), 0));
            players.putData(paidPlayer, new PlayerData(3, paidPlayer, paid.getUUID(), 0));
            players.putData(orphan, new PlayerData(4, orphan, UUID.randomUUID(), 0));
            players.putData(noHome, new PlayerData(5, noHome, null, 0));
            players.putData(offlinePlayer, new PlayerData(6, offlinePlayer, offline.getUUID(), 0));
            assertEquals(List.of(home), VillageCommand.villagesDueForUpkeep(
                    List.of(first, second, first, paidPlayer, orphan, noHome, UUID.randomUUID()), players, villages));
            home.markDailyRanToday();
            assertTrue(VillageCommand.villagesDueForUpkeep(List.of(first, second), players, villages).isEmpty());
            assertEquals(0, VillageCommand.runOnlinePlayerVillageUpkeep(server));
        }
    }

    @Test
    void plotsIncludeCenterAndFarmsButExcludePlainVillageMissingAndForeignClaims() {
        ChunkPos center = new ChunkPos(-1, -1), farm = new ChunkPos(0, -1), plain = new ChunkPos(1, -1);
        ChunkPos missing = new ChunkPos(2, -1), foreign = new ChunkPos(3, -1);
        var village = village(3, 0, center, farm, plain, missing, foreign);
        try (var chunks = chunks()) {
            chunks.putData(center.pack(), new ChunkData("village center", 1, village.getUUID()));
            chunks.putData(farm.pack(), new ChunkData("FARM", 1, village.getUUID()));
            chunks.putData(plain.pack(), new ChunkData("VILLAGE", 1, village.getUUID()));
            chunks.putData(foreign.pack(), new ChunkData("plot", 2, UUID.randomUUID()));
            assertEquals(2, VillageCommand.getPlotCount(village, chunks));
            assertEquals(330, VillageCommand.dailyCost(village.getLevel(), VillageCommand.getPlotCount(village, chunks)));
        }
    }

    @Test
    void structureVisibilityUsesCorrectNegativeChunkOwnershipAndVisitedUndergroundBoundary() {
        var village = village(1, 0);
        try (var chunks = chunks(); var structures = structures()) {
            chunks.putData(ChunkPos.pack(-1, -1), new ChunkData("village", 1, village.getUUID()));
            chunks.putData(ChunkPos.pack(0, -1), new ChunkData("plot", 2, UUID.randomUUID()));
            var surface = new GameStructureData(1L, "Surface", new BlockPos(-1, 60, -1), "tower", true, false);
            var secret = new GameStructureData(2L, "Secret", new BlockPos(-16, 59, -16), "tower", true, false);
            var visited = new GameStructureData(3L, "Visited", new BlockPos(-1, 59, -1), "tower", false, true);
            var foreign = new GameStructureData(4L, "Foreign", new BlockPos(0, 70, -1), "tower", true, true);
            var unowned = new GameStructureData(5L, "Unowned", new BlockPos(-17, 70, -1), "tower", true, true);
            for (var structure : List.of(surface, secret, visited, foreign, unowned))
                structures.putData(structure.getId(), structure);
            assertTrue(VillageCommand.ownsStructure(village.getUUID(), surface, chunks));
            assertFalse(VillageCommand.ownsStructure(village.getUUID(), foreign, chunks));
            assertFalse(VillageCommand.ownsStructure(village.getUUID(), unowned, chunks));
            assertEquals(Set.of(1L, 3L), VillageCommand.getVillageStructuresList(village, structures, chunks)
                    .stream().map(java.util.Map.Entry::getKey).collect(Collectors.toSet()));
            assertFalse(surface.isOnClaimedPlot());
            assertFalse(secret.isOnClaimedPlot());
            assertFalse(visited.isOnClaimedPlot());
            assertFalse(unowned.isOnClaimedPlot());
            assertTrue(foreign.isOnClaimedPlot());
            assertTrue(visited.wasVisited(), "claim repair must preserve visits");
        }
    }

    @Test
    void completeCommandTreeRetainsPublicBranchesNestedArgumentsAndGamemasterRestrictions() {
        var dispatcher = new CommandDispatcher<CommandSourceStack>();
        VillageCommand.register(dispatcher);
        var root = dispatcher.getRoot().getChild("village");
        var ordinary = Commands.createCompilationContext(PermissionSet.NO_PERMISSIONS);
        var moderator = Commands.createCompilationContext(LevelBasedPermissionSet.MODERATOR);
        var admin = Commands.createCompilationContext(LevelBasedPermissionSet.GAMEMASTER);
        assertEquals(Set.of("info", "list", "buy", "rename", "delete", "levelup", "setlevel", "rundailyupkeep",
                "biomes", "farms", "structures", "villagers", "setanimal", "setfounder", "invite", "accept",
                "decline", "animals", "admin", "map", "chores"),
                root.getChildren().stream().map(node -> node.getName()).collect(Collectors.toSet()));
        for (var node : root.getChildren()) {
            boolean restricted = Set.of("delete", "setlevel", "rundailyupkeep", "setfounder", "admin")
                    .contains(node.getName());
            assertEquals(!restricted, node.canUse(ordinary), node.getName());
            assertEquals(!restricted, node.canUse(moderator), node.getName());
            assertTrue(node.canUse(admin), node.getName());
        }
        assertFalse(root.getChild("structures").getChild("rescan").canUse(ordinary));
        assertTrue(root.getChild("structures").getChild("rescan").canUse(admin));
        assertNotNull(root.getChild("structures").getChild("index").getChild("chests").getCommand());
        assertNotNull(root.getChild("structures").getChild("filter").getChild("count").getCommand());
        assertNotNull(root.getChild("info").getCommand());
        assertNotNull(root.getChild("info").getChild("villageName").getCommand());
        assertNotNull(root.getChild("map").getChild("clear").getCommand());
        assertEquals(Set.of("regenanimalgrains", "setfounder", "addmember", "clearbreeding"),
                root.getChild("admin").getChildren().stream().map(node -> node.getName()).collect(Collectors.toSet()));
        var level = (IntegerArgumentType) ((ArgumentCommandNode<?, ?>) root.getChild("setlevel")
                .getChild("level")).getType();
        assertEquals(1, level.getMinimum());
        assertEquals(10, level.getMaximum());
        assertTrue(dispatcher.parse("village buy \"Two Words\"", ordinary).getExceptions().isEmpty());
        assertTrue(dispatcher.parse("village structures claimed 100", ordinary).getExceptions().isEmpty());
        assertFalse(dispatcher.parse("village setlevel 11", admin).getExceptions().isEmpty());
        assertFalse(dispatcher.parse("village structures claimed 0", ordinary).getExceptions().isEmpty());
    }

    @Test
    void invalidInputAndNonPlayerCommandsFailBeforeAnyWorldOrDatabaseAccess() {
        var console = Commands.createCompilationContext(PermissionSet.ALL_PERMISSIONS);
        assertEquals(0, VillageCommand.buyVillage(console, "New"));
        assertEquals(0, VillageCommand.setVillageLevel(console, null));
        assertEquals(0, VillageCommand.setVillageLevel(console, 0));
        assertEquals(0, VillageCommand.setVillageLevel(console, 11));
        assertEquals(0, VillageCommand.levelUpVillage(console));
        assertEquals(0, VillageCommand.runVillageDailyUpkeep(console));
        assertEquals(0, VillageCommand.showVillageBiomes(console));
        assertEquals(0, VillageCommand.showVillagefarms(console));
        assertEquals(0, VillageCommand.showVillageStructures(console, 0, "all"));
        assertEquals(0, VillageCommand.showVillageStructures(console, 20, "invalid"));
        assertEquals(0, VillageCommand.setVillageAnimalType(console, null));
        assertEquals(0, VillageCommand.invitePlayerToVillage(console, "Nobody"));
        assertEquals(0, VillageCommand.acceptVillageInvite(console));
        assertEquals(0, VillageCommand.declineVillageInvite(console));
    }
}
