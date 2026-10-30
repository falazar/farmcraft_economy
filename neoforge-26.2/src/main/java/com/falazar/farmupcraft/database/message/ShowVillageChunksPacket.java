package com.falazar.farmupcraft.database.message;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ShowVillageChunksPacket implements CustomPacketPayload {
    public static final int MAX_CHUNKS = 16_384;
    public static final Type<ShowVillageChunksPacket> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("farmupcraft", "show_village_chunks"));
    public static final StreamCodec<FriendlyByteBuf, ShowVillageChunksPacket> STREAM_CODEC =
            CommandPayloadValidation.codec((buffer, payload) -> payload.toBytes(buffer), ShowVillageChunksPacket::new);
    private final List<Chunk> entries;
    private final boolean show;
    private final String dimId;

    public ShowVillageChunksPacket(List<int[]> chunks, boolean show, String dimId) {
        Objects.requireNonNull(chunks, "Village chunks");
        if (chunks.size() > MAX_CHUNKS) {
            throw new IllegalArgumentException("Too many village chunks");
        }
        List<Chunk> snapshot = new ArrayList<>(chunks.size());
        for (int[] chunk : chunks) {
            if (chunk == null || chunk.length != 3) {
                throw new IllegalArgumentException("Village chunk must contain x, z, and RGB color");
            }
            snapshot.add(new Chunk(chunk[0], chunk[1], chunk[2]));
        }
        this.entries = List.copyOf(snapshot);
        this.show = show;
        this.dimId = CommandPayloadValidation.identifier(dimId);
    }

    public ShowVillageChunksPacket(FriendlyByteBuf buffer) {
        this(readChunks(buffer), buffer.readBoolean(), buffer.readUtf(CommandPayloadValidation.MAX_IDENTIFIER_LENGTH));
    }

    private static List<int[]> readChunks(FriendlyByteBuf buffer) {
        int count = buffer.readInt();
        if (count < 0 || count > MAX_CHUNKS) {
            throw new IllegalArgumentException("Invalid village chunk count: " + count);
        }
        List<int[]> chunks = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            chunks.add(new int[]{buffer.readInt(), buffer.readInt(), buffer.readInt()});
        }
        return chunks;
    }

    public List<Chunk> entries() {
        return entries;
    }

    public List<int[]> chunks() {
        return entries.stream().map(chunk -> new int[]{chunk.x(), chunk.z(), chunk.color()}).toList();
    }

    public boolean show() {
        return show;
    }

    public String dimId() {
        return dimId;
    }

    public void toBytes(FriendlyByteBuf buffer) {
        buffer.writeInt(entries.size());
        for (Chunk chunk : entries) {
            buffer.writeInt(chunk.x());
            buffer.writeInt(chunk.z());
            buffer.writeInt(chunk.color());
        }
        buffer.writeBoolean(show);
        buffer.writeUtf(dimId, CommandPayloadValidation.MAX_IDENTIFIER_LENGTH);
    }

    @Override
    public Type<ShowVillageChunksPacket> type() {
        return TYPE;
    }

    public record Chunk(int x, int z, int color) {
        public Chunk {
            x = CommandPayloadValidation.chunk(x);
            z = CommandPayloadValidation.chunk(z);
            color = WaypointPayloadValidation.color(color);
        }
    }
}
