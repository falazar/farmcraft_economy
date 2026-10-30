package com.falazar.farmupcraft;

import com.falazar.farmupcraft.data.ChunkData;
import com.falazar.farmupcraft.data.PlayerData;
import com.falazar.farmupcraft.database.DataBase;
import com.falazar.farmupcraft.database.serializers.CodecDataSerializer;
import com.falazar.farmupcraft.database.serializers.LongDataSerializer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class ChunkManagerTest {
    private static final UUID PLAYER = UUID.fromString("12345678-1234-5678-9876-123456789abc");
    private static final UUID VILLAGE = UUID.fromString("12345678-1234-5678-9876-123456789def");

    @AfterEach
    void clearState() {
        ChunkManager.clearTransientState();
    }

    @Test
    void villageMembershipUsesPersistentUuidNotEntityId() {
        assertTrue(ChunkManager.isPlayerVillageMember(null, null));
        assertFalse(ChunkManager.isPlayerVillageMember(VILLAGE, null));
        assertFalse(ChunkManager.isPlayerVillageMember(VILLAGE, new PlayerData(7, PLAYER, null, 0)));
        assertFalse(ChunkManager.isPlayerVillageMember(VILLAGE, new PlayerData(7, PLAYER, PLAYER, 0)));
        assertTrue(ChunkManager.isPlayerVillageMember(VILLAGE, new PlayerData(8, PLAYER, VILLAGE, 0)));
    }

    @Test
    void houseAccessMigratesLegacyOwnerAndPersistsWithoutGrantingStrangersAccess(MinecraftServer server) {
        try (var database = database(server)) {
            ChunkPos pos = new ChunkPos(-3, 5);
            ChunkData legacy = new ChunkData("house", 17, VILLAGE);
            assertFalse(ChunkManager.hasHouseAccess(PLAYER, 18, "Owner", legacy, database, pos));
            assertNull(legacy.getOwnerUUID());
            assertNull(database.getData(pos.pack()));
            assertTrue(ChunkManager.hasHouseAccess(PLAYER, 17, "Owner", legacy, database, pos));
            assertEquals(PLAYER, legacy.getOwnerUUID());
            assertSame(legacy, database.getData(pos.pack()));
            assertTrue(database.isDirty());

            UUID stranger = UUID.randomUUID();
            assertFalse(ChunkManager.hasHouseAccess(stranger, 17, "Stranger", legacy, database, pos),
                    "A reused entity ID cannot replace an already migrated owner");
            legacy.addVisitor("Friend");
            assertTrue(ChunkManager.hasHouseAccess(stranger, 18, "FRIEND", legacy, database, pos));
            assertTrue(ChunkManager.hasHouseAccess(PLAYER, 999, "RenamedOwner", legacy, database, pos));
        }
    }

    @Test
    void migratedOwnersUsePackedContainingChunkAtNegativeBlockBoundaries(MinecraftServer server) {
        try (var database = database(server)) {
            BlockPos block = new BlockPos(-17, 85, -1);
            ChunkPos chunk = ChunkPos.containing(block);
            assertEquals(new ChunkPos(-2, -1), chunk);
            ChunkData legacy = new ChunkData("house", 17, VILLAGE);
            assertTrue(ChunkManager.hasHouseAccess(PLAYER, 17, "Owner", legacy, database, chunk));
            assertSame(legacy, database.getData(ChunkPos.pack(block)));
            assertSame(legacy, database.getData(ChunkPos.pack(-2, -1)));
            assertNull(database.getData(ChunkPos.pack(-1, 0)));
        }
    }

    @Test
    void visitorAccessDoesNotInventAnOwnerAndProtectionUsesPurchaseHeight(MinecraftServer server) {
        try (var database = database(server)) {
            ChunkData data = new ChunkData("house", 17, VILLAGE);
            data.addVisitor("friend");
            assertTrue(ChunkManager.hasHouseAccess(PLAYER, 18, "friend", data, database, new ChunkPos(0, 0)));
            assertNull(data.getOwnerUUID());
            assertEquals(60, ChunkManager.protectionMinY(data));
            data.setBoughtY(85);
            assertEquals(85, ChunkManager.protectionMinY(data));
            data.setBoughtY(0);
            assertEquals(56, ChunkManager.protectionMinY(data));
            data.setBoughtY(-20);
            assertEquals(56, ChunkManager.protectionMinY(data));
        }
    }

    @Test
    void claimedSpawnBufferIncludesCenterAndEightNeighborsButNotSecondRing(MinecraftServer server) {
        try (var database = database(server)) {
            ChunkPos center = new ChunkPos(-10, -20);
            assertFalse(ChunkManager.isAdjacentToBoughtPlot(center, database));
            database.putData(ChunkPos.pack(-8, -20), new ChunkData("farm", 1, VILLAGE));
            assertFalse(ChunkManager.isAdjacentToBoughtPlot(center, database));
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    long key = ChunkPos.pack(center.x() + dx, center.z() + dz);
                    database.putData(key, new ChunkData("", 1, VILLAGE));
                    assertFalse(ChunkManager.isAdjacentToBoughtPlot(center, database));
                    database.putData(key, new ChunkData("village", 1, VILLAGE));
                    assertTrue(ChunkManager.isAdjacentToBoughtPlot(center, database), dx + "," + dz);
                    database.removeDataBatch(java.util.List.of(key));
                }
            }
        }
    }

    @Test
    void actualRegistryTagsProtectContainersDoorsAndGatesAndIdentifyHeldLogs(MinecraftServer server) {
        assertNull(server.overworld(), "Official fixture supplies registries, not a fake world");
        for (var block : new net.minecraft.world.level.block.Block[] {
                Blocks.OAK_DOOR, Blocks.OAK_TRAPDOOR, Blocks.OAK_FENCE_GATE,
                Blocks.CHEST, Blocks.TRAPPED_CHEST, Blocks.ENDER_CHEST, Blocks.BARREL, Blocks.SHULKER_BOX
        }) {
            assertTrue(ChunkManager.isProtectedAccessBlock(block.defaultBlockState()), block.toString());
        }
        assertFalse(ChunkManager.isProtectedAccessBlock(Blocks.STONE.defaultBlockState()));
        assertTrue(ChunkManager.isHoldingLog(new ItemStack(Items.OAK_LOG)));
        assertTrue(ChunkManager.isHoldingLog(new ItemStack(Items.STRIPPED_BIRCH_LOG)));
        assertFalse(ChunkManager.isHoldingLog(new ItemStack(Items.OAK_PLANKS)));
        assertFalse(ChunkManager.isHoldingLog(new ItemStack(Items.BONE_MEAL)));
        assertFalse(ChunkManager.isHoldingLog(ItemStack.EMPTY));
        assertFalse(ChunkManager.isHoldingLog(null));
        assertTrue(ChunkManager.isRestrictedBucket(new ItemStack(Items.WATER_BUCKET)));
        assertTrue(ChunkManager.isRestrictedBucket(new ItemStack(Items.LAVA_BUCKET)));
        assertFalse(ChunkManager.isRestrictedBucket(new ItemStack(Items.BUCKET)));
        assertFalse(ChunkManager.isRestrictedBucket(new ItemStack(Items.POWDER_SNOW_BUCKET)));
        assertFalse(ChunkManager.isRestrictedBucket(null));
    }

    @Test
    void joinGuardPreservesHeightLightVillageAndModNamespacePolicies() {
        Identifier lycanite = Identifier.parse("lycanitesmobs:spriggan");
        Identifier jengu = Identifier.parse("lycanitesmobs:jengu");
        Identifier queen = Identifier.parse("lycanitesmobs:vespidqueen");
        Identifier otherMod = Identifier.parse("example:hostile");
        assertEquals(0, ChunkManager.joinedMobSuppressionChance(Identifier.parse("minecraft:zombie"), 70, 15, "farm"));
        assertEquals(0, ChunkManager.joinedMobSuppressionChance(lycanite, 44, 15, "farm"));
        assertEquals(0, ChunkManager.joinedMobSuppressionChance(lycanite, 70, 0, "farm"));
        assertEquals(0, ChunkManager.joinedMobSuppressionChance(lycanite, 70, 15, ""));
        assertEquals(1, ChunkManager.joinedMobSuppressionChance(lycanite, 45, 1, "farm"));
        assertEquals(1, ChunkManager.joinedMobSuppressionChance(otherMod, 70, 1, "house"));
        assertEquals(0, ChunkManager.joinedMobSuppressionChance(otherMod, 70, 15, "village"));
        assertEquals(0.5, ChunkManager.joinedMobSuppressionChance(lycanite, 54, 1, "village"));
        assertEquals(0.95, ChunkManager.joinedMobSuppressionChance(lycanite, 55, 1, "VILLAGE"));
        assertEquals(1, ChunkManager.joinedMobSuppressionChance(jengu, 45, 0, "village"));
        assertEquals(1, ChunkManager.joinedMobSuppressionChance(queen, 70, 0, "farm"));
        assertEquals(0, ChunkManager.joinedMobSuppressionChance(jengu, 70, 15, ""));
    }

    @Test
    void customDropInterceptionIsTimeDistanceAndDimensionBounded() {
        BlockPos death = new BlockPos(-10, 64, 20);
        Vec3 edge = new Vec3(-7, 68, 20);
        assertTrue(ChunkManager.isRecentDeathNear(death, Level.OVERWORLD, 1_000, Level.OVERWORLD, edge, 4_000));
        assertFalse(ChunkManager.isRecentDeathNear(death, Level.OVERWORLD, 1_000, Level.OVERWORLD, edge, 4_001));
        assertFalse(ChunkManager.isRecentDeathNear(death, Level.OVERWORLD, 1_000, Level.NETHER, edge, 1_001));
        assertFalse(ChunkManager.isRecentDeathNear(death, Level.OVERWORLD, 1_000, Level.OVERWORLD,
                new Vec3(-7, 68.01, 20), 1_001));
        assertEquals(0, ChunkManager.reducedDropCount(1));
        assertEquals(1, ChunkManager.reducedDropCount(2));
        assertEquals(1, ChunkManager.reducedDropCount(3));
        assertEquals(32, ChunkManager.reducedDropCount(64));
    }

    @Test
    @SuppressWarnings("unchecked")
    void serverStopClearsCooldownAndDeathTrackingEvenWithoutOverworld(MinecraftServer server) throws Exception {
        assertNull(server.overworld());
        for (String name : new String[] {"VILLAGE_CENTER_SHOWN_AT", "WILDERNESS_SCAN_LAST_AT"}) {
            var field = ChunkManager.class.getDeclaredField(name);
            field.setAccessible(true);
            ((Map<UUID, Long>) field.get(null)).put(PLAYER, 100L);
        }
        var deaths = ChunkManager.class.getDeclaredField("LYCANITES_DEATH_POS");
        deaths.setAccessible(true);
        var deathType = Class.forName(ChunkManager.class.getName() + "$RecentDeath");
        var constructor = deathType.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        ((Collection<Object>) deaths.get(null)).add(constructor.newInstance(BlockPos.ZERO, Level.OVERWORLD, 100L));
        ChunkManager.clearTransientState();
        ChunkManager.clearTransientState();
        assertTrue(((Collection<?>) deaths.get(null)).isEmpty());
        for (String name : new String[] {"VILLAGE_CENTER_SHOWN_AT", "WILDERNESS_SCAN_LAST_AT"}) {
            var field = ChunkManager.class.getDeclaredField(name);
            field.setAccessible(true);
            assertTrue(((Map<?, ?>) field.get(null)).isEmpty());
        }
    }

    private static DataBase<Long, ChunkData> database(MinecraftServer server) {
        return new DataBase<>("chunk-manager-test", new LongDataSerializer(),
                new CodecDataSerializer<>(ChunkData.CODEC), false, 0, false, server.registryAccess());
    }
}
