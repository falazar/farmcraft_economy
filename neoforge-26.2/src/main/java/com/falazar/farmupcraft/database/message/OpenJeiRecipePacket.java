package com.falazar.farmupcraft.database.message;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record OpenJeiRecipePacket(String itemId) implements CustomPacketPayload {
    public static final Type<OpenJeiRecipePacket> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("farmupcraft", "open_jei_recipe"));
    public static final StreamCodec<FriendlyByteBuf, OpenJeiRecipePacket> STREAM_CODEC =
            CommandPayloadValidation.codec((buffer, payload) -> payload.toBytes(buffer), OpenJeiRecipePacket::new);

    public OpenJeiRecipePacket {
        itemId = CommandPayloadValidation.identifier(itemId);
    }

    public OpenJeiRecipePacket(FriendlyByteBuf buffer) {
        this(buffer.readUtf(CommandPayloadValidation.MAX_IDENTIFIER_LENGTH));
    }

    public void toBytes(FriendlyByteBuf buffer) {
        buffer.writeUtf(itemId, CommandPayloadValidation.MAX_IDENTIFIER_LENGTH);
    }

    @Override
    public Type<OpenJeiRecipePacket> type() {
        return TYPE;
    }
}
