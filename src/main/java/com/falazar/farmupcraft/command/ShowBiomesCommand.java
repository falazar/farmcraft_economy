package com.falazar.farmupcraft.command;

import com.falazar.farmupcraft.data.PlayerData;
import com.falazar.farmupcraft.data.VillageData;
import com.falazar.farmupcraft.database.DataBase;
import com.falazar.farmupcraft.events.ModEvents;
import com.falazar.farmupcraft.saveddata.BiomeRulesInstance;
import com.falazar.farmupcraft.saveddata.BiomeRulesManager;
import com.falazar.farmupcraft.util.CustomLogger;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static com.falazar.farmupcraft.CropsManager.getBiomeLangKey;
import static com.falazar.farmupcraft.command.VillageCommand.getVillageBiomes;

public class ShowBiomesCommand {
    public static final CustomLogger LOGGER = new CustomLogger(ShowBiomesCommand.class.getSimpleName());

    public static void register(CommandDispatcher<CommandSourceStack> pDispatcher) {
        // Define the base command "show"
        LiteralArgumentBuilder<CommandSourceStack> builder = Commands.literal("show")
                .requires(s -> s.hasPermission(0));  // Adjust permission as needed

        // Define the "biomes" sub-command with "pos" and "radius" arguments
        LiteralArgumentBuilder<CommandSourceStack> biomesBuilder = Commands.literal("biomes")
                .then(Commands.argument("pos", Vec3Argument.vec3())
                        .then(Commands.argument("radius", IntegerArgumentType.integer())
                                .executes(c -> showBiomes(c, Vec3Argument.getVec3(c, "pos"), IntegerArgumentType.getInteger(c, "radius")))
                        )
                )
                .requires(s -> s.hasPermission(2));  // Adjust permission as needed
        // Add the "biomes" sub-command to the "show" command
        builder.then(biomesBuilder);

        // show cropbiomes tomato
        // Define the "cropbiomes" sub-command with crop name argument
        LiteralArgumentBuilder<CommandSourceStack> cropbiomesBuilder = Commands.literal("cropbiomes")
                .then(Commands.argument("crop", StringArgumentType.string())
                        .executes(c -> {
                            // Handle the cropbiomes command here
                            String cropName = StringArgumentType.getString(c, "crop");
                            // Implement your logic for showing crop biomes
                            showCropBiomes(c.getSource(), cropName);
                            return 0;
                        })
                );
        // Add the "cropbiomes" sub-command to the "show" command
        builder.then(cropbiomesBuilder);

        // Define the "biomecrops" sub-command with biome name argument.
        LiteralArgumentBuilder<CommandSourceStack> biomecropsBuilder = Commands.literal("biomecrops")
                .then(Commands.argument("biome", StringArgumentType.string())
                        .executes(c -> {
                            // Handle the biomecrops command here
                            String biomeName = StringArgumentType.getString(c, "biome");
                            // Implement your logic for showing biome crops
                            showBiomeCrops(c.getSource(), biomeName);
                            return 0;
                        })
                );
        // Add the "biomecrops" sub-command to the "show" command
        builder.then(biomecropsBuilder);

        // Register the main "show" command with the dispatcher
        pDispatcher.register(builder);
    }

    // Given a crop name show all biomes you can plant this in.
    public static void showCropBiomes(CommandSourceStack source, String cropName) {
        // Implement your logic for showing crop biomes here
        Player playerSource = source.getPlayer();

        LOGGER.info("DEBUG: cropName is " + cropName);

        // STEP 2: Load biome rules. The biome has rules defined for what can happen in it or not!
        BiomeRulesManager manager = BiomeRulesManager.get(source.getLevel());
        if (manager == null || !manager.hasRules()) return;

        // STEP 2: Get the crop item from the name from main item thing registry.
        // DEBUG grab from minecraft or pams for now, whichever exists.
        String cropNameWithMod = "pamhc2crops:" + cropName + "item";
        LOGGER.info("DEBUG: cropNameWithMod is " + cropNameWithMod);
        Item cropItem = ForgeRegistries.ITEMS.getValue(new ResourceLocation(cropNameWithMod));
        LOGGER.info("DEBUG: cropItem is " + cropItem);
        if (cropItem == null || cropItem.toString().trim().equals("air")) {
            // Try to get the item from the Minecraft registry
            // carrot potato, careful with wheat and beet cuz seeds.
            if (cropName.equals("wheat")) {
                cropNameWithMod = "minecraft:wheat_seeds";
            } else if (cropName.equals("beetroot")) {
                cropNameWithMod = "minecraft:beetroot_seeds";
            } else {
                cropNameWithMod = "minecraft:" + cropName;
            }
            LOGGER.info("DEBUG: cropNameWithMod is " + cropNameWithMod);
            cropItem = ForgeRegistries.ITEMS.getValue(new ResourceLocation(cropNameWithMod));
            LOGGER.info("DEBUG: cropItem is " + cropItem);
        }

        if (!manager.hasItems()) {
            LOGGER.info("DEBUG: No items found in manager.");
            return;
        }
        // Get the translated biome names, sort them, ensure they are unique, and combine them into a single component
//        Component biomesListShow = manager.getBiomesForItem(cropItem).stream()
//                .map(b -> Component.translatable(getBiomeLangKey(b.unwrapKey().get().location())).withStyle(ChatFormatting.AQUA))
//                .map(Component::getString) // Convert to plain text for uniqueness check
//                .distinct() // Ensure each biome is unique
//                .sorted() // Sort the biomes alphabetically
//                .map(name -> Component.literal(name)) // Convert back to Component
//                .reduce((comp1, comp2) -> comp1.append(", ").append(comp2))
//                .orElse(Component.literal("None"));

        // TODO MAKE METHOD - remove other one use  here... from cropsmanager.
        Component biomesListShow = getBiomeCropsChat(playerSource, manager, cropItem);

        // Create the final message component
        // TODO chat Code here can change colors easily for us, use in other areas.
        MutableComponent component = Component.literal("§bBiomes you can plant " + cropName + " in §3").append(biomesListShow);
        // TODO highlight in green any that are in this farm, yellow if in this town.
        // TODO highlight in green any that are in this farm, yellow if in this town.
        // TODO highlight in green any that are in this farm, yellow if in this town.
        // TODO highlight in green any that are in this farm, yellow if in this town.
        // TODO highlight in green any that are in this farm, yellow if in this town.
        // TODO highlight in green any that are in this farm, yellow if in this town.
        // TODO highlight in green any that are in this farm, yellow if in this town.
        // TODO highlight in green any that are in this farm, yellow if in this town.

        playerSource.displayClientMessage(component, false);
    }

