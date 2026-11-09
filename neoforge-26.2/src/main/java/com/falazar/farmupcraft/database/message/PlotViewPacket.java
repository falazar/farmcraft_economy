package com.falazar.farmupcraft.database.message;

import com.falazar.farmupcraft.util.PlotView;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import java.util.ArrayList;

public record PlotViewPacket(PlotView view) implements CustomPacketPayload {
    public static final Type<PlotViewPacket> TYPE = new Type<>(Identifier.fromNamespaceAndPath("farmupcraft", "plot_view"));
    public static final StreamCodec<FriendlyByteBuf, PlotViewPacket> STREAM_CODEC =
            CommandPayloadValidation.codec((b, p) -> p.toBytes(b), PlotViewPacket::new);
    public PlotViewPacket(FriendlyByteBuf b) { this(read(b)); }

    private static PlotView read(FriendlyByteBuf b) {
        var details = new VillageViewPacket(b).view();
        String dimension = b.readUtf(256);
        int x = b.readInt(), z = b.readInt();
        String type = b.readUtf(64);
        int buy = b.readVarInt(), change = b.readVarInt();
        boolean canBuy = b.readBoolean(), canChange = b.readBoolean();
        int size = b.readVarInt();
        if (size < 0 || size > 32) throw new IllegalArgumentException("Invalid plot type count");
        var types = new ArrayList<String>(size);
        for (int i = 0; i < size; i++) types.add(b.readUtf(64));
        return new PlotView(details, dimension, x, z, type, buy, change, canBuy, canChange, types);
    }

    public void toBytes(FriendlyByteBuf b) {
        new VillageViewPacket(view.details()).toBytes(b);
        b.writeUtf(view.dimension(), 256); b.writeInt(view.x()); b.writeInt(view.z()); b.writeUtf(view.type(), 64);
        b.writeVarInt(view.buyCost()); b.writeVarInt(view.changeCost());
        b.writeBoolean(view.canBuy()); b.writeBoolean(view.canChange());
        b.writeVarInt(view.types().size());
        for (String type : view.types()) b.writeUtf(type, 64);
    }

    @Override
    public Type<PlotViewPacket> type() { return TYPE; }
}
