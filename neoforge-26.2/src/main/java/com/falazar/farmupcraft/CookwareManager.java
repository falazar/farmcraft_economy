package com.falazar.farmupcraft;

import com.falazar.farmupcraft.events.ModEvents;
import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import com.falazar.farmupcraft.util.CustomLogger;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.Set;

@EventBusSubscriber(modid = FarmUpCraftNeoForge.MOD_ID)
public class CookwareManager {
    public static final CustomLogger LOGGER = new CustomLogger(CookwareManager.class.getSimpleName());
    public static final int COOKWARE_MAX_USES = 500;
    public static final int COOKWARE_KITCHEN_USES = 10000;
    private static final String NBT_USES = "farmupcraft_cookware_uses";
    private static final Set<String> COOKWARE_IDS = Set.of(
            "cuttingboarditem", "grinderitem", "juiceritem", "mixingbowlitem", "potitem",
            "rolleritem", "saucepanitem", "skilletitem", "bakewareitem");

    @SubscribeEvent
    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) {
            return;
        }
        String namespace = BuiltInRegistries.ITEM.getKey(event.getCrafting().getItem()).getNamespace();
        if (!namespace.equals("pamhc2foodcore") && !namespace.equals("pamhc2foodextended")) {
            return;
        }
        Container grid = event.getInventory();
        for (int slot = 0; slot < grid.getContainerSize(); slot++) {
            ItemStack cookware = grid.getItem(slot);
            if (!isCookware(cookware)) {
                continue;
            }
            boolean inKitchen = isInKitchen(player);
            int remaining = consumeUse(cookware, inKitchen);
            grid.setChanged();
            if (remaining == 0) {
                player.sendOverlayMessage(Component.literal("Your " + getCookwareName(cookware)
                        + " broke from overuse!").withStyle(ChatFormatting.RED));
                LOGGER.info("Cookware broke: {} had 0 uses left.", getCookwareName(cookware));
            } else {
                if (remaining <= (inKitchen ? 500 : 50)) {
                    player.sendOverlayMessage(Component.literal("Warning: your " + getCookwareName(cookware)
                            + " only has " + remaining + " uses left!").withStyle(ChatFormatting.YELLOW));
                }
                LOGGER.info("Cookware used: {} now has {} uses remaining.", getCookwareName(cookware), remaining);
            }
            return;
        }

    }

    public static boolean isCookware(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id.getNamespace().equals("pamhc2foodcore") && COOKWARE_IDS.contains(id.getPath());
    }

    static int consumeUse(ItemStack stack, boolean inKitchen) {
        if (!isCookware(stack)) {
            throw new IllegalArgumentException("Expected a HarvestCraft cookware item");
        }
        int uses = storedUses(stack, inKitchen ? COOKWARE_KITCHEN_USES : COOKWARE_MAX_USES);
        if (uses <= 0) {
            throw new IllegalStateException("Exhausted cookware cannot be used again");
        }
        int remaining = uses - 1;
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt(NBT_USES, remaining));
        return remaining;
    }

    public static boolean isExhausted(ItemStack stack) {
        return isCookware(stack) && storedUses(stack, COOKWARE_MAX_USES) == 0;
    }

    public static int getRemainingUses(ItemStack stack) {
        return storedUses(stack, COOKWARE_MAX_USES);
    }

    public static int getMaxUsesForDisplay(ItemStack stack) {
        return getRemainingUses(stack) > COOKWARE_MAX_USES ? COOKWARE_KITCHEN_USES : COOKWARE_MAX_USES;
    }

    private static int storedUses(ItemStack stack, int initialUses) {
        var tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!tag.contains(NBT_USES)) {
            return initialUses;
        }
        int uses = tag.getInt(NBT_USES)
                .orElseThrow(() -> new IllegalArgumentException("Cookware uses must be an integer"));
        if (uses < 0) {
            throw new IllegalArgumentException("Cookware uses cannot be negative");
        }
        return uses;
    }

    private static boolean isInKitchen(Player player) {
        var plot = ModEvents.getChunkDataDatabase(player.level()).getData(ChunkPos.pack(player.blockPosition()));
        return plot != null && plot.getType().equalsIgnoreCase("kitchen");
    }

    private static String getCookwareName(ItemStack stack) {
        return switch (BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath()) {
            case "cuttingboarditem" -> "cutting board";
            case "grinderitem" -> "grinder";
            case "juiceritem" -> "juicer";
            case "mixingbowlitem" -> "mixing bowl";
            case "potitem" -> "pot";
            case "rolleritem" -> "rolling pin";
            case "saucepanitem" -> "saucepan";
            case "skilletitem" -> "skillet";
            case "bakewareitem" -> "baking dish";
            default -> "cookware";
        };
    }
}
