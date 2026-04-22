package com.falazar.farmupcraft.database.message;

import com.falazar.farmupcraft.client.JeiIntegration;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
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
        IJeiRuntime runtime = JeiIntegration.getRuntime();
        if (runtime == null)
            return;

        ResourceLocation rl = new ResourceLocation(itemId);
        Item item = ForgeRegistries.ITEMS.getValue(rl);
        if (item == null)
            return;

        ItemStack stack = new ItemStack(item);
        var focus = runtime.getJeiHelpers().getFocusFactory()
                .createFocus(RecipeIngredientRole.OUTPUT, VanillaTypes.ITEM_STACK, stack);
        runtime.getRecipesGui().show(List.of(focus));
    }
}
