package com.falazar.farmupcraft.database.message;

import com.falazar.farmupcraft.util.VillageView;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.ArrayList;
import java.util.function.Supplier;

public record VillageViewPacket(VillageView view) {
    public VillageViewPacket(FriendlyByteBuf buffer) {
        this(read(buffer));
    }

    private static VillageView read(FriendlyByteBuf buffer) {
        String name = buffer.readUtf(256);
        int size = buffer.readVarInt();
        if (size < 0 || size > VillageView.MAX_ENTRIES) throw new IllegalArgumentException("Invalid village view size");
        var entries = new ArrayList<VillageView.Entry>(size);
        for (int i = 0; i < size; i++)
            entries.add(new VillageView.Entry(buffer.readUtf(64), buffer.readUtf(VillageView.MAX_TEXT), buffer.readInt()));
        return new VillageView(name, entries);
    }

    public void toBytes(FriendlyByteBuf buffer) {
        buffer.writeUtf(view.name(), 256);
        buffer.writeVarInt(view.entries().size());
        for (var entry : view.entries()) {
            buffer.writeUtf(entry.label(), 64);
            buffer.writeUtf(entry.value(), VillageView.MAX_TEXT);
            buffer.writeInt(entry.color());
        }
    }

    public static void handle(VillageViewPacket packet, Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        context.enqueueWork(() -> com.falazar.farmupcraft.client.VillageInfoScreen.open(packet.view()));
        context.setPacketHandled(true);
    }
}
