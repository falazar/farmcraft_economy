package com.falazar.farmupcraft.database.message;

import com.falazar.farmupcraft.database.DataBase;
import com.falazar.farmupcraft.database.DataBaseAccess;
import com.falazar.farmupcraft.database.DataBaseManager;
import com.falazar.farmupcraft.util.ClientUtils;
import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkEvent;
import org.slf4j.Logger;

import java.util.function.Supplier;

public class DataBaseEntryS2C<M, V> {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final CompoundTag key;
    private final CompoundTag value;
    private final String databaseName;

    public DataBaseEntryS2C(CompoundTag key, CompoundTag value, String databaseName) {
        this.key = key;
        this.value = value;
        this.databaseName = databaseName;
    }

    // Constructor that reads from FriendlyByteBuf
    public DataBaseEntryS2C(FriendlyByteBuf buf) {
        this.key = buf.readNbt();
        this.value = buf.readNbt();
        this.databaseName = buf.readUtf();
    }

    // Method to write to FriendlyByteBuf
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeNbt(key);
        buf.writeNbt(value);
        buf.writeUtf(databaseName);
    }

    // Handle packet received
    public static <M, V> void handle(DataBaseEntryS2C<M, V> message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            Level level =  ClientUtils.getLevel();

            if (level == null) {
                LOGGER.error("ClientLevel is null.");
                return;
            }

            DataBaseAccess<M, V> dataBaseAccess;
            try {
                dataBaseAccess = DataBaseManager.getDataBaseAccess(new ResourceLocation(message.databaseName));
            } catch (Exception e) {
                LOGGER.error("Failed to get DataBaseAccess for database: " + message.databaseName, e);
                return;
            }

            if (dataBaseAccess == null) {
                LOGGER.error("DataBaseAccess is null for database: " + message.databaseName);
                return;
            }

            // Get the key and value serializers
            DataBase.DataSerializer<M> keySerializer;
            DataBase.DataSerializer<V> valueSerializer;
            try {
                keySerializer = dataBaseAccess.getKeySerializer();
                valueSerializer = dataBaseAccess.getValueSerializer();
            } catch (Exception e) {
                LOGGER.error("Failed to get serializers for database: " + message.databaseName, e);
                return;
            }

            if (keySerializer == null || valueSerializer == null) {
                LOGGER.error("Key or value serializer is null for database: " + message.databaseName);
                return;
            }

            M deserializedKey;
            V deserializedValue;
            try {
                deserializedKey = keySerializer.deserialize(message.key, level);
                deserializedValue = valueSerializer.deserialize(message.value, level);
            } catch (Exception e) {
                LOGGER.error("Failed to deserialize key or value for database: " + message.databaseName, e);
                return;
            }

            DataBase<M, V> dataBase;
            try {
                dataBase = dataBaseAccess.get(level);
            } catch (Exception e) {
                LOGGER.error("Failed to access database: " + message.databaseName, e);
                return;
            }

            if (dataBase == null) {
                LOGGER.error("DataBase is null for database: " + message.databaseName);
                return;
            }

            try {
                // Update the database with the deserialized key and value
                dataBase.putData(deserializedKey, deserializedValue);
            } catch (Exception e) {
                LOGGER.error("Failed to update database: " + message.databaseName + " with key and value", e);
            }
        });
        context.setPacketHandled(true);
    }
}
