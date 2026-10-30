package com.falazar.farmupcraft.mixin;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Item.class)
public abstract class HarvestCraftProducePlantingMixin {
    @Inject(method = "useOn", at = @At("HEAD"), cancellable = true)
    private void farmupcraft$plantProduce(UseOnContext context, CallbackInfoReturnable<InteractionResult> callback) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(context.getItemInHand().getItem());
        if (!id.getNamespace().equals("pamhc2crops") || !id.getPath().endsWith("item")) return;
        String path = id.getPath();
        Item seed = BuiltInRegistries.ITEM.get(new ResourceLocation(id.getNamespace(),
                path.substring(0, path.length() - 4) + "seeditem"));
        if (seed instanceof BlockItem plantingItem) {
            // Keep the original stack/context inside Forge's normal placement transaction.
            callback.setReturnValue(plantingItem.useOn(context));
        }
    }
}
