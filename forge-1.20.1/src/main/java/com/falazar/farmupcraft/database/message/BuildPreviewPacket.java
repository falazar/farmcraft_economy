package com.falazar.farmupcraft.database.message;

import com.falazar.farmupcraft.client.BuildPreviewRenderer;
import com.falazar.farmupcraft.util.BuildPreviewGeometry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class BuildPreviewPacket {
    private final String dimension;
    private final List<BuildPreviewGeometry.Entry> source;
    private final List<BuildPreviewGeometry.Entry> destination;
    private final long expiresGameTime;

    public BuildPreviewPacket(String dimension, List<BuildPreviewGeometry.Entry> source,
                              List<BuildPreviewGeometry.Entry> destination, long expiresGameTime) {
        BuildPreviewGeometry.validate(dimension, source, destination, expiresGameTime);
        this.dimension = dimension;
        this.source = List.copyOf(source);
        this.destination = List.copyOf(destination);
        this.expiresGameTime = expiresGameTime;
    }

    public static BuildPreviewPacket clear() {
        return new BuildPreviewPacket("", List.of(), List.of(), 0L);
    }

    public BuildPreviewPacket(FriendlyByteBuf buffer) {
        this(buffer.readUtf(256), readEntries(buffer), readEntries(buffer), buffer.readVarLong());
    }

    private static List<BuildPreviewGeometry.Entry> readEntries(FriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        if (size < 0 || size > BuildPreviewGeometry.MAX_BLOCKS) {
            throw new IllegalArgumentException("Invalid build preview block count: " + size);
        }
        List<BuildPreviewGeometry.Entry> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(new BuildPreviewGeometry.Entry(
                    buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readInt()));
        }
        return list;
    }

    public void toBytes(FriendlyByteBuf buffer) {
        buffer.writeUtf(dimension, 256);
        writeEntries(buffer, source);
        writeEntries(buffer, destination);
        buffer.writeVarLong(expiresGameTime);
    }

    private static void writeEntries(FriendlyByteBuf buffer, List<BuildPreviewGeometry.Entry> entries) {
        buffer.writeVarInt(entries.size());
        for (BuildPreviewGeometry.Entry entry : entries) {
            buffer.writeVarInt(entry.x());
            buffer.writeVarInt(entry.y());
            buffer.writeVarInt(entry.z());
            buffer.writeInt(entry.rgb());
        }
    }

    public String dimension() { return dimension; }
    public List<BuildPreviewGeometry.Entry> source() { return source; }
    public List<BuildPreviewGeometry.Entry> destination() { return destination; }
    public long expiresGameTime() { return expiresGameTime; }

    public static void handle(BuildPreviewPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> handleClient(msg));
        ctx.get().setPacketHandled(true);
    }

    @OnlyIn(Dist.CLIENT)
    private static void handleClient(BuildPreviewPacket msg) {
        BuildPreviewRenderer.receive(msg);
    }
}
