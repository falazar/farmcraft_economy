package com.falazar.farmupcraft.mixin;

import com.falazar.farmupcraft.data.PlayerData;
import com.falazar.farmupcraft.data.VillageData;
import com.falazar.farmupcraft.events.ModEvents;
import com.falazar.farmupcraft.util.CustomLogger;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.EnchantmentMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.text.NumberFormat;

@Mixin(EnchantmentMenu.class)
public class EnchantmentMenuMixin {

    private static final CustomLogger LOGGER = new CustomLogger("EnchantmentMenuMixin");

    @Shadow
    private int[] costs;

    @Inject(method = "clickMenuButton", at = @At("HEAD"), cancellable = true)
    private void farmupcraft$onClickMenuButton(Player pPlayer, int pId,
            CallbackInfoReturnable<Boolean> cir) {
        // Server-side only.
        if (pPlayer.level().isClientSide())
            return;
        // Only valid enchant slot ids 0, 1, 2.
        if (pId < 0 || pId > 2)
            return;
        // Creative players enchant for free.
        if (pPlayer.isCreative())
            return;
        // Slot must have a valid enchantment queued.
        if (costs[pId] <= 0)
            return;

        // Base cost: slot 0=100, 1=200, 2=300.
        int baseCost = (pId + 1) * 100;

        // Add 100 per village level.
        int villageBonus = 0;
        PlayerData playerData = ModEvents.getPlayerDatabase().getData(pPlayer.getUUID());
        if (playerData != null && playerData.getHomeVillageUUID() != null) {
            VillageData village = ModEvents.getVillageDatabase().getData(playerData.getHomeVillageUUID());
            if (village != null) {
                villageBonus = village.getLevel() * 100;
            }
        }

        int totalCost = baseCost + villageBonus;
        String formattedCost = NumberFormat.getInstance().format(totalCost);

        if (playerData == null || !playerData.removeCoins(totalCost)) {
            int have = playerData != null ? playerData.getCoins() : 0;
            pPlayer.displayClientMessage(
                    Component.literal("Not enough coins to enchant! Need " + formattedCost
                            + " coins (you have " + NumberFormat.getInstance().format(have) + ").")
                            .withStyle(ChatFormatting.RED),
                    false);
            cir.setReturnValue(false);
            return;
        }

        // Save the deducted coin balance.
        ModEvents.getPlayerDatabase().putData(pPlayer.getUUID(), playerData);
        LOGGER.info("Player {} enchanted at slot {} for {} coins.", pPlayer.getName().getString(), pId, totalCost);
        pPlayer.displayClientMessage(
                Component.literal("Enchanted! " + formattedCost + " coins deducted.")
                        .withStyle(ChatFormatting.GREEN),
                false);
    }
}
