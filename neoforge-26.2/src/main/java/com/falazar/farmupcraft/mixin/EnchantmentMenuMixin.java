package com.falazar.farmupcraft.mixin;

import com.falazar.farmupcraft.enchantment.EnchantmentPayment;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayDeque;
import java.util.Deque;

@Mixin(EnchantmentMenu.class)
public abstract class EnchantmentMenuMixin {
    @Unique
    private final Deque<EnchantmentPayment> farmupcraft$payments = new ArrayDeque<>();

    @WrapMethod(method = "clickMenuButton")
    private boolean farmupcraft$paymentScope(Player player, int buttonId, Operation<Boolean> original) {
        EnchantmentPayment payment = new EnchantmentPayment(player, buttonId);
        farmupcraft$payments.push(payment);
        try {
            return original.call(player, buttonId) && !payment.isDenied();
        } finally {
            try {
                payment.close();
            } finally {
                farmupcraft$payments.pop();
            }
        }
    }

    // 26.2: the level callback has passed lapis/XP/item checks AND selected a nonempty enchantment list.
    @Inject(method = "lambda$clickMenuButton$0",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Player;onEnchantmentPerformed(Lnet/minecraft/world/item/ItemStack;I)V"),
            cancellable = true, require = 1, allow = 1)
    private void farmupcraft$reservePayment(ItemStack item, int buttonId, Player player, int enchantmentCost,
                                          ItemStack lapis, Level level, BlockPos pos, CallbackInfo callback) {
        if (!farmupcraft$payments.element().reserve()) {
            callback.cancel();
        }
    }

    // The transformed item is now installed; commit before the noncancellable, post-apply NeoForge event.
    @Inject(method = "lambda$clickMenuButton$0",
            at = @At(value = "INVOKE",
                    target = "Lnet/neoforged/neoforge/common/CommonHooks;onPlayerEnchantItem(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;Ljava/util/List;)V"),
            require = 1, allow = 1)
    private void farmupcraft$completePayment(ItemStack item, int buttonId, Player player, int enchantmentCost,
                                           ItemStack lapis, Level level, BlockPos pos, CallbackInfo callback) {
        farmupcraft$payments.element().complete();
    }
}
