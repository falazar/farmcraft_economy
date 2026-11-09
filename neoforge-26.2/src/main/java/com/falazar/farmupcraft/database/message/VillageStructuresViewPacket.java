package com.falazar.farmupcraft.database.message;

import com.falazar.farmupcraft.util.VillageStructuresView;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import java.util.ArrayList;

public record VillageStructuresViewPacket(VillageStructuresView view) implements CustomPacketPayload {
    public static final Type<VillageStructuresViewPacket> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("farmupcraft", "village_structures_view"));
    public static final StreamCodec<FriendlyByteBuf, VillageStructuresViewPacket> STREAM_CODEC =
            CommandPayloadValidation.codec((b, p) -> p.toBytes(b), VillageStructuresViewPacket::new);

    public VillageStructuresViewPacket(FriendlyByteBuf b) { this(read(b)); }

    private static VillageStructuresView read(FriendlyByteBuf b) {
        String name = b.readUtf(256);
        int size = b.readVarInt();
        if (size < 0 || size > VillageStructuresView.MAX_ENTRIES)
            throw new IllegalArgumentException("Invalid structure snapshot size");
        var entries = new ArrayList<VillageStructuresView.Entry>(size);
        for (int i = 0; i < size; i++)
            entries.add(new VillageStructuresView.Entry(b.readLong(), b.readUtf(256), b.readUtf(256),
                    b.readInt(), b.readInt(), b.readInt(), b.readVarInt(), b.readBoolean(), b.readBoolean()));
        return new VillageStructuresView(name, entries);
    }

    public void toBytes(FriendlyByteBuf b) {
        b.writeUtf(view.name(), 256);
        b.writeVarInt(view.entries().size());
        for (var e : view.entries()) {
            b.writeLong(e.id()); b.writeUtf(e.name(), 256); b.writeUtf(e.type(), 256);
            b.writeInt(e.x()); b.writeInt(e.y()); b.writeInt(e.z()); b.writeVarInt(e.distance());
            b.writeBoolean(e.claimed()); b.writeBoolean(e.visited());
        }
    }

    @Override
    public Type<VillageStructuresViewPacket> type() { return TYPE; }
}
