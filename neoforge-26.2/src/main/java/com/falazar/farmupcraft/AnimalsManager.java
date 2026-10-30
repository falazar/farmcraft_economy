package com.falazar.farmupcraft;

import com.falazar.farmupcraft.data.ChunkData;
import com.falazar.farmupcraft.data.PlayerData;
import com.falazar.farmupcraft.data.VillageData;
import com.falazar.farmupcraft.database.DataBase;
import com.falazar.farmupcraft.events.ModEvents;
import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import com.falazar.farmupcraft.util.AnimalGrainAssigner;
import com.falazar.farmupcraft.util.CustomLogger;
import com.falazar.farmupcraft.util.FUCTags;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.animal.cow.AbstractCow;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.entity.animal.equine.Horse;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@EventBusSubscriber(modid = FarmUpCraftNeoForge.MOD_ID)
public class AnimalsManager {
    public static final CustomLogger LOGGER = new CustomLogger(AnimalsManager.class.getSimpleName());

    private static final int MAX_ANIMALS_PER_PASTURE = 30;
    private static final String NBT_LAST_MILKED = "farmupcraft_last_milked";
    private static final String NBT_LAST_BRED = "farmupcraft_last_bred";
    private static final String NBT_LAST_SHEARED_TICK = "farmupcraft_last_sheared_tick";
    private static final long WOOL_REGROWTH_COOLDOWN_TICKS = 7L * 24000L;

