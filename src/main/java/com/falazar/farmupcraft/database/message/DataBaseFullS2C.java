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

public class DataBaseFullS2C<M, V> {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final CompoundTag database;
    private final String databaseName;

    // Constructor that accepts the database and its name
    public DataBaseFullS2C(CompoundTag database, String databaseName) {
        this.database = database;
        this.databaseName = databaseName;
    }

    // Constructor that reads from FriendlyByteBuf
    public DataBaseFullS2C(FriendlyByteBuf buf) {
        this.database = buf.readNbt();
        this.databaseName = buf.readUtf();
    }

    // Method to write data to FriendlyByteBuf
    public void toBytes(FriendlyByteBuf buf) {
        buf.writeNbt(database);
        buf.writeUtf(databaseName);
    }

    // Handle packet received
    public static <M, V> void handle(DataBaseFullS2C<M, V> message, Supplier<NetworkEvent.Context> contextSupplier) {
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

            try {
                DataBase<M, V> dataBase = dataBaseAccess.get(level);
                dataBase.clearDataBase(false);
                dataBase.load(message.database, level);
                LOGGER.info("Database '{}' successfully updated on the client.", message.databaseName);
            } catch (Exception e) {
                LOGGER.error("Failed to deserialize and update database: " + message.databaseName, e);
            }
        });
        context.setPacketHandled(true);
    }
}
