package com.falazar.farmupcraft.database.message;

import com.falazar.farmupcraft.util.VillageListView;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;

public record VillageListViewPacket(VillageListView view) implements CustomPacketPayload {
    public static final Type<VillageListViewPacket> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("farmupcraft", "village_list_view"));
    public static final StreamCodec<FriendlyByteBuf, VillageListViewPacket> STREAM_CODEC =
            CommandPayloadValidation.codec((b, p) -> p.toBytes(b), VillageListViewPacket::new);

    public VillageListViewPacket(FriendlyByteBuf b) { this(read(b)); }

    private static VillageListView read(FriendlyByteBuf b) {
        int page = b.readVarInt(), total = b.readVarInt(), size = b.readVarInt();
        if (size < 0 || size > VillageListView.PAGE_SIZE) throw new IllegalArgumentException("Invalid village list size");
        var entries = new ArrayList<VillageListView.Entry>(size);
        for (int i = 0; i < size; i++) {
            entries.add(new VillageListView.Entry(b.readUUID(), b.readUtf(256), b.readVarInt(),
                    b.readInt(), b.readInt(), b.readVarInt(), b.readBoolean(), b.readBoolean()));
        }
        return new VillageListView(page, total, entries);
    }

    public void toBytes(FriendlyByteBuf b) {
        b.writeVarInt(view.page()); b.writeVarInt(view.total()); b.writeVarInt(view.entries().size());
        for (var e : view.entries()) {
            b.writeUUID(e.id()); b.writeUtf(e.name(), 256); b.writeVarInt(e.level());
            b.writeInt(e.x()); b.writeInt(e.z()); b.writeVarInt(e.chunks());
            b.writeBoolean(e.bought()); b.writeBoolean(e.home());
        }
    }

    @Override
    public Type<VillageListViewPacket> type() { return TYPE; }
}
