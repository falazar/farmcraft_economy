package com.falazar.farmupcraft.database.message;

import com.falazar.farmupcraft.util.MarketView;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.function.Supplier;

public record MarketViewPacket(MarketView view) {
    public MarketViewPacket(FriendlyByteBuf buffer) {
        this(read(buffer));
    }

    private static MarketView read(FriendlyByteBuf buffer) {
        String category = buffer.readUtf(16);
        int minimumPrice = buffer.readVarInt();
        int coins = buffer.readInt();
        int size = buffer.readVarInt();
        if (size < 0 || size > MarketView.MAX_ENTRIES) throw new IllegalArgumentException("Invalid market list size");
        var entries = new ArrayList<MarketView.Entry>(size);
        for (int i = 0; i < size; i++) {
            entries.add(new MarketView.Entry(buffer.readUtf(256), buffer.readVarInt(), buffer.readVarInt(), buffer.readBoolean()));
        }
        return new MarketView(category, minimumPrice, coins, entries);
    }

    public void toBytes(FriendlyByteBuf buffer) {
        buffer.writeUtf(view.category(), 16);
        buffer.writeVarInt(view.minimumPrice());
        buffer.writeInt(view.coins());
        buffer.writeVarInt(view.entries().size());
        for (var entry : view.entries()) {
            buffer.writeUtf(entry.itemId(), 256);
            buffer.writeVarInt(entry.price());
            buffer.writeVarInt(entry.count());
            buffer.writeBoolean(entry.favorite());
        }
    }

    public static void handle(MarketViewPacket packet, Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        context.enqueueWork(() -> com.falazar.farmupcraft.client.MarketScreen.open(packet.view()));
        context.setPacketHandled(true);
    }
}
