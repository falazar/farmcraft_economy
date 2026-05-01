package com.falazar.farmupcraft;

import com.falazar.farmupcraft.data.ChunkData;
import com.falazar.farmupcraft.data.PlayerData;
import com.falazar.farmupcraft.data.VillageData;
import com.falazar.farmupcraft.database.DataBase;
import com.falazar.farmupcraft.events.ModEvents;
import com.falazar.farmupcraft.util.AnimalGrainAssigner;
import com.falazar.farmupcraft.util.CustomLogger;
import com.falazar.farmupcraft.util.FUCTags;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.time.LocalDate;

import static com.falazar.farmupcraft.FarmUpCraft.MODID;

@Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class AnimalsManager {

    public static final CustomLogger LOGGER = new CustomLogger(AnimalsManager.class.getSimpleName());

    private static final int MAX_ANIMALS_PER_PASTURE = 30;
    private static final String NBT_LAST_MILKED = "farmupcraft_last_milked";
    private static final String NBT_LAST_BRED = "farmupcraft_last_bred";

    // Limit milking cows to once per real-time day.
    // Uses persistent NBT on the cow so it survives server restarts.
    @SubscribeEvent
    public static void onRightClickCow(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide())
            return;

        Entity source = event.getEntity();
        if (!(source instanceof Player player))
            return;

        try {
            ItemStack usedStack = player.getItemInHand(event.getHand());

            // Skip in creative mode.
            if (player.isCreative())
                return;

            Entity target = event.getTarget();
            if (!(target instanceof Cow cow))
                return;

            // Only apply when holding an empty bucket (vanilla milking).
            if (!usedStack.is(Items.BUCKET))
                return;

            String today = LocalDate.now().toString(); // "yyyy-MM-dd"
            String lastMilked = cow.getPersistentData().getString(NBT_LAST_MILKED);

            if (today.equals(lastMilked)) {
                cancelWithError(event, player, "This cow has already been milked today.");
                return;
            }

            // Allow the milk — record today's date on the cow.
            cow.getPersistentData().putString(NBT_LAST_MILKED, today);
            LOGGER.info("Cow {} milked by {} on {}", cow.getUUID(), player.getName().getString(), today);
        } catch (Throwable t) {
            LOGGER.error("Unhandled milking handler failure: {}", t.getMessage(), t);
            cancelWithError(event, player, "Milking failed due to an internal error.");
        }
    }

    // Limit breeding all animals to once per real-time day.
    // Fires when a player right-clicks an animal with its food item.
    @SubscribeEvent
    public static void onRightClickBreeding(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide())
            return;

        Entity source = event.getEntity();
        if (!(source instanceof Player player))
            return;

        try {
            ItemStack usedStack = player.getItemInHand(event.getHand());

            // Allow all in creative mode.
            if (player.isCreative())
                return;

            Entity target = event.getTarget();
            if (!(target instanceof Animal animal))
                return;

            if (!isFarmAnimal(animal)) {
                return;
            }
            if (!isPastureAnimal(animal)) {
                return;
            }

            // Only treat vanilla/HC2 crop-style items as breeding feed.
            // This lets interactions like sheep shearing pass through normally.
            if (!usedStack.is(FUCTags.VANILLA_CROPS)
                    && !usedStack.is(FUCTags.MODDED_CROPS)
                    && !usedStack.is(FUCTags.MODDED_SEEDS)) {
                return;
            }

            // Only enforce pasture rules for specific farmable/pasture species.
            if (enforcePastureBreedingRules(event, player, animal, usedStack))
                return;

            String today = LocalDate.now().toString(); // "yyyy-MM-dd"
            String lastBred = animal.getPersistentData().getString(NBT_LAST_BRED);
            if (today.equals(lastBred)) {
                cancelWithError(event, player, "This animal has already been bred today.");
                return;
            }

            // Trigger breeding directly so approved custom grains (e.g., alfalfa) also
            // enter love mode and show hearts.
            if (!triggerLoveMode(event, player, animal, usedStack)) {
                return;
            }
            // Allow breeding — record today's date on the animal.
            animal.getPersistentData().putString(NBT_LAST_BRED, today);
            LOGGER.info("Animal {} bred by {} on {}", animal.getUUID(), player.getName().getString(), today);
        } catch (Throwable t) {
            LOGGER.error("Unhandled breeding handler failure: {}", t.getMessage(), t);
            cancelWithError(event, player, "Breeding failed due to an internal error.");
        }
    }

    // Returns true if this animal is one of the pasture-managed species (pasture +
    // cap rules apply).
    private static boolean isPastureAnimal(Animal animal) {
        return animal instanceof Cow
                || animal instanceof Sheep
                || animal instanceof Pig
                || animal instanceof Chicken
                || animal instanceof Horse
                || animal instanceof Goat;
    }

    // Returns true if this is a farm animal (village species restriction applies).
    private static boolean isFarmAnimal(Animal animal) {
        return animal instanceof Cow
                || animal instanceof Sheep
                || animal instanceof Pig
                || animal instanceof Chicken;
    }

    // Returns the short species name matching village animalType values.
    private static String getAnimalSpeciesName(Animal animal) {
        if (animal instanceof Cow)
            return "cow";
        if (animal instanceof Sheep)
            return "sheep";
        if (animal instanceof Pig)
            return "pig";
        if (animal instanceof Chicken)
            return "chicken";

        return "unknown";
    }

    // Count all Animal entities in the chunk containing blockPos.
    private static int countAnimalsInChunk(Level level, net.minecraft.core.BlockPos pos) {
        ChunkPos chunkPos = new ChunkPos(pos);
        double minX = chunkPos.getMinBlockX();
        double minZ = chunkPos.getMinBlockZ();
        double maxX = chunkPos.getMaxBlockX() + 1;
        double maxZ = chunkPos.getMaxBlockZ() + 1;
        double minY = Math.max(level.getMinBuildHeight(), pos.getY() - 2);
        double maxY = Math.min(level.getMaxBuildHeight(), pos.getY() + 3);
        AABB chunkBox = new AABB(minX, minY, minZ, maxX, maxY, maxZ);
        return level.getEntitiesOfClass(Animal.class, chunkBox).size();
    }

    // Returns true when pasture-specific validation blocks breeding.
    private static boolean enforcePastureBreedingRules(
            PlayerInteractEvent.EntityInteract event,
            Player player,
            Animal animal,
            ItemStack usedStack) {
        Level level = event.getLevel();

        // STEP 1: Must be on a pasture plot.
        String plotType = ChunkManager.getPlotType(animal.blockPosition(), level);
        if (!"pasture".equals(plotType)) {
            return cancelWithError(event, player, "Animals can only be bred in a pasture plot.");
        }

        // STEP 2: Pasture must belong to the player's own village.
        PlayerData playerData = ModEvents.getPlayerDatabase().getData(player.getUUID());
        if (playerData == null || playerData.getHomeVillageUUID() == null) {
            return cancelWithError(event, player, "You must belong to a village to breed animals.");
        }
        ChunkPos animalChunk = new ChunkPos(animal.blockPosition());
        DataBase<Long, ChunkData> chunkDb = ModEvents.getChunkDataDatabase();
        ChunkData chunkData = chunkDb.getData(animalChunk.toLong());
        if (chunkData == null || !playerData.getHomeVillageUUID().equals(chunkData.getVillageId())) {
            return cancelWithError(event, player, "You can only breed animals in your own village's pasture.");
        }

        // STEP 3: Check animal cap in this chunk.
        int count = countAnimalsInChunk(level, animal.blockPosition());
        if (count >= MAX_ANIMALS_PER_PASTURE) {
            return cancelWithError(event, player,
                    "This pasture is full! (" + count + "/" + MAX_ANIMALS_PER_PASTURE + " animals)");
        }

        // STEP 4-5: Farm-animal-only rules.
        boolean farmAnimal = isFarmAnimal(animal);
        DataBase<UUID, VillageData> villageDb = ModEvents.getVillageDatabase();
        VillageData village = null;
        if (farmAnimal) {
            village = villageDb.getData(playerData.getHomeVillageUUID());
            if (village != null && !village.getAnimalType().isEmpty()) {
                String allowed = village.getAnimalType();
                String animalSpecies = getAnimalSpeciesName(animal);
                if (!allowed.equals(animalSpecies)) {
                    return cancelWithError(event, player,
                            "Your village only breeds " + allowed
                                    + "s. Use /village setanimal to change (once per week).");
                }
            }

            // STEP 5: Grain requirement — player must have required grains.
            String animalSpecies = getAnimalSpeciesName(animal);
            boolean blocked = enforceBreedingGrainRule(event, player, usedStack, animalSpecies, playerData, village,
                    villageDb);
            if (blocked)
                return true;
        }

        return false;
    }

    // Returns true when breeding should be blocked and event flow should stop.
    private static boolean enforceBreedingGrainRule(
            PlayerInteractEvent.EntityInteract event,
            Player player,
            ItemStack usedStack,
            String animalSpecies,
            PlayerData playerData,
            VillageData village,
            DataBase<UUID, VillageData> villageDb) {
        if (village == null)
            village = villageDb.getData(playerData.getHomeVillageUUID());
        if (village == null)
            return false;

        // Legacy-safe fallback: if grains were never initialized, initialize now but
        // allow this interaction to proceed with vanilla food (e.g., wheat).
        if (!village.hasAnimalGrainsAssigned()) {
            AnimalGrainAssigner.assignGrains(village);
            villageDb.putData(village.getUUID(), village);
            return false;
        }

        List<String> required = village.getGrainsForAnimal(animalSpecies);
        if (required.isEmpty())
            return false;

        String usedItemId = getItemId(usedStack);
        LOGGER.info("Breeding grain check: animal={} usedItem={} required={}", animalSpecies, usedItemId, required);
        if (usedItemId == null || !required.contains(usedItemId)) {
            List<String> options = toDisplayNames(required);
            return cancelWithError(event, player,
                    "To breed " + animalSpecies + "s, hold either: " + String.join(" or ", options));
        }

        // No extra consume here; vanilla breeding consumes one item from the used hand.
        return false;
    }

    private static boolean triggerLoveMode(
            PlayerInteractEvent.EntityInteract event,
            Player player,
            Animal animal,
            ItemStack usedStack) {
        if (!animal.canFallInLove()) {
            return false;
        }

        animal.setInLove(player);
        if (!player.getAbilities().instabuild) {
            usedStack.shrink(1);
        }

        // We handled breeding + consumption ourselves; avoid double-processing.
        event.setCanceled(true);
        return true;
    }

    private static String getItemId(ItemStack stack) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id == null ? null : id.toString();
    }

    private static List<String> toDisplayNames(List<String> itemIds) {
        List<String> names = new ArrayList<>();
        for (String itemId : itemIds) {
            names.add(AnimalGrainAssigner.displayName(itemId));
        }
        return names;
    }

    private static boolean cancelWithError(PlayerInteractEvent.EntityInteract event, Player player, String message) {
        event.setCanceled(true);
        player.displayClientMessage(Component.literal(message).withStyle(ChatFormatting.RED), false);
        return true;
    }
}
