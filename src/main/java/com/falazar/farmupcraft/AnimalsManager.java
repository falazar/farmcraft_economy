package com.falazar.farmupcraft;

import com.falazar.farmupcraft.data.ChunkData;
import com.falazar.farmupcraft.data.PlayerData;
import com.falazar.farmupcraft.data.VillageData;
import com.falazar.farmupcraft.database.DataBase;
import com.falazar.farmupcraft.events.ModEvents;
import com.falazar.farmupcraft.util.AnimalGrainAssigner;
import com.falazar.farmupcraft.util.CustomLogger;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import java.util.UUID;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
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
        if (event.getHand() != InteractionHand.MAIN_HAND)
            return;

        Entity source = event.getEntity();
        if (!(source instanceof Player player))
            return;

        // Skip in creative mode.
        if (player.isCreative())
            return;

        Entity target = event.getTarget();
        if (!(target instanceof Cow cow))
            return;

        // Only apply when holding an empty bucket (vanilla milking).
        if (!player.getMainHandItem().is(Items.BUCKET))
            return;

        String today = LocalDate.now().toString(); // "yyyy-MM-dd"
        String lastMilked = cow.getPersistentData().getString(NBT_LAST_MILKED);

        if (today.equals(lastMilked)) {
            event.setCanceled(true);
            player.displayClientMessage(
                    Component.literal("This cow has already been milked today.")
                            .withStyle(ChatFormatting.RED),
                    false);
            return;
        }

        // Allow the milk — record today's date on the cow.
        cow.getPersistentData().putString(NBT_LAST_MILKED, today);
        LOGGER.info("Cow {} milked by {} on {}", cow.getUUID(), player.getName().getString(), today);
    }

    // Limit breeding all animals to once per real-time day.
    // Fires when a player right-clicks an animal with its food item.
    @SubscribeEvent
    public static void onRightClickBreeding(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide())
            return;
        if (event.getHand() != InteractionHand.MAIN_HAND)
            return;

        Entity source = event.getEntity();
        if (!(source instanceof Player player))
            return;

        // Skip in creative mode.
        if (player.isCreative())
            return;

        Entity target = event.getTarget();
        if (!(target instanceof Animal animal))
            return;

        // Only apply when the held item is food for this animal (would trigger
        // breeding).
        if (!animal.isFood(player.getMainHandItem()))
            return;

        // Only enforce pasture rules for specific farmable/pasture species.
        if (isPastureAnimal(animal)) {
            Level level = event.getLevel();

            // STEP 1: Must be on a pasture plot.
            String plotType = ChunkManager.getPlotType(animal.blockPosition(), level);
            if (!plotType.equals("pasture")) {
                event.setCanceled(true);
                player.displayClientMessage(
                        Component.literal("Animals can only be bred in a pasture plot.")
                                .withStyle(ChatFormatting.RED),
                        false);
                return;
            }

            // STEP 2: Pasture must belong to the player's own village.
            PlayerData playerData = ModEvents.getPlayerDatabase().getData(player.getUUID());
            if (playerData == null || playerData.getHomeVillageUUID() == null) {
                event.setCanceled(true);
                player.displayClientMessage(
                        Component.literal("You must belong to a village to breed animals.")
                                .withStyle(ChatFormatting.RED),
                        false);
                return;
            }
            ChunkPos animalChunk = new ChunkPos(animal.blockPosition());
            DataBase<Long, ChunkData> chunkDb = ModEvents.getChunkDataDatabase();
            ChunkData chunkData = chunkDb.getData(animalChunk.toLong());
            if (chunkData == null || !playerData.getHomeVillageUUID().equals(chunkData.getVillageId())) {
                event.setCanceled(true);
                player.displayClientMessage(
                        Component.literal("You can only breed animals in your own village's pasture.")
                                .withStyle(ChatFormatting.RED),
                        false);
                return;
            }

            // STEP 3: Check animal cap in this chunk.
            int count = countAnimalsInChunk(level, animal.blockPosition());
            if (count >= MAX_ANIMALS_PER_PASTURE) {
                event.setCanceled(true);
                player.displayClientMessage(
                        Component
                                .literal(
                                        "This pasture is full! (" + count + "/" + MAX_ANIMALS_PER_PASTURE + " animals)")
                                .withStyle(ChatFormatting.RED),
                        false);
                return;
            }

            // STEP 4: For farm animals only, enforce the village species restriction.
            DataBase<UUID, VillageData> villageDb = ModEvents.getVillageDatabase();
            VillageData village = null;
            if (isFarmAnimal(animal)) {
                village = villageDb.getData(playerData.getHomeVillageUUID());
                if (village != null && !village.getAnimalType().isEmpty()) {
                    String allowed = village.getAnimalType();
                    String animalSpecies = getAnimalSpeciesName(animal);
                    if (!allowed.equals(animalSpecies)) {
                        event.setCanceled(true);
                        player.displayClientMessage(
                                Component
                                        .literal("Your village only breeds " + allowed
                                                + "s. Use /village setanimal to change (once per week).")
                                        .withStyle(ChatFormatting.RED),
                                false);
                        return;
                    }
                }
            }

            // STEP 5: Grain requirement — player must have both required grains in inventory.
            if (isFarmAnimal(animal)) {
                if (village == null) village = villageDb.getData(playerData.getHomeVillageUUID());
                if (village != null) {
                    // Assign grains on first use if not yet set.
                    if (!village.hasAnimalGrainsAssigned()) {
                        AnimalGrainAssigner.assignGrains(village);
                        villageDb.putData(village.getUUID(), village);
                    }
                    String animalSpecies = getAnimalSpeciesName(animal);
                    java.util.List<String> required = village.getGrainsForAnimal(animalSpecies);
                    if (!required.isEmpty()) {
                        java.util.List<String> missing = new java.util.ArrayList<>();
                        for (String itemId : required) {
                            net.minecraft.world.item.Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(itemId));
                            if (item == null || !player.getInventory().hasAnyOf(java.util.Set.of(item))) {
                                missing.add(AnimalGrainAssigner.displayName(itemId));
                            }
                        }
                        if (!missing.isEmpty()) {
                            event.setCanceled(true);
                            player.displayClientMessage(
                                    Component.literal("To breed " + animalSpecies + "s you need: "
                                            + String.join(" & ", missing) + " in your inventory.")
                                            .withStyle(ChatFormatting.RED),
                                    false);
                            return;
                        }
                        // Consume one of each required grain.
                        for (String itemId : required) {
                            net.minecraft.world.item.Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(itemId));
                            if (item != null) {
                                player.getInventory().clearOrCountMatchingItems(
                                        stack -> stack.is(item), 1, player.inventoryMenu.getCraftSlots());
                            }
                        }
                    }
                }
            }
        }

        String today = LocalDate.now().toString(); // "yyyy-MM-dd"
        String lastBred = animal.getPersistentData().getString(NBT_LAST_BRED);

        if (today.equals(lastBred)) {
            event.setCanceled(true);
            player.displayClientMessage(
                    Component.literal("This animal has already been bred today.")
                            .withStyle(ChatFormatting.RED),
                    false);
            return;
        }

        // Allow breeding — record today's date on the animal.
        animal.getPersistentData().putString(NBT_LAST_BRED, today);
        LOGGER.info("Animal {} bred by {} on {}", animal.getUUID(), player.getName().getString(), today);
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
        AABB chunkBox = new AABB(minX, level.getMinBuildHeight(), minZ, maxX, level.getMaxBuildHeight(), maxZ);
        return level.getEntitiesOfClass(Animal.class, chunkBox).size();
    }
}
