package com.falazar.farmupcraft.database.message;

import com.falazar.farmupcraft.database.DataBase;
import com.falazar.farmupcraft.database.DataBaseAccess;
import com.falazar.farmupcraft.database.DataBaseManager;
import com.falazar.farmupcraft.database.DataSerializer;
import com.mojang.logging.LogUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;

public final class DataBasePayloadHandler {
    private static final Logger LOGGER = LogUtils.getLogger();

    private DataBasePayloadHandler() {
    }

    public static void handleEntry(DataBaseEntryS2C payload, IPayloadContext context) {
        try {
            Level level = context.player().level();
            requireClient(level);
            receiveEntry(requireAccess(payload.databaseName()), level, payload);
        } catch (IllegalArgumentException | IllegalStateException error) {
            LOGGER.error("Failed to apply database entry for {}", payload.databaseName(), error);
            context.disconnect(Component.literal("Failed to synchronize FarmUpCraft database: " + payload.databaseName()));
        }
    }

    public static void handleFull(DataBaseFullS2C payload, IPayloadContext context) {
        try {
            Level level = context.player().level();
            requireClient(level);
            receiveFull(requireAccess(payload.databaseName()), level, payload);
        } catch (IllegalArgumentException | IllegalStateException error) {
            LOGGER.error("Failed to apply database snapshot for {}", payload.databaseName(), error);
            context.disconnect(Component.literal("Failed to synchronize FarmUpCraft database: " + payload.databaseName()));
        }
    }

    public static <M, V> void applyEntry(DataBase<M, V> database, DataSerializer<M> keys, DataSerializer<V> values,
                                        HolderLookup.Provider registries, DataBaseEntryS2C payload) {
        requireName(database, payload.databaseName());
        M key = keys.deserialize(payload.key(), registries);
        V value = values.deserialize(payload.value(), registries);
        database.putData(key, value);
    }

    public static void applyFull(DataBase<?, ?> database, HolderLookup.Provider registries, DataBaseFullS2C payload) {
        requireName(database, payload.databaseName());
        var snapshot = payload.database();
        if (!snapshot.getString("database_name").filter(payload.databaseName()::equals).isPresent()) {
            throw new IllegalArgumentException("Database snapshot identity does not match its payload");
        }
        database.load(snapshot, registries);
    }

    private static DataBaseAccess<?, ?> requireAccess(String name) {
        DataBaseAccess<?, ?> access = DataBaseManager.getDataBaseAccess(Identifier.parse(name));
        if (access == null) {
            throw new IllegalArgumentException("Unregistered database: " + name);
        }
        if (!access.shouldSync()) {
            throw new IllegalArgumentException("Database is not configured for synchronization: " + name);
        }
        return access;
    }

    private static void requireClient(Level level) {
        if (!level.isClientSide()) {
            throw new IllegalStateException("Clientbound database payload received on a server level");
        }
    }

    private static void requireName(DataBase<?, ?> database, String name) {
        if (!database.getDatabaseName().equals(name)) {
            throw new IllegalArgumentException("Database payload identity mismatch: " + name);
        }
    }

    private static <M, V> void receiveEntry(DataBaseAccess<M, V> access, Level level, DataBaseEntryS2C payload) {
        applyEntry(access.get(level), access.getKeySerializer(), access.getValueSerializer(), level.registryAccess(), payload);
    }

    private static <M, V> void receiveFull(DataBaseAccess<M, V> access, Level level, DataBaseFullS2C payload) {
        var database = access.get(level);
        applyFull(database, level.registryAccess(), payload);
        LOGGER.info("Database '{}' synchronized on client: {} entries", payload.databaseName(), database.getSize());
    }
}
