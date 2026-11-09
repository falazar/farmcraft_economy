package com.falazar.farmupcraft.database.message;

import com.falazar.farmupcraft.util.VillageListView;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.function.Supplier;

public record VillageListViewPacket(VillageListView view) {
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

    public static void handle(VillageListViewPacket packet, Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        context.enqueueWork(() -> com.falazar.farmupcraft.client.VillageListScreen.open(packet.view()));
        context.setPacketHandled(true);
    }
}
