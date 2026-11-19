package com.falazar.farmupcraft.database.message;

import com.falazar.farmupcraft.client.JeiIntegration;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

/**
 * Server → Client packet that tells the client to open JEI for a given item.
 */
public class OpenJeiRecipePacket {

    private final String itemId;

    public OpenJeiRecipePacket(String itemId) {
        this.itemId = itemId;
    }

    public OpenJeiRecipePacket(FriendlyByteBuf buf) {
        this.itemId = buf.readUtf();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUtf(itemId);
    }

    public static void handle(OpenJeiRecipePacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> openJei(message.itemId));
        context.setPacketHandled(true);
    }

    @OnlyIn(Dist.CLIENT)
    private static void openJei(String itemId) {
        JeiIntegration.openRecipe(itemId, message -> {
            var player = Minecraft.getInstance().player;
            if (player != null) player.sendSystemMessage(message);
        });
    }
}
