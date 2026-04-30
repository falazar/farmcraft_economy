package com.falazar.farmupcraft;

import com.falazar.farmupcraft.data.ChunkData;
import com.falazar.farmupcraft.database.DataBase;
import com.falazar.farmupcraft.events.ModEvents;
import com.falazar.farmupcraft.util.CustomLogger;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import static com.falazar.farmupcraft.FarmUpCraft.MODID;

/**
 * Manages cookware durability for HarvestCraft 2 tools (cutting board, grinder,
 * juicer).
 * <p>
 * Rules:
 * - Each cookware item starts with {@link #COOKWARE_MAX_USES} uses (500).
 * - Using cookware in a crafting recipe outside a kitchen costs 1 use.
 * - Using it inside a kitchen plot costs 0 uses (free).
 * - When uses reach 0, the cookware is not returned (it breaks).
 */
@Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CookwareManager {

    public static final CustomLogger LOGGER = new CustomLogger(CookwareManager.class.getSimpleName());

    public static final int COOKWARE_MAX_USES = 500;
    private static final String NBT_USES = "farmupcraft_cookware_uses";

    // All cookware item IDs from pamhc2foodcore.
    private static final String CUTTING_BOARD = "pamhc2foodcore:cuttingboarditem";
    private static final String GRINDER       = "pamhc2foodcore:grinderitem";
    private static final String JUICER        = "pamhc2foodcore:juiceritem";
    private static final String MIXING_BOWL   = "pamhc2foodcore:mixingbowlitem";
    private static final String POT           = "pamhc2foodcore:potitem";
    private static final String ROLLING_PIN   = "pamhc2foodcore:rolleritem";
    private static final String SAUCEPAN      = "pamhc2foodcore:saucepanitem";
    private static final String SKILLET       = "pamhc2foodcore:skilletitem";
    private static final String BAKEWARE      = "pamhc2foodcore:bakewareitem";

    private static final java.util.Set<String> COOKWARE_IDS = java.util.Set.of(
            CUTTING_BOARD, GRINDER, JUICER, MIXING_BOWL, POT, ROLLING_PIN, SAUCEPAN, SKILLET, BAKEWARE
    );

    @SubscribeEvent
    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        try {
            Player player = event.getEntity();
            if (player.level().isClientSide())
                return;

            // Only care about pamhc2 food results.
            ItemStack result = event.getCrafting();
            ResourceLocation resultKey = ForgeRegistries.ITEMS.getKey(result.getItem());
            if (resultKey == null)
                return;
            String resultId = resultKey.toString();
            if (!resultId.startsWith("pamhc2foodcore:") && !resultId.startsWith("pamhc2foodextended:"))
                return;

            // Scan the crafting grid for a cookware item.
            Container grid = event.getInventory();
            ItemStack cookware = ItemStack.EMPTY;
            for (int i = 0; i < grid.getContainerSize(); i++) {
                ItemStack slot = grid.getItem(i);
                if (isCookware(slot)) {
                    cookware = slot.copy();
                    break;
                }
            }
            if (cookware.isEmpty())
                return;

            // Determine current uses (initialise to max on first craft).
            int uses = cookware.hasTag() ? cookware.getOrCreateTag().getInt(NBT_USES) : 0;
            if (uses <= 0)
                uses = COOKWARE_MAX_USES;

            // Check if player is standing on a kitchen plot.
            boolean inKitchen = isInKitchen(player);

            if (inKitchen) {
                // Kitchen: return cookware with same uses (no decay).
                ItemStack returned = cookware.copy();
                returned.setCount(1);
                returned.getOrCreateTag().putInt(NBT_USES, uses);
                player.getInventory().add(returned);
                LOGGER.info("Kitchen cookware: returned {} with {} uses.", getCookwareName(cookware), uses);
            } else {
                int newUses = uses - 1;
                if (newUses > 0) {
                    // Return cookware with decremented uses.
                    ItemStack returned = cookware.copy();
                    returned.setCount(1);
                    returned.getOrCreateTag().putInt(NBT_USES, newUses);
                    player.getInventory().add(returned);

                    // Warn the player when getting low.
                    if (newUses <= 50) {
                        player.displayClientMessage(
                                Component.literal("Warning: your " + getCookwareName(cookware)
                                        + " only has " + newUses + " uses left!")
                                        .withStyle(ChatFormatting.YELLOW),
                                true);
                    }
                    LOGGER.info("Cookware used: {} now has {} uses remaining.", getCookwareName(cookware), newUses);
                } else {
                    // Uses exhausted — do not return the item.
                    player.displayClientMessage(
                            Component.literal("Your " + getCookwareName(cookware) + " broke from overuse!")
                                    .withStyle(ChatFormatting.RED),
                            true);
                    LOGGER.info("Cookware broke: {} had 0 uses left.", getCookwareName(cookware));
                }
            }
        } catch (Exception ex) {
            LOGGER.error("Error in onItemCrafted (CookwareManager): " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    /** Returns true if the given item stack is a HarvestCraft 2 cookware tool. */
    public static boolean isCookware(ItemStack stack) {
        if (stack.isEmpty())
            return false;
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (key == null)
            return false;
        return COOKWARE_IDS.contains(key.toString());
    }

    /** Returns true if the player is currently standing on a kitchen plot chunk. */
    private static boolean isInKitchen(Player player) {
        ChunkPos chunkPos = new ChunkPos(player.blockPosition());
        DataBase<Long, ChunkData> db = ModEvents.getChunkDataDatabase();
        ChunkData chunk = db.getData(chunkPos.toLong());
        return chunk != null && chunk.getType().equalsIgnoreCase("kitchen");
    }

    /** Returns a display-friendly name for a cookware item. */
    private static String getCookwareName(ItemStack stack) {
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (key == null)
            return "cookware";
        return switch (key.toString()) {
            case CUTTING_BOARD -> "cutting board";
            case GRINDER       -> "grinder";
            case JUICER        -> "juicer";
            case MIXING_BOWL   -> "mixing bowl";
            case POT           -> "pot";
            case ROLLING_PIN   -> "rolling pin";
            case SAUCEPAN      -> "saucepan";
            case SKILLET       -> "skillet";
            case BAKEWARE      -> "baking dish";
            default            -> "cookware";
        };
    }

    /**
     * Returns the remaining uses on a cookware item stack.
     * Returns {@link #COOKWARE_MAX_USES} if the item has never been used.
     */
    public static int getRemainingUses(ItemStack stack) {
        if (!stack.hasTag())
            return COOKWARE_MAX_USES;
        int uses = stack.getOrCreateTag().getInt(NBT_USES);
        return uses > 0 ? uses : COOKWARE_MAX_USES;
    }
}
