package com.falazar.farmupcraft.database.message;

import com.falazar.farmupcraft.util.VillageStructuresView;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.ArrayList;
import java.util.function.Supplier;

public record VillageStructuresViewPacket(VillageStructuresView view) {
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

    public static void handle(VillageStructuresViewPacket p, Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        context.enqueueWork(() -> com.falazar.farmupcraft.client.VillageStructuresScreen.open(p.view()));
        context.setPacketHandled(true);
    }
}