    public static @NotNull Component getBiomeCropsChat(Player playerSource, BiomeRulesManager manager, Item cropItem) {
        // STEP 1: Get the village data for the player.
        DataBase<UUID, VillageData> villageDataDB = ModEvents.getVillageDatabase();
        PlayerData playerData = ModEvents.getPlayerDatabase(playerSource.level()).getData(playerSource.getUUID());
        VillageData villageData = villageDataDB.getData(playerData.getHomeVillageUUID());

        // STEP 2: Get the list of biomes in the village.
        Map<String, Integer> biomes = getVillageBiomes(villageData);
        // Map to a single set of biomes.
        Set<String> biomeSet = new HashSet<>();
        for (String biomeString : biomes.keySet()) {
            biomeSet.add(biomeString);
        }

        // STEP 3: Get the biomes for the crop item and create components for each biome.
        List<MutableComponent> biomeComponents = manager.getBiomesForItem(cropItem).stream()
                .map(b -> {
                    String biomeName = getBiomeLangKey(b.unwrapKey().get().location());
//                        LOGGER.info("DEBUG3 comparing to biomeSet biomeName=" + biomeName + "*");
                    String biomeName2 = biomeName.replace("biome.", ""); // Remove the "biome." prefix
                    biomeName2 = biomeName2.replace(".", ":");
//                        LOGGER.info("DEBUG3 comparing to biomeSet biomeName=" + biomeName + "*");

                    ChatFormatting color = biomeSet.contains(biomeName2) ? ChatFormatting.GREEN : ChatFormatting.AQUA;
                    return Component.translatable(biomeName).withStyle(color);
                })
                .distinct() // Ensure each biome is unique
                .sorted(Comparator.comparing(Component::getString)) // Sort alphabetically
                .toList();

        // Step 4: Combine the components into a single component
        Component biomesListShow = biomeComponents.stream()
                .reduce((comp1, comp2) -> comp1.append(", ").append(comp2))
                .orElse(Component.literal("None"));
        return biomesListShow;
    }

