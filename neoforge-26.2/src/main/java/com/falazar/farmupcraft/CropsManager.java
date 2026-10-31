package com.falazar.farmupcraft;

import com.falazar.farmupcraft.command.ShowBiomesCommand;
import com.falazar.farmupcraft.data.ChunkData;
import com.falazar.farmupcraft.data.CropBlockData;
import com.falazar.farmupcraft.data.CropBlockDataJsonManager;
import com.falazar.farmupcraft.data.PlayerData;
import com.falazar.farmupcraft.events.ModEvents;
import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import com.falazar.farmupcraft.saveddata.BiomeRulesInstance;
import com.falazar.farmupcraft.saveddata.BiomeRulesManager;
import com.falazar.farmupcraft.util.AsyncLocator;
import com.falazar.farmupcraft.util.CustomLogger;
import com.falazar.farmupcraft.util.FUCTags;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.block.CropGrowEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.*;

@EventBusSubscriber(modid = FarmUpCraftNeoForge.MOD_ID)
public final class CropsManager {
    public static final CustomLogger LOGGER = new CustomLogger(CropsManager.class.getSimpleName());

    @SubscribeEvent
    public static void onRightClickPlanting(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getLevel() instanceof ServerLevel level) || event.getEntity().isCreative()) return;
        Player player = event.getEntity();
        BlockPos pos = event.getPos();
        var state = level.getBlockState(pos);
        ItemStack stack = event.getItemStack();
        if (state.getBlock() instanceof BaseEntityBlock || state.is(BlockTags.DOORS)
                || state.is(BlockTags.TRAPDOORS) || state.is(BlockTags.FENCE_GATES)) return;

        if (stack.is(Items.SUGAR_CANE) || stack.is(Items.SWEET_BERRIES)) {
            if (ModEvents.getWorldData(level).isUseVillageFarms()
                    && !ChunkManager.getPlotType(pos, level).equals("farm")) {
                reject(event, Component.literal(
                        "Sugar cane and sweet berries can only be planted on farm plots. Use /plot buy farm to purchase one.")
                        .withStyle(ChatFormatting.RED));
            }
            return;
        }
        boolean farmlandBelow = event.getFace() != null
                && level.getBlockState(pos.relative(event.getFace()).below()).is(FUCTags.FARMLAND);
        if (!state.is(FUCTags.FARMLAND) && !farmlandBelow) return;
        if (!state.is(FUCTags.FARMLAND)) pos = pos.relative(event.getFace()).below();
        if (!stack.is(FUCTags.MODDED_CROPS) && !stack.is(FUCTags.VANILLA_CROPS)
                && !stack.is(FUCTags.MODDED_SEEDS)) return;
        if (!ModEvents.getWorldData(level).isUseBiomeCropRules()) return;
        BiomeRulesManager manager = BiomeRulesManager.get(level);
        if (!manager.hasRules()) return;
        Holder<Biome> biome = level.getBiome(pos);
        BiomeRulesInstance instance = manager.getBiomeRules(biome);
        if (instance == null) return;
        ChunkData plot = ChunkManager.getPlot(pos, level);
        int plotLevel = plot == null ? 1 : plot.getPlotLevel();
        if (plotLevel >= 3 && isCropAllowedInAnyChunkBiome(manager, stack, pos, level)) return;
        if (!isCropAllowed(manager, instance, stack, biome, event)) {
            cancelPredictedPlacement(event);
        }
    }

    @SubscribeEvent
    public static void onRightClickHoeing(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getLevel() instanceof ServerLevel level) || event.getEntity().isCreative()
                || !event.getItemStack().is(ItemTags.HOES)) return;
        var state = level.getBlockState(event.getPos());
        if (!state.is(Blocks.DIRT) && !state.is(Blocks.GRASS_BLOCK) && !state.is(Blocks.PODZOL)
                && !state.is(Blocks.COARSE_DIRT)) return;
        if (ModEvents.getWorldData(level).isUseVillageFarms()
                && !ChunkManager.getPlotType(event.getPos(), level).equals("farm")) {
            reject(event, Component.literal(
                    "You can only hoe on farm plots.  Use /plot buy command to buy plots in your village. "));
        }
    }

    private static void reject(PlayerInteractEvent.RightClickBlock event, Component message) {
        event.getEntity().sendSystemMessage(message);
        cancelPredictedPlacement(event);
    }

    private static void cancelPredictedPlacement(PlayerInteractEvent.RightClickBlock event) {
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
        if (event.getEntity() instanceof ServerPlayer player) {
            player.connection.send(new ClientboundBlockUpdatePacket(event.getLevel(), event.getPos()));
            if (event.getFace() != null) {
                player.connection.send(new ClientboundBlockUpdatePacket(event.getLevel(),
                        event.getPos().relative(event.getFace())));
            }
            player.inventoryMenu.sendAllDataToRemote();
        }
    }

    @SubscribeEvent
    public static void onRightClickHarvestCrops(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getLevel() instanceof ServerLevel level) || event.getHand() != InteractionHand.MAIN_HAND
                || event.getEntity().isCreative() || event.getItemStack().is(Items.BONE_MEAL)) return;
        BlockPos pos = event.getPos();
        var state = level.getBlockState(pos);
        Identifier blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (!blockId.getNamespace().equals("pamhc2crops") || !(state.getBlock() instanceof CropBlock crop)
                || !crop.isMaxAge(state) || !ChunkManager.getPlotType(pos, level).equalsIgnoreCase("farm")) return;
        ChunkData plot = ChunkManager.getPlot(pos, level);
        int chance = harvestBonusChance(plot == null ? 1 : plot.getPlotLevel());
        if (chance == 0) return;
        Item item = harvestItem(blockId);
        if (item != Items.AIR && level.getRandom().nextInt(100) < chance) {
            event.getEntity().addItem(new ItemStack(item));
        }
    }

    static int harvestBonusChance(int plotLevel) {
        return plotLevel <= 1 ? 0 : (plotLevel - 1) * 20;
    }

    static Item harvestItem(Identifier blockId) {
        String path = blockId.getPath();
        if (path.startsWith("pam")) path = path.substring(3);
        if (path.endsWith("crop")) path = path.substring(0, path.length() - 4);
        return BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(blockId.getNamespace(), path + "item"));
    }

    @SubscribeEvent
    public static void slowCropsEvent(CropGrowEvent.Pre event) {
        CropBlockData data = CropBlockDataJsonManager.getCropBlockDataEntries().get(event.getState().getBlock());
        if (data != null && !growthAllowed(data.getGrowthSuccesRate(),
                ((ServerLevel) event.getLevel()).getRandom().nextInt(100))) {
            event.setResult(CropGrowEvent.Pre.Result.DO_NOT_GROW);
        }
    }

    static boolean growthAllowed(double successRate, int roll) {
        return roll < (int) successRate;
    }

    public static void findNearestVillage(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !level.getServer().getWorldGenSettings().options().generateStructures()) return;
        var structures = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).get(StructureTags.VILLAGE);
        if (structures.isEmpty()) return;
        var task = AsyncLocator.locate(level, structures.get(), event.getPos(), 100, true);
        task.completableFuture().whenComplete((result, failure) -> {
            if (failure != null && !(failure instanceof java.util.concurrent.CancellationException)) {
                LOGGER.error("Village location failed", failure);
            }
        });
        task.thenOnServerThread(result -> {
            if (result == null) return;
            LOGGER.info("Nearest village: {} at {}", result.getSecond(), result.getFirst());
            level.structureManager().startsForStructure(ChunkPos.containing(result.getFirst()), structure -> true)
                    .forEach(start -> LOGGER.info("Village bounding box: {}", start.getBoundingBox()));
        });
    }

    /** Samples all block columns at farmland height, including mixed-biome chunk edges. */
    public static Set<Holder<Biome>> getDistinctChunkBiomes(BlockPos pos, ServerLevel level) {
        ChunkPos chunk = ChunkPos.containing(pos);
        Set<Holder<Biome>> biomes = new LinkedHashSet<>();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                biomes.add(level.getBiome(new BlockPos(chunk.x() * 16 + x, pos.getY(), chunk.z() * 16 + z)));
            }
        }
        return biomes;
    }

    private static boolean isCropAllowedInAnyChunkBiome(BiomeRulesManager manager, ItemStack stack,
                                                       BlockPos pos, ServerLevel level) {
        return isCropAllowedInBiomes(manager, stack, getDistinctChunkBiomes(pos, level));
    }

    static boolean isCropAllowedInBiomes(BiomeRulesManager manager, ItemStack stack, Collection<Holder<Biome>> biomes) {
        return biomes.stream().anyMatch(biome -> {
            BiomeRulesInstance rules = manager.getBiomeRules(biome);
            return rules != null && rules.biomeHasCrops(stack);
        });
    }

    public static boolean isCropAllowed(BiomeRulesManager manager, BiomeRulesInstance instance, ItemStack stack,
                                        Holder<Biome> biome, PlayerInteractEvent event) {
        if (instance.biomeHasCrops(stack)) return true;
        if (!(event.getLevel() instanceof ServerLevel level)) return false;
        String cropName = stack.getHoverName().getString();
        Component biomeName = Component.translatable(getBiomeLangKey(biome.unwrapKey()
                .map(key -> key.identifier()).orElse(Identifier.withDefaultNamespace("unknown"))))
                .withStyle(ChatFormatting.AQUA);
        Player player = event.getEntity();
        player.sendSystemMessage(Component.literal("§eYou cannot plant " + cropName + " in ").append(biomeName));
        Component crops = com.falazar.farmupcraft.util.CropListChat.crops(instance.getCrops(level), player);
        player.sendSystemMessage(Component.literal("§aCrops you can plant in ").append(biomeName)
                .append(": §2").append(crops));
        if (manager.hasItems()) {
            player.sendSystemMessage(Component.literal("Biomes you can plant " + cropName + " in §3")
                    .append(ShowBiomesCommand.getBiomeCropsChat(player, manager, stack.getItem())));
        }
        return false;
    }

    public static String getBiomeLangKey(Identifier location) {
        return "biome." + location.getNamespace() + "." + location.getPath();
    }

    public static String cropDisplayName(Item item) {
        String key = item.getDescriptionId();
        if (key.contains("sesameseedsseeditem")) return "Sesame";
        if (key.contains("mustardseedsseeditem")) return "Mustard";
        if (key.contains("sesameseedsitem")) return "Sesame Seeds";
        if (key.contains("mustardseedsitem")) return "Mustard Seeds";
        if (key.contains("wheat_seeds")) return "Wheat";
        return Component.translatable(key).getString();
    }

    @SubscribeEvent
    public static void hunger(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (!(player.level() instanceof ServerLevel level) || player.isCreative()) return;
        var random = level.getRandom();
        if (random.nextInt(500) == 1) player.getFoodData().addExhaustion(0.5F);
        int regenInterval = regenerationCheckInterval(player.experienceLevel);
        if (random.nextInt(regenInterval) == 1 && player.hasEffect(MobEffects.REGENERATION)) {
            player.removeEffect(MobEffects.REGENERATION);
            player.sendSystemMessage(Component.literal("Your regeneration has faded."));
        }
    }

    static int regenerationCheckInterval(int experienceLevel) {
        return (int) Math.min(Integer.MAX_VALUE, 11000L + Math.max(0, experienceLevel) * 1000L);
    }

    public static List<String> getAllFoods(ServerLevel level, long worldSeed) {
        List<String> foods = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            Identifier id = BuiltInRegistries.ITEM.getKey(item);
            if ((id.getNamespace().equals("pamhc2foodcore") || id.getNamespace().equals("pamhc2foodextended"))
                    && id.getPath().contains("item") && item.components().has(DataComponents.FOOD)) {
                foods.add(item.getDescriptionId());
            }
        }
        Collections.shuffle(foods, new Random(worldSeed));
        List<String> selected = new ArrayList<>(foods.subList(0, Math.min(20, foods.size())));
        Collections.sort(selected);
        selected.removeIf(name -> getIngredients(level, name.replaceFirst("^item\\.", "").replace(".", ":"))
                .stream().anyMatch(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().equals("pamhc2trees")));
        return new ArrayList<>(selected.subList(0, Math.min(15, selected.size())));
    }

    public static List<ItemStack> getIngredients(ServerLevel level, String itemResourceName) {
        return getIngredients(level.getServer().getRecipeManager(), SlotDisplayContext.fromLevel(level), itemResourceName);
    }

    static List<ItemStack> getIngredients(RecipeManager recipes, ContextMap context, String itemResourceName) {
        Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(itemResourceName));
        if (item == Items.AIR) return List.of();
        return recipes.recipeMap().byType(RecipeType.CRAFTING).stream()
                .map(holder -> holder.value())
                .filter(recipe -> recipe.display().stream()
                        .anyMatch(display -> display.result().resolveForStacks(context).stream().anyMatch(stack -> stack.is(item))))
                .findFirst()
                .map(recipe -> recipe.placementInfo().ingredients().stream()
                        .flatMap(ingredient -> ingredient.items().findFirst().stream())
                        .map(holder -> new ItemStack(holder.value())).toList())
                .orElse(List.of());
    }

    @SubscribeEvent
    public static void onFarmlandTrample(BlockEvent.FarmlandTrampleEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        ChunkData plot = ModEvents.getChunkDataDatabase(level).getData(ChunkPos.containing(event.getPos()).pack());
        if (plot == null || (!plot.getType().equalsIgnoreCase("farm")
                && !plot.getType().equalsIgnoreCase("village center"))) return;
        if (!(event.getEntity() instanceof Player player)) {
            event.setCanceled(true);
        } else if (!player.isCreative()) {
            PlayerData data = ModEvents.getPlayerDatabase(level).getData(player.getUUID());
            boolean member = isVillageMember(plot, data);
            if (!member || plot.getPlotLevel() >= 2) {
                event.setCanceled(true);
                player.sendOverlayMessage(Component.literal(member ? "Protected farm — watch your step!"
                                : "You can't trample crops here — not a village member.")
                        .withStyle(member ? ChatFormatting.YELLOW : ChatFormatting.RED));
            }
        }
    }

    static boolean isVillageMember(ChunkData plot, PlayerData player) {
        return plot.getVillageId() == null
                || (player != null && plot.getVillageId().equals(player.getHomeVillageUUID()));
    }

    public static void onMiddleClick(net.neoforged.neoforge.client.event.InputEvent.MouseButton.Pre event) {
        ClientEvents.onMiddleClick(event);
    }

    @EventBusSubscriber(modid = FarmUpCraftNeoForge.MOD_ID, value = Dist.CLIENT)
    public static final class ClientEvents {
        private ClientEvents() {}

        @SubscribeEvent
        public static void onMiddleClick(net.neoforged.neoforge.client.event.InputEvent.MouseButton.Pre event) {
            if (event.getButton() != 2 || event.getAction() != 1) return;
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.player == null || mc.level == null || mc.gameMode == null
                    || !(mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit)) return;
            BlockPos pos = hit.getBlockPos();
            var state = mc.level.getBlockState(pos);
            ItemStack seed = state.getCloneItemStack(pos, mc.level, false, mc.player);
            if (seed.isEmpty() || !seed.is(FUCTags.VANILLA_AND_MODDED_CROPS)) return;
            Item harvest = harvestItem(BuiltInRegistries.BLOCK.getKey(state.getBlock()));
            ItemStack target = harvest == Items.AIR ? seed : new ItemStack(harvest);
            mc.player.sendSystemMessage(Component.literal("Seed: ").withStyle(ChatFormatting.GOLD)
                    .append(seed.getDisplayName().copy().withStyle(ChatFormatting.WHITE)));
            pickItemFromInventory(mc.player.getInventory(), target);
            event.setCanceled(true);
        }

        private static void pickItemFromInventory(net.minecraft.world.entity.player.Inventory inventory, ItemStack target) {
            int slot = inventory.findSlotMatchingItem(target);
            if (slot < 0 || slot >= 36) return;
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.player == null || mc.gameMode == null || mc.getConnection() == null) return;
            if (slot < 9) {
                inventory.setSelectedSlot(slot);
                mc.getConnection().send(new net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket(slot));
            } else if (mc.player.containerMenu == mc.player.inventoryMenu) {
                // The old pick-slot packet was removed. Use the authoritative inventory-menu hotbar swap.
                mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId, slot,
                        inventory.getSelectedSlot(), net.minecraft.world.inventory.ContainerInput.SWAP, mc.player);
            }
        }
    }
}
