package com.falazar.farmupcraft.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public final class CropListChat {
    private CropListChat() {
    }

    public static Component crops(Collection<Item> items, Player player) {
        Map<String, Boolean> entries = new TreeMap<>();
        for (Item item : items) {
            String name = displayName(item);
            if (name.toLowerCase(Locale.ROOT).contains("seed")) continue;
            boolean held = player != null && (matches(item, player.getMainHandItem())
                    || matches(item, player.getOffhandItem()));
            if (player != null) {
                for (int slot = 0; slot < player.getInventory().getContainerSize() && !held; slot++) {
                    held = matches(item, player.getInventory().getItem(slot));
                }
            }
            entries.merge(name, held, Boolean::logicalOr);
        }
        return entries.entrySet().stream()
                .map(entry -> Component.literal(entry.getKey())
                        .withStyle(entry.getValue() ? ChatFormatting.GREEN : ChatFormatting.YELLOW))
                .reduce((first, second) -> first.append(Component.literal(", ").withStyle(ChatFormatting.WHITE))
                        .append(second))
                .orElse(Component.literal("None").withStyle(ChatFormatting.YELLOW));
    }

    private static boolean matches(Item crop, ItemStack held) {
        return !held.isEmpty() && identity(crop).equals(identity(held.getItem()));
    }

    private static String identity(Item item) {
        String key = item.getDescriptionId();
        if (key.startsWith("item.pamhc2crops.") && key.endsWith("seeditem")) {
            return key.substring(0, key.length() - "seeditem".length()) + "item";
        }
        if (key.equals("item.minecraft.wheat_seeds")) return "item.minecraft.wheat";
        if (key.equals("item.minecraft.beetroot_seeds")) return "item.minecraft.beetroot";
        return key;
    }

    private static String displayName(Item item) {
        String key = item.getDescriptionId();
        if (key.contains("sesameseedsseeditem")) return "Sesame";
        if (key.contains("mustardseedsseeditem")) return "Mustard";
        if (key.contains("sesameseedsitem")) return "Sesame Seeds";
        if (key.contains("mustardseedsitem")) return "Mustard Seeds";
        if (key.contains("wheat_seeds")) return "Wheat";
        return Component.translatable(key).getString();
    }
}