    // Given a biome name show all crops you can plant in this biome.
    public static void showBiomeCrops(CommandSourceStack source, String biomeName) {
        // Implement your logic for showing biome crops here
        Player playerSource = source.getPlayer();

        LOGGER.info("DEBUG: biomeName is " + biomeName);

        // MAKE all a method, too complicated!
        // STEP 1: Load biome rules. The biome has rules defined for what can happen in it or not!
        BiomeRulesManager manager = BiomeRulesManager.get(source.getLevel());
        if (manager == null || !manager.hasRules()) return;

        // TODO do i need plains or whole name minecraft:plains or biomesoplenty:XXXXX
        // STEP 2: Get the biome from the name from main item thing registry.
        String fullBiomeName = "minecraft:" + biomeName;
        ResourceLocation biomeResLoc = new ResourceLocation(fullBiomeName);
        Biome biome = source.getLevel().registryAccess().registryOrThrow(Registries.BIOME).get(biomeResLoc);
        // biome not found orchard.
        // plains crops none.  both none now.
        if (biome == null) {
            // Second guess with a biomesoplenty biome name.
            fullBiomeName = "biomesoplenty:" + biomeName;
            biomeResLoc = new ResourceLocation(fullBiomeName);
            biome = source.getLevel().registryAccess().registryOrThrow(Registries.BIOME).get(biomeResLoc);

            if (biome == null) {
                playerSource.displayClientMessage(Component.literal("Biome not found: " + biomeName), false);
                return;
            }
        }
        LOGGER.info("DEBUG1: biome is " + biome);
        Holder<Biome> biomeHolder = Holder.direct(biome);
        BiomeRulesInstance instance = manager.getBiomeRules(biomeHolder);
        if (instance == null) return;
        LOGGER.info("DEBUG2: instance is " + instance);

        // STEP 3: List the crops allowed in the current biome
        Component cropsAllowedShow = instance.getCrops((ServerLevel) source.getLevel()).stream()
                // Map item to a custom string for special cases and then translate
                .map(item -> {
                    String locationString = item.getDescriptionId();
                    String translatedName = Component.translatable(locationString).getString();

                    // Handle special cases where we want to avoid filtering out specific seeds
                    if (locationString.contains("sesameseedsseeditem")) {
                        translatedName = "Sesame";
                    } else if (locationString.contains("mustardseedsseeditem")) {
                        translatedName = "Mustard";
                    } else if (locationString.contains("sesameseedsitem")) {
                        translatedName = "Sesame Seeds"; // Same display name to keep only one of them
                    } else if (locationString.contains("mustardseedsitem")) {
                        translatedName = "Mustard Seeds"; // Same display name to keep only one of them
                    } else if (locationString.contains("wheat_seeds")) {
                        translatedName = "Wheat"; // Same display name to keep only one of them
                    }

                    return translatedName; // Return the adjusted or original translated name
                })
                // Filter out the remaining names that still include "Seed" or "Seeds" but not the special cases
                .filter(translatedName -> !translatedName.toLowerCase().contains("seed"))
                // Sort the remaining names alphabetically
                .sorted()
                .distinct() // Ensure each name is unique
                // Map the filtered names back to Component
                .map(Component::literal)
                // Join the names with commas
                .reduce((comp1, comp2) -> comp1.append(", ").append(comp2))
                .orElse(Component.literal("None"));

        // Create the final message component
        MutableComponent component = Component.literal("§bCrops you can plant in " + biomeName + " §3").append(cropsAllowedShow);
        playerSource.displayClientMessage(component, false);
    }

    // Show biomes from a block radius from standing point.
    public static int showBiomes(CommandContext<CommandSourceStack> c, Vec3 pos, int radius) {
        try {
            Entity nullableSummoner = c.getSource().getEntity();
            Player summoner = nullableSummoner instanceof Player ? (Player) nullableSummoner : c.getSource().getLevel().getNearestPlayer(pos.x(), pos.y(), pos.z(), 64, false);
            if (summoner != null) {
                Level level = summoner.level();
                Set<ResourceLocation> biomesInRadius = new HashSet<>();

                // Calculate the chunk boundaries based on the radius
                int chunkRadius = (int) Math.ceil(radius / 16.0);
                ChunkPos centerChunkPos = new ChunkPos(BlockPos.containing(pos));

                // Iterate over chunks within the radius
                for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
                    for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                        ChunkPos chunkPos = new ChunkPos(centerChunkPos.x + dx, centerChunkPos.z + dz);
                        ChunkAccess chunk = level.getChunk(chunkPos.x, chunkPos.z);

                        // Check all block positions in the chunk within the radius
                        for (int x = 0; x < 16; x++) {
                            for (int z = 0; z < 16; z++) {
                                BlockPos blockPos = chunk.getPos().getBlockAt(x, 0, z);
                                double distance = blockPos.distToCenterSqr(pos.x, 0, pos.z);

                                // Only consider blocks within the radius
                                if (distance <= radius * radius) {
                                    Biome biome = level.getBiome(blockPos).value();
                                    ResourceLocation biomeName = level.registryAccess().registryOrThrow(Registries.BIOME).getKey(biome);
                                    if (biomeName != null) {
                                        biomesInRadius.add(biomeName);
                                    }
                                }
                            }
                        }
                    }
                }

                // Build a response message
                if (!biomesInRadius.isEmpty()) {
                    MutableComponent response = Component.literal("Biomes within radius " + radius + ": ");
                    for (ResourceLocation biome : biomesInRadius) {
                        // Remove mod tag, dont need really.  With regex all before the ":"
                        String biomeName = biome.toString().replaceAll("^[^:]+:", "");
                        response = response.append(Component.literal(biomeName + ", "));
                    }
                    MutableComponent finalResponse = response;
                    c.getSource().sendSuccess(() -> finalResponse, false);
                } else {
                    c.getSource().sendFailure(Component.literal("No biomes found within the given radius."));
                }
            } else {
                c.getSource().sendFailure(Component.literal("Player not found within 64 blocks."));
            }
        } catch (Exception ex) {
            c.getSource().sendFailure(Component.literal("Exception thrown - see log"));
            ex.printStackTrace();
        }
        return 0;
    }
}
