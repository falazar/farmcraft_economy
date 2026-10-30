package com.falazar.farmupcraft.events;

import com.falazar.farmupcraft.data.PlayerData;
import com.falazar.farmupcraft.database.DataBase;
import com.falazar.farmupcraft.database.serializers.CodecDataSerializer;
import com.falazar.farmupcraft.database.serializers.UUIDDataSerializer;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ForgeEventsTest {
    @Test
    void firstLoginCreatesTenCoinsOnlyOnceAndPreservesExistingState() {
        var database = playerDatabase();
        try {
            UUID id = UUID.randomUUID();
            assertTrue(ForgeEvents.ensurePlayerRecord(database, 123, id));
            PlayerData original = database.getData(id);
            assertEquals(id, original.getPlayerUUID());
            assertEquals(123, original.getId());
            assertEquals(10, original.getCoins());
            assertNotNull(original.getHomeVillageUUID());
            original.setCoins(87);
            assertFalse(ForgeEvents.ensurePlayerRecord(database, 456, id));
            assertSame(original, database.getData(id));
            assertEquals(87, database.getData(id).getCoins());
            assertEquals(123, database.getData(id).getId());
        } finally {
            database.shutdown();
        }
    }

    @Test
    void legacyMissingPlayerUuidIsBackfilledWithoutResettingCoinsOrVillage() {
        var database = playerDatabase();
        try {
            UUID id = UUID.randomUUID();
            UUID village = UUID.randomUUID();
            PlayerData legacy = new PlayerData(321, new UUID(0, 0), village, 57);
            database.putData(id, legacy);
            assertFalse(ForgeEvents.ensurePlayerRecord(database, 654, id));
            assertEquals(id, legacy.getPlayerUUID());
            assertEquals(57, legacy.getCoins());
            assertEquals(village, legacy.getHomeVillageUUID());
        } finally {
            database.shutdown();
        }
    }

    @Test
    void choresAreFilteredLimitedAndDeterministicWithoutMutatingTheInput() {
        var lines = new ArrayList<>(List.of("# Tasks", "", "- Feed cows", "* Plant wheat", "1. Mine stone",
                "2. Chop wood", "Visit village", "  # Header  ", " -  "));
        var original = List.copyOf(lines);
        var date = LocalDate.of(2026, 10, 28);
        var selected = ForgeEvents.selectDailyTasks(lines, date);
        assertEquals(4, selected.size());
        assertEquals(selected, ForgeEvents.selectDailyTasks(lines, date));
        assertTrue(List.of("Feed cows", "Plant wheat", "Mine stone", "Chop wood", "Visit village").containsAll(selected));
        assertEquals(original, lines);
        assertThrows(UnsupportedOperationException.class, () -> selected.add("Other"));
        assertTrue(ForgeEvents.selectDailyTasks(List.of("# Heading", "", "  "), date).isEmpty());
    }

    private static DataBase<UUID, PlayerData> playerDatabase() {
        return new DataBase<>("farmupcraft:login_test", new UUIDDataSerializer(),
                new CodecDataSerializer<>(PlayerData.CODEC), false, 0, false);
    }
}
