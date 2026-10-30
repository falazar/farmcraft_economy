package com.falazar.farmupcraft.database.message;

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

public class DataBaseChunkS2C<M, V> {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final CompoundTag chunkTag;
    private final String databaseName;
    private final int chunkIndex;
    private final int totalChunks;

    public DataBaseChunkS2C(CompoundTag chunkTag, String databaseName, int chunkIndex, int totalChunks) {
        this.chunkTag = chunkTag;
        this.databaseName = databaseName;
        this.chunkIndex = chunkIndex;
        this.totalChunks = totalChunks;
    }

    public DataBaseChunkS2C(FriendlyByteBuf buf) {
        this.chunkTag = buf.readNbt();
        this.databaseName = buf.readUtf();
        this.chunkIndex = buf.readInt();
        this.totalChunks = buf.readInt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeNbt(chunkTag);
        buf.writeUtf(databaseName);
        buf.writeInt(chunkIndex);
        buf.writeInt(totalChunks);
    }

    public static <M, V> void handle(DataBaseChunkS2C<M, V> message, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            Level level = ClientUtils.getLevel();
            if (level == null) return;

            ResourceLocation dbId = new ResourceLocation(message.databaseName);
            DataBaseAccess<M, V> access = DataBaseManager.getDataBaseAccess(dbId);
            if (access == null) return;

            ChunkCollector collector = ChunkCollector.getOrCreate(dbId, message.totalChunks);
            collector.addChunk(message.chunkIndex, message.chunkTag);

            if (collector.isComplete()) {
                CompoundTag merged = collector.mergeAll(message.databaseName);
                access.get(level).clearDataBase(false);
                access.get(level).load(merged, level);
                ChunkCollector.clear(dbId);
                LOGGER.info("Database '{}' successfully updated on the client.", message.databaseName);
            }
        });
        ctx.setPacketHandled(true);
    }
}
