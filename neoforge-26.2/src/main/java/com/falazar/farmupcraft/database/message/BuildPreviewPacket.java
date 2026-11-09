package com.falazar.farmupcraft.database.message;

import com.falazar.farmupcraft.util.BuildPreviewGeometry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

public record BuildPreviewPacket(String dimension, List<BuildPreviewGeometry.Entry> source,
                                 List<BuildPreviewGeometry.Entry> destination,
                                 long expiresGameTime) implements CustomPacketPayload {
    public static final Type<BuildPreviewPacket> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("farmupcraft", "build_preview"));
    public static final StreamCodec<FriendlyByteBuf, BuildPreviewPacket> STREAM_CODEC = StreamCodec.of(
            (buffer, packet) -> packet.write(buffer), BuildPreviewPacket::new);

    public BuildPreviewPacket {
        BuildPreviewGeometry.validate(dimension, source, destination, expiresGameTime);
        source = List.copyOf(source);
        destination = List.copyOf(destination);
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

    public void write(FriendlyByteBuf buffer) {
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

    @Override
    public Type<BuildPreviewPacket> type() {
        return TYPE;
    }
}
