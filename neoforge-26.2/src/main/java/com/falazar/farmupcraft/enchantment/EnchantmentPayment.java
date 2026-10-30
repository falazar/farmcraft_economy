package com.falazar.farmupcraft.enchantment;

import com.falazar.farmupcraft.data.PlayerData;
import com.falazar.farmupcraft.data.VillageData;
import com.falazar.farmupcraft.events.ModEvents;
import com.falazar.farmupcraft.util.CustomLogger;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import java.text.NumberFormat;

/** One synchronous menu invocation, including rollback if item application fails. */
public final class EnchantmentPayment implements AutoCloseable {
    private static final CustomLogger LOGGER = new CustomLogger("EnchantmentMenuMixin");

    private final Player player;
    private final int buttonId;
    private PlayerData playerData;
    private int cost;
    private boolean attempted;
    private boolean denied;
    private boolean reserved;
    private boolean completed;
    private boolean closed;

    public EnchantmentPayment(Player player, int buttonId) {
        this.player = player;
        this.buttonId = buttonId;
    }

    public boolean reserve() {
        if (closed) {
            throw new IllegalStateException("Enchantment payment is closed");
        }
        if (attempted) {
            return !denied;
        }
        attempted = true;
        if (player.level().isClientSide() || player.isCreative()) {
            return true;
        }
        if (buttonId < 0 || buttonId > 2) {
            throw new IllegalArgumentException("Invalid enchantment button: " + buttonId);
        }

        playerData = ModEvents.getPlayerDatabase(player.level()).getData(player.getUUID());
        VillageData village = playerData != null && playerData.getHomeVillageUUID() != null
                ? ModEvents.getVillageDatabase(player.level()).getData(playerData.getHomeVillageUUID()) : null;
        long totalCost = buttonId * 100L + (village == null ? 0L : village.getLevel() * 100L);
        if (village != null && village.getCoins() < 0) {
            deny("Your village is in debt (" + village.getCoins()
                    + " coins). Pay off the debt before enchanting.");
            return false;
        }
        if (playerData == null || totalCost < 0 || totalCost > Integer.MAX_VALUE
                || !playerData.removeCoins((int) totalCost)) {
            deny("Not enough coins to enchant! Need " + NumberFormat.getInstance().format(totalCost)
                    + " coins (you have " + NumberFormat.getInstance().format(playerData == null ? 0 : playerData.getCoins())
                    + ").");
            return false;
        }
        cost = (int) totalCost;
        reserved = true;
        return true;
    }

    public boolean isDenied() {
        return denied;
    }

    public void complete() {
        if (closed) {
            throw new IllegalStateException("Enchantment payment is closed");
        }
        if (!reserved || completed) {
            return;
        }
        completed = true;
        ModEvents.getPlayerDatabase(player.level()).putData(player.getUUID(), playerData);
        LOGGER.info("Player {} enchanted at slot {} for {} coins.", player.getName().getString(), buttonId, cost);
        player.sendSystemMessage(Component.literal("Enchanted! " + NumberFormat.getInstance().format(cost)
                + " coins deducted.").withStyle(ChatFormatting.GREEN));
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        if (reserved && !completed) {
            playerData.addCoins(cost);
            ModEvents.getPlayerDatabase(player.level()).putData(player.getUUID(), playerData);
        }
    }

    private void deny(String message) {
        denied = true;
        player.sendSystemMessage(Component.literal(message).withStyle(ChatFormatting.RED));
    }
}
