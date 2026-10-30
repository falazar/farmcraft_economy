package com.falazar.farmupcraft.database;

import com.falazar.farmupcraft.database.fileformats.FileFormat;
import com.falazar.farmupcraft.database.serializers.IntDataSerializer;
import com.falazar.farmupcraft.database.serializers.StringDataSerializer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DataBaseBuilderTest {
    private static final Identifier NAME = Identifier.parse("farmupcraft:test");
    private final StringDataSerializer keys = new StringDataSerializer();
    private final IntDataSerializer values = new IntDataSerializer();

    @AfterEach
    void clearRegistry() {
        DataBaseManager.clear();
    }

    private DataBaseBuilder<String, Integer> builder() {
        return new DataBaseBuilder<String, Integer>(NAME).setKeySerializer(keys).setValueSerializer(values);
    }

    @Test
    void preservesLegacyDefaults() {
        var access = builder().build();
        assertEquals(NAME, access.getDatabaseName());
        assertSame(keys, access.getKeySerializer());
        assertSame(values, access.getValueSerializer());
        assertFalse(access.isEnableExpiry());
        assertEquals(0, access.getExpiryDuration());
        assertFalse(access.isShouldBackup());
        assertEquals(0, access.getBackupInterval());
        assertEquals(6, access.getBackupCount());
        assertEquals(7L * 24 * 60 * 60 * 1000, access.getBackupRemovalTime());
        assertSame(FileFormat.NBT, access.getBackUpFileFormat());
        assertFalse(access.shouldSync());
    }

    @Test
    void preservesConfiguredValuesAndUnits() {
        var access = builder().enableExpiry(12_345).setBackUpTime(37).setBackupCount(3)
                .setBackupRemovalTime(98_765).setBackUpFileFormat(FileFormat.JSON).autoSync().build();
        assertTrue(access.isEnableExpiry());
        assertEquals(12_345, access.getExpiryDuration());
        assertTrue(access.isShouldBackup());
        assertEquals(37, access.getBackupInterval());
        assertEquals(3, access.getBackupCount());
        assertEquals(98_765, access.getBackupRemovalTime());
        assertSame(FileFormat.JSON, access.getBackUpFileFormat());
        assertTrue(access.shouldSync());
        assertFalse(builder().enableExpiry(false, 0).build().isEnableExpiry());
        assertEquals(0, builder().setBackupRemovalTime(0).build().getBackupRemovalTime());
    }

    @Test
    void rejectsMissingSerializersAndNullConfiguration() {
        assertThrows(IllegalStateException.class, () -> new DataBaseBuilder<String, Integer>(NAME).build());
        assertThrows(IllegalStateException.class,
                () -> new DataBaseBuilder<String, Integer>(NAME).setKeySerializer(keys).build());
        assertThrows(NullPointerException.class, () -> new DataBaseBuilder<>(null));
        assertThrows(NullPointerException.class, () -> builder().setKeySerializer(null));
        assertThrows(NullPointerException.class, () -> builder().setValueSerializer(null));
        assertThrows(NullPointerException.class, () -> builder().setBackUpFileFormat(null));
    }

    @Test
    void rejectsInvalidDurationsAndCountsBeforeScheduling() {
        assertThrows(IllegalArgumentException.class, () -> builder().enableExpiry(-1));
        assertThrows(IllegalArgumentException.class, () -> builder().enableExpiry(0));
        assertThrows(IllegalArgumentException.class, () -> builder().enableExpiry(false, -1));
        assertThrows(IllegalArgumentException.class, () -> builder().setBackUpTime(0));
        assertThrows(IllegalArgumentException.class, () -> builder().setBackUpTime(-1));
        assertThrows(IllegalArgumentException.class, () -> builder().setBackUpTime(Long.MAX_VALUE));
        assertThrows(IllegalArgumentException.class, () -> builder().setBackUpTime(Long.MAX_VALUE / 1000));
        assertThrows(IllegalArgumentException.class, () -> builder().setBackupCount(0));
        assertThrows(IllegalArgumentException.class, () -> builder().setBackupCount(-1));
        assertThrows(IllegalArgumentException.class, () -> builder().setBackupRemovalTime(-1));
        assertThrows(IllegalArgumentException.class, () -> new DataBaseAccess<>(NAME, keys, values,
                false, 0, true, 0, 6, 1000, FileFormat.NBT, false));
        assertThrows(IllegalArgumentException.class, () -> new DataBaseAccess<>(NAME, keys, values,
                false, 0, false, 0, 0, 1000, FileFormat.NBT, false));
        assertThrows(NullPointerException.class, () -> builder().build().get(null));
    }

    @Test
    void registryTracksSyncWithoutExposingMutableCollectionsOrOpeningDatabases() {
        var access = builder().autoSync().setBackUpTime(10).build();
        DataBaseManager.registerDataBaseAccess(NAME, access);
        assertSame(access, DataBaseManager.<String, Integer>getDataBaseAccess(NAME));
        assertEquals(java.util.List.of(NAME.toString()), DataBaseManager.getDataBasesList());
        assertTrue(DataBaseManager.getDataBasesToSync().contains(NAME));
        assertThrows(UnsupportedOperationException.class, () -> DataBaseManager.getDataBasesToSync().clear());
        assertThrows(IllegalStateException.class, () -> DataBaseManager.registerDataBaseAccess(NAME, builder().build()));
        assertThrows(IllegalArgumentException.class,
                () -> DataBaseManager.registerDataBaseAccess(Identifier.parse("farmupcraft:other"), access));
        DataBaseManager.unregisterDataBaseAccess(NAME);
        assertNull(DataBaseManager.getDataBaseAccess(NAME));
        assertTrue(DataBaseManager.getDataBasesToSync().isEmpty());
    }

    @Test
    void clearingRegistryDoesNotInstantiateUnusedDatabases() {
        var access = new DataBaseAccess<String, Integer>(NAME, keys, values,
                false, 0, true, 10, 6, 1000, FileFormat.NBT, false) {
            @Override
            public DataBase<String, Integer> get(Level level) {
                throw new AssertionError("Lifecycle cleanup must not open an unused database.");
            }
        };
        DataBaseManager.registerDataBaseAccess(NAME, access);
        assertDoesNotThrow(DataBaseManager::clear);
        assertTrue(DataBaseManager.getDataBases().isEmpty());
    }
}