    @SubscribeEvent
    public static void onRightClickSheepShearing(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !(event.getTarget() instanceof Sheep sheep)) {
            return;
        }
        Player player = event.getEntity();
        if (!player.getItemInHand(event.getHand()).is(Items.SHEARS) || sheep.isBaby() || sheep.isSheared()) {
            return;
        }
        String plotType = ChunkManager.getPlotType(sheep.blockPosition(), level);
        if (!canShearInPlot(plotType)) {
            cancelWithError(event, player, "Sheep can only be sheared in a pasture plot or the wilderness.");
            return;
        }
        long gameTime = level.getGameTime();
        sheep.getPersistentData().putLong(NBT_LAST_SHEARED_TICK, gameTime);
        ChunkData chunk = ChunkManager.getPlot(sheep.blockPosition(), level);
        int pastureLevel = chunk == null ? 1 : chunk.getPlotLevel();
        int extraWool = getExtraDropsForPastureLevel(pastureLevel);
        if (extraWool > 0) {
            sheep.spawnAtLocation(level, new ItemStack(getWoolItemForColor(sheep.getColor()), extraWool));
            LOGGER.info("Sheep {} bonus wool dropped: +{} (pastureLevel={}) by {}",
                    sheep.getUUID(), extraWool, pastureLevel, player.getName().getString());
        }
        LOGGER.info("Sheep {} sheared by {} at gameTick={}", sheep.getUUID(), player.getName().getString(), gameTime);
    }

    @SubscribeEvent
    public static void onRightClickSheepDye(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !(event.getTarget() instanceof Sheep sheep)) {
            return;
        }
        Player player = event.getEntity();
        ItemStack held = player.getItemInHand(event.getHand());
        DyeColor dyeColor = DyeColor.getColor(held);
        if (dyeColor == null || !sheep.isAlive() || sheep.isSheared() || sheep.getColor() == dyeColor) {
            return;
        }
        ChunkData chunk = ChunkManager.getPlot(sheep.blockPosition(), level);
        int pastureLevel = chunk == null ? 1 : chunk.getPlotLevel();
        int successChance = dyeSuccessChance(pastureLevel);
        if (level.getRandom().nextInt(100) >= successChance) {
            event.setCanceled(true);
            player.sendOverlayMessage(Component.literal(
                    "The dye didn't take. (" + successChance + "% chance, pasture level " + pastureLevel + ")")
                    .withStyle(ChatFormatting.RED));
            if (player instanceof ServerPlayer serverPlayer) {
                held.shrink(1);
                serverPlayer.inventoryMenu.sendAllDataToRemote();
            }
        }
    }

    @SubscribeEvent
    public static void onSheepTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Sheep sheep) || sheep.level().isClientSide()
                || !sheep.getPersistentData().contains(NBT_LAST_SHEARED_TICK)) {
            return;
        }
        long lastShearedTick = sheep.getPersistentData().getLongOr(NBT_LAST_SHEARED_TICK, 0L);
        if (woolRegrowthBlocked(sheep.level().getGameTime(), lastShearedTick)) {
            sheep.setSheared(true);
        } else if (!sheep.isSheared()) {
            sheep.getPersistentData().remove(NBT_LAST_SHEARED_TICK);
        }
    }

    @SubscribeEvent
    public static void onRightClickCow(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        Player player = event.getEntity();
        try {
            if (player.isCreative() || !(event.getTarget() instanceof AbstractCow cow)
                    || cow.isBaby() || !player.getItemInHand(event.getHand()).is(Items.BUCKET)) {
                return;
            }
            if (!"pasture".equals(ChunkManager.getPlotType(cow.blockPosition(), event.getLevel()))) {
                cancelMilkingWithSync(event, player, "Cows can only be milked in a pasture plot.");
                return;
            }
            String today = LocalDate.now().toString();
            if (today.equals(cow.getPersistentData().getStringOr(NBT_LAST_MILKED, ""))) {
                cancelMilkingWithSync(event, player, "This cow has already been milked today.");
                return;
            }
            cow.getPersistentData().putString(NBT_LAST_MILKED, today);
            LOGGER.info("Cow {} milked by {} on {}", cow.getUUID(), player.getName().getString(), today);
        } catch (Exception exception) {
            LOGGER.error("Unhandled milking handler failure: {}", exception.getMessage(), exception);
            cancelMilkingWithSync(event, player, "Milking failed due to an internal error.");
        }
    }

    @SubscribeEvent
    public static void onRightClickBreeding(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        Player player = event.getEntity();
        try {
            if (player.isCreative() || !(event.getTarget() instanceof Animal animal)
                    || "unknown".equals(getAnimalSpeciesName(animal))) {
                return;
            }
            ItemStack usedStack = player.getItemInHand(event.getHand());
            if (!isBreedingFeed(usedStack) && !animal.isFood(usedStack)) {
                return;
            }
            if (enforcePastureBreedingRules(event, player, animal, usedStack)) {
                return;
            }
            String today = LocalDate.now().toString();
            if (today.equals(animal.getPersistentData().getStringOr(NBT_LAST_BRED, ""))) {
                cancelWithError(event, player, "This animal has already been bred today.");
                return;
            }
            if (!triggerLoveMode(event, player, animal, usedStack)) {
                return;
            }
            animal.getPersistentData().putString(NBT_LAST_BRED, today);
            LOGGER.info("Animal {} bred by {} on {}", animal.getUUID(), player.getName().getString(), today);
        } catch (Exception exception) {
            LOGGER.error("Unhandled breeding handler failure: {}", exception.getMessage(), exception);
            cancelWithError(event, player, "Breeding failed due to an internal error.");
        }
    }

    static boolean isPastureAnimal(Class<? extends Animal> type) {
        return isFarmAnimal(type) || Horse.class.isAssignableFrom(type) || Goat.class.isAssignableFrom(type);
    }

    static boolean isFarmAnimal(Class<? extends Animal> type) {
        return !"unknown".equals(getAnimalSpeciesName(type));
    }

    static String getAnimalSpeciesName(Animal animal) {
        if ("naturalist:duck".equals(BuiltInRegistries.ENTITY_TYPE.getKey(animal.getType()).toString())) {
            return "duck";
        }
        return getAnimalSpeciesName(animal.getClass());
    }

    static String getAnimalSpeciesName(Class<? extends Animal> type) {
        // Cow and MushroomCow share AbstractCow in 26.2, rather than MushroomCow extending Cow.
        if (AbstractCow.class.isAssignableFrom(type)) return "cow";
        if (Sheep.class.isAssignableFrom(type) || Goat.class.isAssignableFrom(type)) return "sheep";
        if (Pig.class.isAssignableFrom(type)) return "pig";
        if (Chicken.class.isAssignableFrom(type)) return "chicken";
        return "unknown";
    }

    static boolean isBreedingFeed(ItemStack stack) {
        return stack.is(FUCTags.VANILLA_CROPS) || stack.is(FUCTags.MODDED_CROPS)
                || stack.is(FUCTags.MODDED_SEEDS)
                // Legacy vanilla_crops lists wheat seeds, not wheat itself.
                || AnimalGrainAssigner.GRAIN_POOL.contains(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
    }

    /** Counts animals in the legacy five-block-high pasture band, not the entire vertical chunk. */
    public static int countAnimalsInChunk(Level level, BlockPos pos) {
        return level.getEntitiesOfClass(Animal.class, pastureCountingBox(pos, level.getMinY(), level.getMaxY())).size();
    }

    static AABB pastureCountingBox(BlockPos pos, int minY, int maxY) {
        ChunkPos chunk = ChunkPos.containing(pos);
        return new AABB(chunk.getMinBlockX(), Math.max(minY, pos.getY() - 2), chunk.getMinBlockZ(),
                chunk.getMaxBlockX() + 1, Math.min(maxY + 1, pos.getY() + 3), chunk.getMaxBlockZ() + 1);
    }

    private static boolean enforcePastureBreedingRules(PlayerInteractEvent.EntityInteract event, Player player,
                                                       Animal animal, ItemStack usedStack) {
        Level level = event.getLevel();
        if (!"pasture".equals(ChunkManager.getPlotType(animal.blockPosition(), level))) {
            return cancelWithError(event, player, "Animals can only be bred in a pasture plot.");
        }
        PlayerData playerData = ModEvents.getPlayerDatabase(level).getData(player.getUUID());
        if (playerData == null || playerData.getHomeVillageUUID() == null) {
            return cancelWithError(event, player, "You must belong to a village to breed animals.");
        }
        ChunkData chunk = ModEvents.getChunkDataDatabase(level).getData(ChunkPos.containing(animal.blockPosition()).pack());
        if (chunk == null || !playerData.getHomeVillageUUID().equals(chunk.getVillageId())) {
            return cancelWithError(event, player, "You can only breed animals in your own village's pasture.");
        }
        int count = countAnimalsInChunk(level, animal.blockPosition());
        if (count >= MAX_ANIMALS_PER_PASTURE) {
            return cancelWithError(event, player,
                    "This pasture is full! (" + count + "/" + MAX_ANIMALS_PER_PASTURE + " animals)");
        }
        DataBase<UUID, VillageData> villageDb = ModEvents.getVillageDatabase(level);
        VillageData village = villageDb.getData(playerData.getHomeVillageUUID());
        String species = getAnimalSpeciesName(animal);
        if (village != null && !village.getAnimalType().isEmpty() && !village.getAnimalType().equals(species)) {
            return cancelWithError(event, player, "Your village only breeds " + village.getAnimalType()
                    + "s. Use /village setanimal to change (once every 2 weeks).");
        }
        if ("duck".equals(species) && village != null) {
            if (AnimalGrainAssigner.ensureDuckGrain(village)) villageDb.putData(village.getUUID(), village);
            if (animal.isFood(usedStack)) return false;
        }
        return enforceBreedingGrainRule(event, player, usedStack, species, village, villageDb);
    }

    private static boolean enforceBreedingGrainRule(PlayerInteractEvent.EntityInteract event, Player player,
                                                   ItemStack usedStack, String species, VillageData village,
                                                   DataBase<UUID, VillageData> villageDb) {
        if (village == null) return false;
        // Preserve the first-feed migration fallback for existing villages.
        if (!village.hasAnimalGrainsAssigned()) {
            AnimalGrainAssigner.assignGrains(village);
            villageDb.putData(village.getUUID(), village);
            return false;
        }
        List<String> required = village.getGrainsForAnimal(species);
        if (required.isEmpty()) return false;
        String itemId = BuiltInRegistries.ITEM.getKey(usedStack.getItem()).toString();
        LOGGER.info("Breeding grain check: animal={} usedItem={} required={}", species, itemId, required);
        if (!required.contains(itemId)) {
            List<String> options = required.stream().map(AnimalGrainAssigner::displayName).toList();
            return cancelWithError(event, player,
                    "To breed " + species + "s, hold either: " + String.join(" or ", options)
                            + ("duck".equals(species) ? " or its usual Naturalist food (fish)" : ""));
        }
        return false;
    }

    private static boolean triggerLoveMode(PlayerInteractEvent.EntityInteract event, Player player,
                                            Animal animal, ItemStack usedStack) {
        // canFallInLove alone does not check age; vanilla only breeds adults at age zero.
        if (!canEnterLoveMode(animal.getAge(), animal.canFallInLove())) return false;
        animal.setInLove(player);
        if (!player.getAbilities().instabuild) usedStack.shrink(1);
        event.setCanceled(true);
        return true;
    }

    static boolean canEnterLoveMode(int age, boolean canFallInLove) {
        return age == 0 && canFallInLove;
    }

    static boolean canShearInPlot(String plotType) {
        return plotType.isEmpty() || "pasture".equals(plotType);
    }

    static int dyeSuccessChance(int pastureLevel) {
        return (int) Math.min(100L, 20L + ((long) pastureLevel - 1) * 20L);
    }

    static boolean woolRegrowthBlocked(long gameTime, long lastShearedTick) {
        return gameTime - lastShearedTick < WOOL_REGROWTH_COOLDOWN_TICKS;
    }

    private static boolean cancelWithError(PlayerInteractEvent.EntityInteract event, Player player, String message) {
        event.setCanceled(true);
        player.sendSystemMessage(Component.literal(message).withStyle(ChatFormatting.RED));
        return true;
    }

    private static void cancelMilkingWithSync(PlayerInteractEvent.EntityInteract event, Player player, String message) {
        cancelWithError(event, player, message);
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.inventoryMenu.sendAllDataToRemote();
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Animal animal)) return;
        List<WrappedGoal> toReplace = new ArrayList<>();
        for (WrappedGoal wrapped : animal.goalSelector.getAvailableGoals()) {
            if (wrapped.getGoal() instanceof TemptGoal) toReplace.add(wrapped);
        }
        for (WrappedGoal wrapped : toReplace) {
            TemptGoal original = (TemptGoal) wrapped.getGoal();
            animal.goalSelector.removeGoal(original);
            animal.goalSelector.addGoal(wrapped.getPriority(), new NoLoveTemptGoal(animal, original));
        }
    }

    private static class NoLoveTemptGoal extends Goal {
        private final Animal animal;
        private final TemptGoal delegate;

        NoLoveTemptGoal(Animal animal, TemptGoal delegate) {
            this.animal = animal;
            this.delegate = delegate;
            setFlags(delegate.getFlags());
        }

        @Override
        public boolean canUse() {
            boolean blocked = alreadyBredToday();
            // Run once: the delegate searches for and stores the nearby player.
            boolean delegateWants = delegate.canUse();
            return !blocked && delegateWants;
        }

        @Override
        public boolean canContinueToUse() {
            return !alreadyBredToday() && delegate.canContinueToUse();
        }

        private boolean alreadyBredToday() {
            return animal.isInLove() || LocalDate.now().toString()
                    .equals(animal.getPersistentData().getStringOr(NBT_LAST_BRED, ""));
        }

        @Override
        public boolean isInterruptable() { return delegate.isInterruptable(); }

        @Override
        public boolean requiresUpdateEveryTick() { return delegate.requiresUpdateEveryTick(); }

        @Override
        public void start() { delegate.start(); }

        @Override
        public void stop() { delegate.stop(); }

        @Override
        public void tick() { delegate.tick(); }
    }

    public static int getExtraDropsForPastureLevel(int pastureLevel) {
        return pastureLevel >= 2 && pastureLevel <= 4 ? pastureLevel - 1 : 0;
    }

    static Item getWoolItemForColor(DyeColor color) {
        return Items.WOOL.pick(color);
    }

    @SubscribeEvent
    public static void onPastureAnimalDrops(LivingDropsEvent event) {
        if (event.getEntity().level().isClientSide() || !(event.getEntity() instanceof Animal animal)
                || ("unknown".equals(getAnimalSpeciesName(animal)) && !isPastureAnimal(animal.getClass()))) return;
        Level level = animal.level();
        if (!"pasture".equals(ChunkManager.getPlotType(animal.blockPosition(), level))) return;
        ChunkData chunk = ChunkManager.getPlot(animal.blockPosition(), level);
        if (chunk == null) return;
        int extra = getExtraDropsForPastureLevel(chunk.getPlotLevel());
        if (extra <= 0) return;
        event.getDrops().forEach(entity -> entity.getItem().grow(extra));
    }
}
