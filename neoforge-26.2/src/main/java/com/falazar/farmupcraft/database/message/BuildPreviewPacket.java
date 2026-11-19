package com.falazar.farmupcraft.database.message;

import com.falazar.farmupcraft.util.BuildPreviewGeometry;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

public record BuildPreviewPacket(String dimension, List<BuildPreviewGeometry.Entry> source,
                                 List<BuildPreviewGeometry.Entry> destination,
                                 List<BlockState> states,
                                 long expiresGameTime) implements CustomPacketPayload {
    public static final Type<BuildPreviewPacket> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("farmupcraft", "build_preview_v2"));
    public static final StreamCodec<FriendlyByteBuf, BuildPreviewPacket> STREAM_CODEC = StreamCodec.of(
            (buffer, packet) -> packet.write(buffer), BuildPreviewPacket::new);

    public BuildPreviewPacket {
        BuildPreviewGeometry.validate(dimension, source, destination, expiresGameTime);
        source = List.copyOf(source);
        destination = List.copyOf(destination);
        states = List.copyOf(states);
        if (source.size() != destination.size() || source.size() != states.size()) {
            throw new IllegalArgumentException("Build preview positions and block states must have equal counts");
        }
    }

    public static BuildPreviewPacket clear() {
        return new BuildPreviewPacket("", List.of(), List.of(), List.of(), 0L);
    }

    public BuildPreviewPacket(FriendlyByteBuf buffer) {
        this(buffer.readUtf(256), readEntries(buffer), readEntries(buffer), readStates(buffer), buffer.readVarLong());
    }

    private static List<BlockState> readStates(FriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        if (size < 0 || size > BuildPreviewGeometry.MAX_BLOCKS) {
            throw new DecoderException("Invalid build preview state count: " + size);
        }
        List<BlockState> states = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            int id = buffer.readVarInt();
            BlockState state = Block.BLOCK_STATE_REGISTRY.byId(id);
            if (state == null) {
                throw new DecoderException("Invalid build preview block state: " + id);
            }
            states.add(state);
        }
        return states;
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
        buffer.writeVarInt(states.size());
        states.forEach(state -> buffer.writeVarInt(Block.getId(state)));
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
