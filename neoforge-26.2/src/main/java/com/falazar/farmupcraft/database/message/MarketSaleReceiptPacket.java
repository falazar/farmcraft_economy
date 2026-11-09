package com.falazar.farmupcraft.database.message;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record MarketSaleReceiptPacket(int coins) implements CustomPacketPayload {
    public static final Type<MarketSaleReceiptPacket> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("farmupcraft", "market_sale_receipt"));
    public static final StreamCodec<FriendlyByteBuf, MarketSaleReceiptPacket> STREAM_CODEC =
            CommandPayloadValidation.codec((buffer, packet) -> buffer.writeVarInt(packet.coins()),
                    buffer -> new MarketSaleReceiptPacket(buffer.readVarInt()));

    public MarketSaleReceiptPacket {
        if (coins < 0) throw new IllegalArgumentException("Invalid sale receipt");
    }

    @Override
    public Type<MarketSaleReceiptPacket> type() { return TYPE; }
}
