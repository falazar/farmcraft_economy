package com.falazar.farmupcraft.database.message;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public record MarketSaleReceiptPacket(int coins) {
    public MarketSaleReceiptPacket {
        if (coins < 0) throw new IllegalArgumentException("Invalid sale receipt");
    }

    public MarketSaleReceiptPacket(FriendlyByteBuf buffer) {
        this(buffer.readVarInt());
    }

    public void toBytes(FriendlyByteBuf buffer) { buffer.writeVarInt(coins); }

    public static void handle(MarketSaleReceiptPacket packet, Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        context.enqueueWork(() -> com.falazar.farmupcraft.client.MarketScreen.showSaleReceipt(packet.coins()));
        context.setPacketHandled(true);
    }
}
