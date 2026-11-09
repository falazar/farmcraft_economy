package com.falazar.farmupcraft.database.message;

import com.falazar.farmupcraft.util.MarketView;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;

public record MarketViewPacket(MarketView view) implements CustomPacketPayload {
    public static final Type<MarketViewPacket> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("farmupcraft", "market_view"));
    public static final StreamCodec<FriendlyByteBuf, MarketViewPacket> STREAM_CODEC =
            CommandPayloadValidation.codec((buffer, packet) -> packet.toBytes(buffer), MarketViewPacket::new);

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

    @Override
    public Type<MarketViewPacket> type() {
        return TYPE;
    }
}
