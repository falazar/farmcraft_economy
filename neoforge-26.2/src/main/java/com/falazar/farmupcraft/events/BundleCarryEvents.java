package com.falazar.farmupcraft.events;

import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.TriState;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;

import java.util.Map;
import java.util.WeakHashMap;

@EventBusSubscriber(modid = FarmUpCraftNeoForge.MOD_ID)
public final class BundleCarryEvents {
    private static final Map<ServerPlayer, Long> NEXT_WARNING = new WeakHashMap<>();

    private BundleCarryEvents() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void pickup(ItemEntityPickupEvent.Pre event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)
                || !blocksAcquisition(player, event.getItemEntity().getItem(), ItemStack.EMPTY)) return;
        event.setCanPickup(TriState.FALSE);
    }

    public static boolean blocksAcquisition(ServerPlayer player, ItemStack incoming, ItemStack exchanged) {
        if (player.level().getDifficulty() != Difficulty.HARD || incoming.isEmpty()
                || !(incoming.getItem() instanceof BundleItem)) return false;
        var inventory = player.getInventory();
        boolean hasBundle = false;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            var stack = inventory.getItem(slot);
            if (stack != incoming && stack != exchanged && !stack.isEmpty() && stack.getItem() instanceof BundleItem) {
                hasBundle = true;
                break;
            }
        }
        var carried = player.containerMenu.getCarried();
        if (!hasBundle && (carried == incoming || carried == exchanged || carried.isEmpty()
                || !(carried.getItem() instanceof BundleItem))) return false;
        long now = player.level().getGameTime();
        if (now >= NEXT_WARNING.getOrDefault(player, Long.MIN_VALUE)) {
            player.sendSystemMessage(Component.literal("It's too heavy to carry another bundle."), true);
            NEXT_WARNING.put(player, now + 60);
        }
        return true;
    }
}
