package com.falazar.farmupcraft.command;

import com.falazar.farmupcraft.currency.Coin;
import com.falazar.farmupcraft.data.ChunkData;
import com.falazar.farmupcraft.data.GameStructureData;
import com.falazar.farmupcraft.data.PlayerData;
import com.falazar.farmupcraft.data.VillageData;
import com.falazar.farmupcraft.database.DataBase;
import com.falazar.farmupcraft.database.message.EDBMessages;
import com.falazar.farmupcraft.database.message.ShowVillageChunksPacket;
import com.falazar.farmupcraft.events.ModEvents;
import com.falazar.farmupcraft.registry.CoinRegistry;
import com.falazar.farmupcraft.registry.FUCRegistries;
import com.falazar.farmupcraft.util.AnimalGrainAssigner;
import com.falazar.farmupcraft.util.CustomLogger;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.phys.AABB;

import java.text.NumberFormat;
import java.util.*;

import static com.falazar.farmupcraft.command.PlotCommand.getCropsPlanted;

public class VillageCommand {
    public static final CustomLogger LOGGER = new CustomLogger(VillageCommand.class.getSimpleName());
    private static final String NBT_LAST_BRED = "farmupcraft_last_bred";

    public static void register(CommandDispatcher<CommandSourceStack> pDispatcher) {
        // Define the base command for "village"
        LiteralArgumentBuilder<CommandSourceStack> builder = Commands.literal("village");

        // Define the "info" sub-commands
        // If user doesnt add a name, then show their village, else show the named
        // village.
        LiteralArgumentBuilder<CommandSourceStack> infoBuilder = Commands.literal("info")
                .executes(context -> {
                    // Default behavior when no villageName is provided
                    return showVillageInfo(context.getSource(), null);
                })
                .then(Commands.argument("villageName", StringArgumentType.string())
                        .executes(context -> {
                            String villageName = StringArgumentType.getString(context, "villageName");
                            // Behavior when villageName is provided
                            return showVillageInfo(context.getSource(), villageName);
                        }));
        builder.then(infoBuilder);

        // Define the list subcommand:
        LiteralArgumentBuilder<CommandSourceStack> listBuilder = Commands.literal("list")
                .executes(VillageCommand::listVillages);
        builder.then(listBuilder);

        // Define the "buy" sub-command
        LiteralArgumentBuilder<CommandSourceStack> buyBuilder = Commands.literal("buy")
                .then(Commands.argument("villageName", StringArgumentType.string())
                        .executes(context -> {
                            String villageName = StringArgumentType.getString(context, "villageName");
                            return buyVillage(context.getSource(), villageName);
                        }));
        builder.then(buyBuilder);

        // Define the "rename" sub-command
        LiteralArgumentBuilder<CommandSourceStack> renameBuilder = Commands.literal("rename")
                .then(Commands.argument("villageName", StringArgumentType.string())
                        .executes(context -> {
                            String villageName = StringArgumentType.getString(context, "villageName");
                            return renameVillage(context.getSource(), villageName);
                        }));
        builder.then(renameBuilder);

        // Define the delete sub-command. ADMIN ONLY!
        LiteralArgumentBuilder<CommandSourceStack> deleteBuilder = Commands.literal("delete")
                .then(Commands.argument("villageName", StringArgumentType.string())
                        .executes(context -> {
                            String villageName = StringArgumentType.getString(context, "villageName");
                            return deleteVillage(context.getSource(), villageName);
                        }))
                .requires(s -> s.hasPermission(2)); // Adjust permission as needed
        builder.then(deleteBuilder);

        // Define the "levelup" sub-command for players current village.
        LiteralArgumentBuilder<CommandSourceStack> levelUpBuilder = Commands.literal("levelup")
                .executes(context -> {
                    return levelUpVillage(context.getSource());
                });
        builder.then(levelUpBuilder);

        // Define the "setlevel" sub-command for players current village. ADMIN ONLY!
        LiteralArgumentBuilder<CommandSourceStack> setLevelBuilder = Commands.literal("setlevel")
                .then(Commands.argument("level", IntegerArgumentType.integer(1, 10))
                        .executes(context -> {
                            Integer level = IntegerArgumentType.getInteger(context, "level");
                            return setVillageLevel(context.getSource(), level);
                        }))
                .requires(s -> s.hasPermission(2)); // Adjust permission as needed
        builder.then(setLevelBuilder);

        // Define the "rundailyupkeep" sub-command for players current village. ADMIN
        // ONLY!
        LiteralArgumentBuilder<CommandSourceStack> runDailyUpkeepBuilder = Commands.literal("rundailyupkeep")
                .executes(context -> {
                    return runVillageDailyUpkeep(context.getSource());
                })
                .requires(s -> s.hasPermission(2)); // Adjust permission as needed
        builder.then(runDailyUpkeepBuilder);

        // Define the "biomes" sub-command
        LiteralArgumentBuilder<CommandSourceStack> villageBiomesBuilder = Commands.literal("biomes")
                .executes(context -> {
                    return showVillageBiomes(context.getSource());
                });
        builder.then(villageBiomesBuilder);

        // Define the "farms" sub-command to show farm and biome info.
        LiteralArgumentBuilder<CommandSourceStack> villageFarmsBuilder = Commands.literal("farms")
                .executes(context -> {
                    return showVillagefarms(context.getSource());
                });
        builder.then(villageFarmsBuilder);

        // Define the "structures" sub-command to show structures in the village
        LiteralArgumentBuilder<CommandSourceStack> villageStructuresBuilder = Commands.literal("structures")
                .executes(context -> {
                    return showVillageStructures(context.getSource(), 20, "all");
                })
                .then(Commands.argument("filter", StringArgumentType.word())
                        .suggests((context, builder2) -> {
                            builder2.suggest("all");
                            builder2.suggest("claimed");
                            builder2.suggest("unclaimed");
                            return builder2.buildFuture();
                        })
                        .executes(context -> {
                            String filter = StringArgumentType.getString(context, "filter");
                            return showVillageStructures(context.getSource(), 20, filter);
                        })
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 100))
                                .executes(context -> {
                                    String filter = StringArgumentType.getString(context, "filter");
                                    int count = IntegerArgumentType.getInteger(context, "count");
                                    return showVillageStructures(context.getSource(), count, filter);
                                })));
        builder.then(villageStructuresBuilder);

        // Define the "villagers" sub-command to show nearby villagers
        LiteralArgumentBuilder<CommandSourceStack> villageVillagersBuilder = Commands.literal("villagers")
                .executes(context -> {
                    return findNearVillagers(context.getSource());
                });
        builder.then(villageVillagersBuilder);

        // Define the "setanimal" sub-command - set the village breeding animal type.
        LiteralArgumentBuilder<CommandSourceStack> setAnimalBuilder = Commands.literal("setanimal")
                .then(Commands.argument("animalType", StringArgumentType.word())
                        .suggests((context, builder2) -> {
                            builder2.suggest("cow");
                            builder2.suggest("sheep");
                            builder2.suggest("pig");
                            builder2.suggest("chicken");
                            return builder2.buildFuture();
                        })
                        .executes(context -> {
                            String animalType = StringArgumentType.getString(context, "animalType");
                            return setVillageAnimalType(context.getSource(), animalType);
                        }));
        builder.then(setAnimalBuilder);

        // Define the "setfounder" sub-command. ADMIN ONLY.
        LiteralArgumentBuilder<CommandSourceStack> setFounderBuilder = Commands.literal("setfounder")
                .then(Commands.argument("playerName", StringArgumentType.word())
                        .executes(context -> {
                            String playerName = StringArgumentType.getString(context, "playerName");
                            return setVillageFounder(context.getSource(), playerName);
                        }))
                .requires(s -> s.hasPermission(2));
        builder.then(setFounderBuilder);

        // Define the "invite" sub-command — send a village invite to an online player.
        LiteralArgumentBuilder<CommandSourceStack> inviteBuilder = Commands.literal("invite")
                .then(Commands.argument("playerName", StringArgumentType.word())
                        .executes(context -> {
                            String playerName = StringArgumentType.getString(context, "playerName");
                            return invitePlayerToVillage(context.getSource(), playerName);
                        }));
        builder.then(inviteBuilder);

        // Define the "accept" sub-command — accept a pending village invite.
        LiteralArgumentBuilder<CommandSourceStack> acceptBuilder = Commands.literal("accept")
                .executes(context -> acceptVillageInvite(context.getSource()));
        builder.then(acceptBuilder);

        // Define the "animals" sub-command — show grain requirements per animal type.
        LiteralArgumentBuilder<CommandSourceStack> animalsBuilder = Commands.literal("animals")
                .executes(context -> showVillageAnimals(context.getSource()));
        builder.then(animalsBuilder);

        // Define admin utility subcommands.
        LiteralArgumentBuilder<CommandSourceStack> adminBuilder = Commands.literal("admin")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("regenanimalgrains")
                        .executes(context -> regenerateVillageAnimalGrains(context.getSource(), null))
                        .then(Commands.argument("villageName", StringArgumentType.string())
                                .executes(context -> regenerateVillageAnimalGrains(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "villageName")))))
            .then(Commands.literal("setfounder")
                .then(Commands.argument("playerName", StringArgumentType.word())
                    .executes(context -> setVillageFounder(
                        context.getSource(),
                        StringArgumentType.getString(context, "playerName")))))
                .then(Commands.literal("clearbreeding")
                        .executes(context -> clearVillageBreedingCooldown(context.getSource(), null))
                        .then(Commands.argument("villageName", StringArgumentType.string())
                                .executes(context -> clearVillageBreedingCooldown(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "villageName")))));
        builder.then(adminBuilder);

        // Define the "map" sub-command — show/clear village chunk overlays on
        // JourneyMap.
        // /village map → show blue (plot) / green (claimed) overlays
        // /village map clear → remove all overlays
        LiteralArgumentBuilder<CommandSourceStack> mapBuilder = Commands.literal("map")
                .executes(context -> FarmCraftCommand.showVillageChunks(context.getSource()))
                .then(Commands.literal("clear")
                        .executes(context -> FarmCraftCommand.clearVillageChunks(context.getSource())));
        builder.then(mapBuilder);

        // Register the main "village" command with the dispatcher
        pDispatcher.register(builder);
    }

    // Given a player command, villageName and their location, buy the village and
    // mark chunks as owned.
    public static int buyVillage(CommandSourceStack source, String villageName) {
        try {
            Entity nullablePlayer = source.getEntity();
            Player player = nullablePlayer instanceof Player ? (Player) nullablePlayer : null;
            if (villageName == null || villageName.isEmpty()) {
                source.sendFailure(Component.literal("Village name is required for village plot type."));
                return 0;
            }

            // Level level = player.level();

            ChunkPos chunkPos = new ChunkPos(player.blockPosition());
            DataBase<Long, ChunkData> chunkDataDatabase = ModEvents.getChunkDataDatabase();
            ChunkData chunkData = chunkDataDatabase.getData(chunkPos.toLong());
            // TODO can we hide all this inside???
            DataBase<UUID, PlayerData> playerDataDataBase = ModEvents.getPlayerDatabase();
            PlayerData playerData = playerDataDataBase.getData(player.getUUID());
            LOGGER.info("DEBUG TODO PlayerData: " + playerData.getId() + ", " + playerData.getHomeVillageUUID());

            // Step 1: Check if chunk is owned. (Inside another village)
            // TODO add in, make method.
            if (chunkData != null) {
                source.sendFailure(Component.literal("Chunk is already owned."));
                return 0;
            }

            // Step 2: Check if too close to nearest village
            VillageData closestVillage = getClosestVillage(player.blockPosition());
            if (closestVillage != null) {
                BlockPos closestVillagePos = closestVillage.getPosition().getWorldPosition();
                int distance = Math.abs(closestVillagePos.getX() - player.blockPosition().getX()) +
                        Math.abs(closestVillagePos.getZ() - player.blockPosition().getZ());
                if (distance < 100) { // TODO make this a config value.
                    source.sendFailure(Component.literal(
                            "Too close to another village: " + closestVillage.getName() + " distance = " + distance));
                    return 0;
                }
            }

            // Step 3: Check if village name is unique.
            if (!isVillageNameUnique(villageName)) {
                source.sendFailure(
                        Component.literal("Village name " + villageName + " is not unique, please choose another."));
                return 0;
            }

            // STEP 4: Calculate dynamic village buy cost.
            DataBase<UUID, VillageData> villageDatabase = ModEvents.getVillageDatabase();
            int cost = calculateVillageBuyCost();
            if (playerData.getCoins() < cost) {
                source.sendFailure(Component.literal("Not enough coins to buy a village. Cost: " + cost
                        + ", you have: " + playerData.getCoins()));
                return 0;
            }

            // Step 6: TODO Check if player is in another village right now.
            UUID homeVillageId = playerData.getHomeVillageUUID();
            if (homeVillageId != null) {
                VillageData homeVillage = villageDatabase.getData(homeVillageId);
                if (homeVillage != null) {
                    source.sendFailure(Component.literal("Player is already in a village: " +
                            homeVillage.getName()));
                    return 0;
                }
            }

            // STEP 5: Subtract money from the player.
            if (!playerData.removeCoins(cost)) {
                source.sendFailure(Component.literal("Failed to deduct coins for village purchase."));
                return 0;
            }

            // Step 7: Generate all chunk positions in the radius
            int radius = 5;
            ChunkPos centerChunk = new ChunkPos(player.blockPosition());
            List<ChunkPos> villageChunks = new ArrayList<>();
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    villageChunks.add(new ChunkPos(centerChunk.x + dx, centerChunk.z + dz));
                }
            }
            // TODO add chunk count somewhere?

            // Step 8: Create Village object and save it
            UUID villageId = UUID.randomUUID();
            String founderName = player.getName().getString();
            VillageData villageData = new VillageData(villageId, villageName, player.chunkPosition(), 1, villageChunks,
                    true, 0, "2000-01-01", "", "2000-01-01", founderName, new java.util.ArrayList<>());
            villageDatabase.putData(villageId, villageData);
            LOGGER.info("Village " + villageName + " created by " + founderName + " with id " + villageId +
                    " saved with " + villageChunks.size() + " chunks around " + player.blockPosition());

            // STEP 9: Buy plot and mark to db.
            // TODO1 THESE villageId TO USE UUIDS
            // Mark chunks to village.
            for (ChunkPos pos : villageChunks) {
                ChunkData chunk = new ChunkData("village", player.getId(), villageId);
                chunkDataDatabase.putData(pos.toLong(), chunk);

                // Update structure claim flags for this chunk
                if (player.level() instanceof ServerLevel serverLevel) {
                    StructureCommand.updateStructuresInChunk(pos, true, serverLevel);
                }
            }
            // Mark the center chunk as village center.
            ChunkData data = chunkDataDatabase.getData(chunkPos.toLong());
            data.setType("village center");
            chunkDataDatabase.putData(chunkPos.toLong(), data);

            LOGGER.info("Plot bought at " + chunkPos);

            // Auto-scan for nearby structures in a 30-chunk radius on village creation.
            if (player.level() instanceof ServerLevel serverLevel) {
                LOGGER.info("Auto-scanning for structures within 30 chunks of new village " + villageName);
                StructureCommand.findNearbyStructuresForVillage(
                        player.blockPosition(), serverLevel, player, villageData, "all", 30);
            }
            // TODO1 call a set plot method, separate this out.
            // TODO1 add plot to city.

            // STEP 10: Add player to village member list.
            villageData.addMember(player.getUUID());
            // Assign grain requirements for this village based on its position.
            AnimalGrainAssigner.assignGrains(villageData);
            villageDatabase.putData(villageId, villageData);

            // STEP 11: Add village to player.
            playerData.setHomeVillageId(villageId);

            // We have to put it back in there otherwise it wont sync to client
            playerDataDataBase.putData(player.getUUID(), playerData);

            // STEP 12: Build a response message and send.
            MutableComponent response = Component.literal("Village bought at " + chunkPos);
            response = response.append(Component.literal(" and created with name " + villageName));
            response = response.append(Component.literal(" for " + cost + " coins."));
            MutableComponent finalResponse = response;
            source.sendSuccess(() -> finalResponse, false);
        } catch (Exception ex) {
            source.sendFailure(Component.literal("Exception thrown - see log"));
            ex.printStackTrace();
        }
        return 0;
    }

    // Show grain requirements for all farm animals in the player's village.
    public static int showVillageAnimals(CommandSourceStack source) {
        try {
            Entity nullableSummoner = source.getEntity();
            Player playerSource = nullableSummoner instanceof Player ? (Player) nullableSummoner : null;
            if (playerSource == null) {
                source.sendFailure(Component.literal("Player not found."));
                return 0;
            }
            DataBase<UUID, PlayerData> playerDb = ModEvents.getPlayerDatabase();
            PlayerData playerData = playerDb.getData(playerSource.getUUID());
            if (playerData == null || playerData.getHomeVillageUUID() == null) {
                source.sendFailure(Component.literal("You don't belong to a village."));
                return 0;
            }
            DataBase<UUID, VillageData> villageDb = ModEvents.getVillageDatabase();
            VillageData village = villageDb.getData(playerData.getHomeVillageUUID());
            if (village == null) {
                source.sendFailure(Component.literal("Village not found."));
                return 0;
            }
            // Assign grains lazily if not yet set.
            if (!village.hasAnimalGrainsAssigned()) {
                AnimalGrainAssigner.assignGrains(village);
                villageDb.putData(village.getUUID(), village);
            }
            source.sendSuccess(() -> Component.literal("=== " + village.getName() + " Animal Grain Requirements ===")
                    .withStyle(ChatFormatting.GOLD), false);
            java.util.Map<String, java.util.List<String>> map = village.getAnimalGrainsMap();
            for (String animal : AnimalGrainAssigner.ANIMAL_TYPES) {
                java.util.List<String> grains = map.getOrDefault(animal, java.util.List.of());
                String grainsText = grains.stream()
                        .map(AnimalGrainAssigner::displayName)
                        .collect(java.util.stream.Collectors.joining(" & "));
                source.sendSuccess(() -> Component.literal("  " + capitalize(animal) + ": ")
                        .withStyle(ChatFormatting.YELLOW)
                        .append(Component.literal(grainsText).withStyle(ChatFormatting.WHITE)), false);
            }
            return 1;
        } catch (Exception e) {
            LOGGER.error("Error in showVillageAnimals: " + e.getMessage());
            source.sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty())
            return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    // Admin utility: regenerate animal grain requirements for current or named
    // village.
    public static int regenerateVillageAnimalGrains(CommandSourceStack source, String villageName) {
        try {
            DataBase<UUID, VillageData> villageDb = ModEvents.getVillageDatabase();
            VillageData village;

            if (villageName != null && !villageName.isBlank()) {
                village = findVillageByName(villageName);
                if (village == null) {
                    source.sendFailure(Component.literal("Village not found: " + villageName));
                    return 0;
                }
            } else {
                Entity nullableSummoner = source.getEntity();
                Player playerSource = nullableSummoner instanceof Player ? (Player) nullableSummoner : null;
                if (playerSource == null) {
                    source.sendFailure(Component.literal(
                            "From console, provide a village name: /village admin regenanimalgrains <villageName>"));
                    return 0;
                }

                PlayerData playerData = ModEvents.getPlayerDatabase().getData(playerSource.getUUID());
                if (playerData == null || playerData.getHomeVillageUUID() == null) {
                    source.sendFailure(Component.literal("You don't belong to a village."));
                    return 0;
                }

                village = villageDb.getData(playerData.getHomeVillageUUID());
                if (village == null) {
                    source.sendFailure(Component.literal("Village data not found."));
                    return 0;
                }
            }

            AnimalGrainAssigner.assignGrains(village);
            villageDb.putData(village.getUUID(), village);

            source.sendSuccess(() -> Component.literal(
                    "Regenerated animal grain requirements for village '" + village.getName() + "'."), true);
            LOGGER.info("Admin regenerated animal grains for village {} ({})", village.getName(), village.getUUID());
            return 1;
        } catch (Exception e) {
            LOGGER.error("Error in regenerateVillageAnimalGrains: {}", e.getMessage(), e);
            source.sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    // Admin utility: clear breeding cooldown NBT for farm animals in a local
    // 5x5 chunk area around the command source chunk (+-2).
    public static int clearVillageBreedingCooldown(CommandSourceStack source, String villageName) {
        try {
            ServerLevel level = source.getLevel();
            BlockPos sourcePos = BlockPos.containing(source.getPosition());
            ChunkPos center = new ChunkPos(sourcePos);
            Set<UUID> seen = new HashSet<>();
            int clearedCount = 0;

            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    ChunkPos cp = new ChunkPos(center.x + dx, center.z + dz);
                    double minX = cp.getMinBlockX();
                    double minZ = cp.getMinBlockZ();
                    double maxX = cp.getMaxBlockX() + 1;
                    double maxZ = cp.getMaxBlockZ() + 1;
                    double minY = level.getMinBuildHeight();
                    double maxY = level.getMaxBuildHeight();

                    AABB box = new AABB(minX, minY, minZ, maxX, maxY, maxZ);
                    for (Animal animal : level.getEntitiesOfClass(Animal.class, box)) {
                        if (!isFarmBreedingAnimal(animal) || !seen.add(animal.getUUID())) {
                            continue;
                        }
                        if (animal.getPersistentData().contains(NBT_LAST_BRED)) {
                            animal.getPersistentData().remove(NBT_LAST_BRED);
                            clearedCount++;
                        }
                    }
                }
            }

            int finalClearedCount = clearedCount;
            source.sendSuccess(() -> Component.literal(
                    "Cleared breeding cooldown on " + finalClearedCount
                            + " animals in chunk area +-2 around current position."),
                    true);
            LOGGER.info("Admin cleared breeding cooldown on {} animals in 5x5 chunk area around chunk {},{}",
                    clearedCount, center.x, center.z);
            return 1;
        } catch (Exception e) {
            LOGGER.error("Error in clearVillageBreedingCooldown: {}", e.getMessage(), e);
            source.sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    private static boolean isFarmBreedingAnimal(Animal animal) {
        return animal instanceof Cow
                || animal instanceof Sheep
                || animal instanceof Pig
                || animal instanceof Chicken;
    }

    // Show the village that current player has home in.
    public static int showVillageInfo(CommandSourceStack source, String villageName) {
        try {
            Entity nullableSummoner = source.getEntity();
            Player playerSource = nullableSummoner instanceof Player ? (Player) nullableSummoner : null;
            if (playerSource == null) {
                source.sendFailure(Component.literal("Player not found."));
                return 0;
            }

            DataBase<UUID, PlayerData> playerDataDataBase = ModEvents.getPlayerDatabase();
            PlayerData playerData = playerDataDataBase.getData(playerSource.getUUID());
            VillageData village = null;
            // Find by name or player data. (break these two up, might be slightly different
            // later)
            if (villageName == null) {
                UUID villageId = playerData.getHomeVillageUUID();
                DataBase<UUID, VillageData> dataBase = ModEvents.getVillageDatabase();
                village = dataBase.getData(villageId);
            } else {
                village = findVillageByName(villageName);
            }
            if (village == null) {
                source.sendFailure(Component.literal("No village data found."));
                return 0;
            }

            // STEP 1: Build a response message with basic info.
            NumberFormat numberFormat = NumberFormat.getInstance();
            MutableComponent response = Component.literal("")
                    .append(Component.literal("---------- Village Name: " + village.getName() + " ----------\n")
                            .withStyle(ChatFormatting.YELLOW)) // Yellow
                    .append(Component.literal("Level: " + village.getLevel() + " \n")) // White
                    .append(Component.literal("Range: " + getVillageRadius(village) + " chunks \n")) // White
                    .append(Component.literal("Coins: " + numberFormat.format(village.getCoins()) + " \n")
                            .withStyle(village.getCoins() < 0 ? ChatFormatting.RED : ChatFormatting.WHITE)) // Red if
                                                                                                            // negative,
                                                                                                            // white
                                                                                                            // otherwise
                    .append(Component
                            .literal(" at " + village.getPosition().getWorldPosition().toShortString() + " \n")) // White
                    .append(Component.literal(" with claimed chunks = " + village.getClaimedChunks().size() + "\n")); // White

            // STEP 2: Loop over all plots and count them, and farms.
            int plotCnt = getPlotCount(village);
            int farmCnt = 0;
            for (ChunkPos pos : village.getClaimedChunks()) {
                ChunkData chunkData = ModEvents.getChunkDataDatabase().getData(pos.toLong());
                if (chunkData != null) {
                    if (chunkData.getType().equalsIgnoreCase("farm")) {
                        farmCnt++;
                    }
                }
            }
            response = response.append(Component.literal(" with " + plotCnt + " plots and " + farmCnt + " farms. \n"));

            // Step 3: Show plot and daily costs.
            int plotCost = PlotCommand.calculatePlotCost(village, "plot");
            response = response
                    .append(Component.literal(" Plot cost: " + numberFormat.format(plotCost) + " coins. \n"));
            int dailyCost = getDailyCost(village);
            response = response
                    .append(Component.literal(" Daily cost: " + numberFormat.format(dailyCost) + " coins. \n"));

            // STEP 4: Show structure count and daily coins earned using helper methods.
            int claimedStructures = countVillageClaimedStructures(village, playerSource.level());
            int unclaimedStructures = countVillageUnclaimedStructures(village, playerSource.level());
            int dailyCoins = claimedStructures * 100;
            response = response.append(Component.literal(" Claimed structures: " + claimedStructures + "\n"));
            response = response.append(Component.literal(" Unclaimed structures: " + unclaimedStructures + "\n"));
            response = response
                    .append(Component.literal(" Daily coins earned: " + numberFormat.format(dailyCoins) + "\n"));

            // STEP 5: Show villager count
            BlockPos villageCenter = village.getPosition().getWorldPosition();
            int villagerCount = countVillagers(villageCenter, playerSource.level(), 10);
            response = response.append(Component.literal(" Villagers count: " + villagerCount + "\n"));

            // STEP 6: Show founder.
            String founder = village.getFounder();
            if (!founder.isEmpty()) {
                response = response.append(Component.literal(" Founder: " + founder + "\n"));
            }

            // STEP 7: Show animal type if set.
            String animalType = village.getAnimalType();
            if (!animalType.isEmpty()) {
                response = response.append(Component.literal(" Breeding animal: " + animalType + "\n"));
            }

            // STEP 8: Show member list.
            List<UUID> members = village.getMemberUUIDs();
            if (!members.isEmpty()) {
                StringBuilder memberNames = new StringBuilder();
                for (UUID memberUUID : members) {
                    if (memberNames.length() > 0)
                        memberNames.append(", ");
                    memberNames.append(getPlayerName(source.getServer(), memberUUID));
                }
                response = response.append(Component.literal(" Members (" + members.size() + "): " + memberNames + "\n")
                        .withStyle(ChatFormatting.AQUA));
            } else {
                response = response.append(Component.literal(" Members: none\n").withStyle(ChatFormatting.GRAY));
            }

            MutableComponent finalResponse = response;
            source.sendSuccess(() -> finalResponse, false);
            LOGGER.info(
                    "DEBUG /village info: name={}, level={}, coins={}, plots={}, farms={}, dailyCost={}, dailyEarned={}, lastRanDaily={}, villagers={}",
                    village.getName(), village.getLevel(), village.getCoins(),
                    plotCnt, farmCnt, dailyCost, dailyCoins,
                    village.getLastRanDaily(), villagerCount);
        } catch (Exception ex) {
            source.sendFailure(Component.literal("Exception in show village info - see logs"));
            ex.printStackTrace();
        }
        return 0;
    }

    /**
     * Resolves a player UUID to a display name.
     * Checks online players first, then falls back to the server's profile cache
     * (usercache.json).
     * Vanilla creates and maintains the cache automatically — no setup needed.
     *
     * @param server the Minecraft server instance
     * @param uuid   the player UUID to look up
     * @return display name, appended with " (online)" if currently connected, or a
     *         short UUID if unknown
     */
    public static String getPlayerName(net.minecraft.server.MinecraftServer server, UUID uuid) {
        net.minecraft.server.level.ServerPlayer online = server.getPlayerList().getPlayer(uuid);
        if (online != null) {
            return online.getName().getString() + " (online)";
        }
        if (server.getProfileCache() != null) {
            return server.getProfileCache().get(uuid)
                    .map(com.mojang.authlib.GameProfile::getName)
                    .orElse(uuid.toString().substring(0, 8) + "...");
        }
        return uuid.toString().substring(0, 8) + "...";
    }

    // TODO Create getGameStructures method stub.
    public static List<GameStructureData> getGameStructures(UUID villageId) {
        // Get all near the village.

        // TODO: Implement logic to return nearby or owned structures

        // Fill in two fake ones
        // cemetery and armorers house
        List<GameStructureData> structures = new ArrayList<>();
        structures.add(new GameStructureData(1L, "Cemetery", new BlockPos(0, 0, 0), "village", true, false));
        structures.add(new GameStructureData(2L, "Armorer's House", new BlockPos(1, 1, 1), "village", false, false));

        return structures;
    }

    public static int getVillageRadius(VillageData village) {
        return 2 + 3 * village.getLevel();
    }

    // Runs hourly upkeep for villages of online players only.
    // Returns the number of villages processed.
    public static int runOnlinePlayerVillageUpkeep(net.minecraft.server.MinecraftServer server) {
        int processed = 0;
        try {
            DataBase<UUID, VillageData> villageDatabase = ModEvents.getVillageDatabase();
            DataBase<UUID, PlayerData> playerDatabase = ModEvents.getPlayerDatabase();
            Level level = server.overworld();

            // Collect unique village UUIDs from online players.
            Set<UUID> villageIds = new HashSet<>();
            for (net.minecraft.server.level.ServerPlayer onlinePlayer : server.getPlayerList().getPlayers()) {
                PlayerData playerData = playerDatabase.getData(onlinePlayer.getUUID());
                if (playerData == null || playerData.getHomeVillageUUID() == null)
                    continue;
                villageIds.add(playerData.getHomeVillageUUID());
            }

            if (villageIds.isEmpty()) {
                LOGGER.info("runOnlinePlayerVillageUpkeep: no online players with a village.");
                return 0;
            }

            for (UUID villageId : villageIds) {
                VillageData village = villageDatabase.getData(villageId);
                if (village == null)
                    continue;
                // Skip if upkeep already ran today for this village.
                if (village.hasRanTodayAlready()) {
                    LOGGER.info("runOnlinePlayerVillageUpkeep: {} already ran today, skipping.", village.getName());
                    continue;
                }
                processVillageUpkeep(village, level);
                processed++;
            }
        } catch (Exception ex) {
            LOGGER.error("runOnlinePlayerVillageUpkeep: exception - " + ex.getMessage());
            ex.printStackTrace();
        }
        return processed;
    }

    public static int getDailyCost(VillageData village) {
        int dailyCost = village.getLevel() * 100 + getPlotCount(village) * 30;

        return dailyCost;
    }

    /** Village buy price: base 500 + 200 per existing village in the world. */
    public static int calculateVillageBuyCost() {
        int existingVillageCount = ModEvents.getVillageDatabase().getSize();
        return 500 + (Math.max(0, existingVillageCount) * 200);
    }

    public static int getPlotCount(VillageData village) {
        int plotCnt = 0;
        for (ChunkPos pos : village.getClaimedChunks()) {
            ChunkData chunkData = ModEvents.getChunkDataDatabase().getData(pos.toLong());
            if (chunkData != null) {
                if (!chunkData.getType().equalsIgnoreCase("village")) {
                    plotCnt++;
                }
            }
        }

        return plotCnt;
    }

    // List all villages in the world.
    public static int listVillages(CommandContext<CommandSourceStack> context) {
        try {
            Entity nullableSummoner = context.getSource().getEntity();
            Player summoner = nullableSummoner instanceof Player ? (Player) nullableSummoner : null;
            if (summoner == null) {
                context.getSource().sendFailure(Component.literal("Player not found."));
                return 0;
            }
            // Level level = summoner.level();

            // TODO load all village from db.
            // TODO MAKE METHOD.
            DataBase<UUID, VillageData> dataBase = ModEvents.getVillageDatabase();
            Collection<VillageData> villageList = dataBase.getValues();
            if (villageList == null || villageList.isEmpty()) {
                context.getSource().sendFailure(Component.literal("No villages data found."));
                return 0;
            }

            // Build a response message
            MutableComponent response = Component.literal("Village list: \n");
            int index = 1;
            // What order here? TODO Make alpha.
            for (VillageData village : villageList) {
                response = response.append(Component.literal(index++
                        + ". " + village.getName() +
                        " Level " + village.getLevel() +
                        " at " + village.getPosition().getWorldPosition().toShortString()
                        + " with " + village.getClaimedChunks().size() + " chunks, \n"));
                // TODO1 bug size is not getting right here, or claim got too many.
                LOGGER.info("DEBUG TODO Village info for: village = " + village.getName()
                        + ", id = " + village.getUUID()
                        + ", chunks = " + village.getClaimedChunks().stream().count()
                        + " claimedChunkSet = " + village.getClaimedChunkSet().size());
            }

            // TODO1 claimed chunks is wayyyyyy too large.
            MutableComponent finalResponse = response;
            context.getSource().sendSuccess(() -> finalResponse, false);
        } catch (Exception ex) {
            context.getSource().sendFailure(Component.literal("Exception thrown - see log"));
            ex.printStackTrace();
        }
        return 0;
    }

    public static VillageData findVillageByName(String villageName) {
        DataBase<UUID, VillageData> dataBase = ModEvents.getVillageDatabase();
        Collection<VillageData> dataList = dataBase.getValues();
        if (dataList == null || dataList.isEmpty()) {
            return null;
        }

        // Loop and find the village.
        for (VillageData data : dataList) {
            if (data.getName().equalsIgnoreCase(villageName)) {
                return data;
            }
        }
        return null;
    }

    public static int deleteVillage(CommandSourceStack source, String villageName) {
        try {
            Entity nullableSummoner = source.getEntity();
            Player playerSource = nullableSummoner instanceof Player ? (Player) nullableSummoner : null;
            if (playerSource == null) {
                source.sendFailure(Component.literal("Player not found."));
                return 0;
            }

            DataBase<UUID, VillageData> villageDatabase = ModEvents.getVillageDatabase();
            VillageData village = findVillageByName(villageName);
            if (village == null) {
                source.sendFailure(Component.literal("No village data found."));
                return 0;
            }

            // TODO remove from player data.
            // TODO NEED player data to get uuid dont have, save to db!!!
            // Loop over all players, if home village is this one, remove it.
            // DataBase<UUID, PlayerData> playerDataDatabase =
            // ModEvents.getPlayerDatabase();
            // Collection<PlayerData> playerDataList = playerDataDatabase.getValues();
            // if (playerDataList != null && !playerDataList.isEmpty()) {
            // for (PlayerData playerData : playerDataList) {
            // if (playerData.getHomeVillageUUID() != null &&
            // playerData.getHomeVillageUUID().equals(villageData.getUUID())) {
            // playerData.setHomeVillageId(null);
            // playerDataDatabase.putData(player.getUUID(), playerData);
            // }
            // }
            // }

            // TODO remove chunks data. test
            DataBase<Long, ChunkData> chunkDataDatabase = ModEvents.getChunkDataDatabase();
            for (ChunkPos chunkPos : village.getClaimedChunks()) {
                ChunkData chunkData = chunkDataDatabase.getData(chunkPos.toLong());
                if (chunkData != null) {
                    chunkDataDatabase.removeDataAsync(chunkPos.toLong(), null);
                }
            }
            // TODO test once more.

            villageDatabase.removeDataAsync(village.getUUID(), null);
            villageDatabase.setDirty();

            // Build a response message
            MutableComponent response = Component.literal("Village deleted: " + village.getName());
            MutableComponent finalResponse = response;
            source.sendSuccess(() -> finalResponse, false);
        } catch (Exception ex) {
            source.sendFailure(Component.literal("Exception thrown - see log"));
            ex.printStackTrace();
        }
        return 0;
    }

    // Rename a village.
    public static int renameVillage(CommandSourceStack source, String villageName) {
        try {
            Entity nullableSummoner = source.getEntity();
            Player playerSource = nullableSummoner instanceof Player ? (Player) nullableSummoner : null;
            if (playerSource == null) {
                source.sendFailure(Component.literal("Player not found."));
                return 0;
            }
            if (villageName == null || villageName.isEmpty()) {
                source.sendFailure(Component.literal("Village name is required for village plot type."));
                return 0;
            }

            // Load village from db that villager is in.
            PlayerData player = ModEvents.getPlayerDatabase().getData(playerSource.getUUID());
            DataBase<UUID, VillageData> villageDatabase = ModEvents.getVillageDatabase();
            VillageData villageData = villageDatabase.getData(player.getHomeVillageUUID());
            if (villageData == null) {
                source.sendFailure(Component.literal("No village data found."));
                return 0;
            }

            // Check if name is unique.
            if (!isVillageNameUnique(villageName)) {
                source.sendFailure(
                        Component.literal("Village name " + villageName + " is not unique, please choose another."));
                return 0;
            }

            // Update name in db.
            villageData.setName(villageName);
            villageDatabase.putData(villageData.getUUID(), villageData);
            // TODO TEST

            // Build a response message
            MutableComponent response = Component.literal("Village renamed to: " + villageData.getName());
            MutableComponent finalResponse = response;
            source.sendSuccess(() -> finalResponse, false);
        } catch (Exception ex) {
            source.sendFailure(Component.literal("Exception thrown - see log"));
            ex.printStackTrace();
        }
        return 0;
    }

    // -------------------------------------------------------------------------
    // Village Invite System (in-memory only, 10-minute TTL, no server-restart
    // persistence)
    // -------------------------------------------------------------------------
    private record PendingInvite(UUID villageId, String inviterName, long expiryMs) {
    }

    /** key = invited player UUID */
    private static final Map<UUID, PendingInvite> PENDING_INVITES = new HashMap<>();

    /** Remove expired entries (called lazily on invite/accept). */
    private static void cleanExpiredInvites() {
        long now = System.currentTimeMillis();
        PENDING_INVITES.entrySet().removeIf(e -> e.getValue().expiryMs() < now);
    }

    public static int invitePlayerToVillage(CommandSourceStack source, String targetName) {
        try {
            Entity nullableSummoner = source.getEntity();
            Player inviter = nullableSummoner instanceof Player ? (Player) nullableSummoner : null;
            if (inviter == null) {
                source.sendFailure(Component.literal("Player not found."));
                return 0;
            }

            // Inviter must be in a village.
            PlayerData inviterData = ModEvents.getPlayerDatabase().getData(inviter.getUUID());
            if (inviterData == null || inviterData.getHomeVillageUUID() == null) {
                source.sendFailure(Component.literal("You are not in a village."));
                return 0;
            }

            VillageData village = ModEvents.getVillageDatabase().getData(inviterData.getHomeVillageUUID());
            if (village == null) {
                source.sendFailure(Component.literal("Could not find your village data."));
                return 0;
            }

            // Find the target player online.
            net.minecraft.server.level.ServerPlayer target = source.getServer().getPlayerList()
                    .getPlayerByName(targetName);
            if (target == null) {
                source.sendFailure(Component.literal("Player '" + targetName + "' is not online."));
                return 0;
            }

            // Don't invite yourself.
            if (target.getUUID().equals(inviter.getUUID())) {
                source.sendFailure(Component.literal("You cannot invite yourself."));
                return 0;
            }

            // Check if target already in a village.
            PlayerData targetData = ModEvents.getPlayerDatabase().getData(target.getUUID());
            if (targetData != null && targetData.getHomeVillageUUID() != null
                    && ModEvents.getVillageDatabase().getData(targetData.getHomeVillageUUID()) != null) {
                source.sendFailure(Component.literal(targetName + " is already a member of a village."));
                return 0;
            }

            cleanExpiredInvites();
            long expiryMs = System.currentTimeMillis() + 10L * 60 * 1000; // 10 minutes
            PENDING_INVITES.put(target.getUUID(),
                    new PendingInvite(village.getUUID(), inviter.getName().getString(), expiryMs));

            LOGGER.info("Invite stored: {} invited {} (uuid={}) to village {}",
                    inviter.getName().getString(), targetName, target.getUUID(), village.getName());

            // Tell the inviter.
            source.sendSuccess(() -> Component.literal(
                    "Invite sent to " + targetName + " to join village '" + village.getName()
                            + "'. It expires in 10 minutes."),
                    false);

            // Notify the invited player — both in chat and above the hotbar so it is hard
            // to miss.
            Component inviteMsg = Component.literal(
                    "[Village] " + inviter.getName().getString() + " invited you to '" + village.getName()
                            + "'! Type /village accept to join!")
                    .withStyle(ChatFormatting.GREEN);
            target.sendSystemMessage(inviteMsg);
            target.displayClientMessage(inviteMsg, false);
        } catch (Exception ex) {
            source.sendFailure(Component.literal("Exception thrown - see log"));
            ex.printStackTrace();
        }
        return 0;
    }

    public static int acceptVillageInvite(CommandSourceStack source) {
        try {
            Entity nullableSummoner = source.getEntity();
            Player player = nullableSummoner instanceof Player ? (Player) nullableSummoner : null;
            if (player == null) {
                source.sendFailure(Component.literal("Player not found."));
                return 0;
            }

            cleanExpiredInvites();
            PendingInvite invite = PENDING_INVITES.remove(player.getUUID());
            if (invite == null) {
                source.sendFailure(Component.literal("You have no pending village invite (or it has expired)."));
                return 0;
            }

            // Double-check target is not already in a village.
            DataBase<UUID, PlayerData> playerDb = ModEvents.getPlayerDatabase();
            PlayerData playerData = playerDb.getData(player.getUUID());
            if (playerData == null) {
                source.sendFailure(Component.literal("Your player data was not found."));
                return 0;
            }
            if (playerData.getHomeVillageUUID() != null
                    && ModEvents.getVillageDatabase().getData(playerData.getHomeVillageUUID()) != null) {
                source.sendFailure(Component.literal("You are already a member of a village."));
                return 0;
            }

            // Ensure village still exists.
            VillageData village = ModEvents.getVillageDatabase().getData(invite.villageId());
            if (village == null) {
                source.sendFailure(Component.literal("The village you were invited to no longer exists."));
                return 0;
            }

            // Join the village.
            playerData.setHomeVillageId(invite.villageId());
            playerDb.putData(player.getUUID(), playerData);
            village.addMember(player.getUUID());
            ModEvents.getVillageDatabase().putData(village.getUUID(), village);

            source.sendSuccess(() -> Component.literal(
                    "You have joined the village '" + village.getName() + "'! Welcome!")
                    .withStyle(ChatFormatting.GREEN), false);

            // Broadcast to village members who are online.
            Component joinMsg = Component.literal("[Village] " + player.getName().getString()
                    + " has joined the village '" + village.getName() + "'!")
                    .withStyle(ChatFormatting.YELLOW);
            for (net.minecraft.server.level.ServerPlayer online : source.getServer().getPlayerList().getPlayers()) {
                PlayerData onlineData = playerDb.getData(online.getUUID());
                if (onlineData != null && invite.villageId().equals(onlineData.getHomeVillageUUID())) {
                    online.sendSystemMessage(joinMsg);
                }
            }
        } catch (Exception ex) {
            source.sendFailure(Component.literal("Exception thrown - see log"));
            ex.printStackTrace();
        }
        return 0;
    }

    // -------------------------------------------------------------------------

    private static final java.util.Set<String> VALID_ANIMAL_TYPES = java.util.Set.of("cow", "sheep", "pig", "chicken");

    // Sets the village's allowed breeding animal type. Can only be changed once per
    // week.
    public static int setVillageAnimalType(CommandSourceStack source, String animalType) {
        try {
            Entity nullableSummoner = source.getEntity();
            Player playerSource = nullableSummoner instanceof Player ? (Player) nullableSummoner : null;
            if (playerSource == null) {
                source.sendFailure(Component.literal("Player not found."));
                return 0;
            }

            if (!VALID_ANIMAL_TYPES.contains(animalType.toLowerCase())) {
                source.sendFailure(Component.literal("Invalid animal type. Must be: cow, sheep, pig, or chicken."));
                return 0;
            }

            PlayerData player = ModEvents.getPlayerDatabase().getData(playerSource.getUUID());
            DataBase<UUID, VillageData> villageDatabase = ModEvents.getVillageDatabase();
            VillageData villageData = villageDatabase.getData(player.getHomeVillageUUID());
            if (villageData == null) {
                source.sendFailure(Component.literal("No village data found."));
                return 0;
            }

            // Enforce once-per-week limit (skip for creative/admin).
            if (!playerSource.isCreative() && !villageData.canChangeAnimalType()) {
                source.sendFailure(Component.literal(
                        "Village animal type was recently changed. You can change it again after 7 days. Last changed: "
                                + villageData.getLastAnimalTypeChange()));
                return 0;
            }

            String lower = animalType.toLowerCase();
            villageData.setAnimalType(lower);
            villageData.setLastAnimalTypeChange(java.time.LocalDate.now().toString());
            villageDatabase.putData(villageData.getUUID(), villageData);

            source.sendSuccess(() -> Component.literal(
                    "Village breeding animal set to: " + lower + ". Only " + lower + "s may be bred in pastures."),
                    false);
        } catch (Exception ex) {
            source.sendFailure(Component.literal("Exception thrown - see log"));
            ex.printStackTrace();
        }
        return 0;
    }

    // Sets the village founder. Admin only.
    public static int setVillageFounder(CommandSourceStack source, String playerName) {
        try {
            Entity nullableSummoner = source.getEntity();
            Player playerSource = nullableSummoner instanceof Player ? (Player) nullableSummoner : null;
            if (playerSource == null) {
                source.sendFailure(Component.literal("Player not found."));
                return 0;
            }

            PlayerData player = ModEvents.getPlayerDatabase().getData(playerSource.getUUID());
            DataBase<UUID, VillageData> villageDatabase = ModEvents.getVillageDatabase();
            VillageData villageData = villageDatabase.getData(player.getHomeVillageUUID());
            if (villageData == null) {
                source.sendFailure(Component.literal("No village data found."));
                return 0;
            }

            villageData.setFounder(playerName);
            villageDatabase.putData(villageData.getUUID(), villageData);
            source.sendSuccess(() -> Component.literal("Village founder set to: " + playerName), false);
        } catch (Exception ex) {
            source.sendFailure(Component.literal("Exception thrown - see log"));
            ex.printStackTrace();
        }
        return 0;
    }

    // Sets the village level - NO CHUNK CHANGES!
    public static int setVillageLevel(CommandSourceStack source, Integer level) {
        try {
            Entity nullableSummoner = source.getEntity();
            Player playerSource = nullableSummoner instanceof Player ? (Player) nullableSummoner : null;

            // Load village from db that villager is in.
            PlayerData player = ModEvents.getPlayerDatabase().getData(playerSource.getUUID());
            DataBase<UUID, VillageData> villageDatabase = ModEvents.getVillageDatabase();
            VillageData village = villageDatabase.getData(player.getHomeVillageUUID());

            // Update level in db.
            village.setLevel(level);
            villageDatabase.putData(village.getUUID(), village);

            // Build a response message
            MutableComponent response = Component.literal("Village level set to: " + village.getLevel());
            MutableComponent finalResponse = response;
            source.sendSuccess(() -> finalResponse, false);
        } catch (Exception ex) {
            source.sendFailure(Component.literal("Exception thrown - see log"));
            ex.printStackTrace();
        }
        return 0;
    }

    // Level up a village.
    public static int levelUpVillage(CommandSourceStack source) {
        try {
            Entity nullableSummoner = source.getEntity();
            Player playerSource = nullableSummoner instanceof Player ? (Player) nullableSummoner : null;

            // Load village from db that villager is in.
            PlayerData player = ModEvents.getPlayerDatabase().getData(playerSource.getUUID());
            DataBase<UUID, VillageData> villageDatabase = ModEvents.getVillageDatabase();
            VillageData village = villageDatabase.getData(player.getHomeVillageUUID());

            int currLevel = village.getLevel();
            int newLevel = currLevel + 1;

            // STEP 2: Check if already max level.
            if (currLevel >= 10) {
                source.sendFailure(Component.literal("Village is already at max level 10."));
                return 0;
            }

            // STEP 3: Check all requirements for leveling up.
            boolean failedChecks = false;
            MutableComponent failureMessages = Component.literal("");

            // a. Check cost to level up
            int levelUpCost = currLevel * 300;
            if (player.getCoins() < levelUpCost) {
                failedChecks = true;
                failureMessages = failureMessages.append(
                        Component.literal("Not enough coins to level up village. Cost is " + levelUpCost + " coins\n"));
            }

            // b. Check minimum villagers requirement
            BlockPos villageCenter = village.getPosition().getWorldPosition();
            int requiredVillagers = newLevel * 2;
            int currentVillagers = countVillagers(villageCenter, playerSource.level(), 10);
            if (currentVillagers < requiredVillagers) {
                failedChecks = true;
                failureMessages = failureMessages
                        .append(Component.literal("Not enough villagers to level up village. Need " + requiredVillagers
                                + " villagers, have " + currentVillagers + "\n"));
            }

            // c. Check minimum claimed structures requirement
            int requiredStructures = newLevel * 2 - 2;
            int currentClaimedStructures = countVillageClaimedStructures(village, playerSource.level());
            if (currentClaimedStructures < requiredStructures) {
                failedChecks = true;
                failureMessages = failureMessages
                        .append(Component.literal("Not enough claimed structures to level up village. Need "
                                + requiredStructures + " claimed structures, have " + currentClaimedStructures + "\n"));
            }

            // d. If any checks failed, show all messages and return
            if (failedChecks) {
                source.sendFailure(failureMessages);
                return 0;
            }

            // STEP 4: Subtract money out of player. TODO helper method hide this???
            player.removeCoins(levelUpCost);
            ModEvents.getPlayerDatabase().putData(playerSource.getUUID(), player);

            // STEP 5: Update level in db.
            village.setLevel(currLevel + 1);

            // STEP 6: Add new chunks.
            int newChunksCount = addNewVillageChunks(village, playerSource);
            villageDatabase.putData(village.getUUID(), village);

            // Build a response message
            MutableComponent response = Component.literal("Village leveled up to: " + village.getLevel());
            response = response.append(Component.literal(", and added " + newChunksCount + " new chunks. \n"));
            MutableComponent finalResponse = response;
            source.sendSuccess(() -> finalResponse, false);
        } catch (Exception ex) {
            source.sendFailure(Component.literal("Exception thrown - see log"));
            ex.printStackTrace();
        }
        return 0;
    }

    // When a village levels, add new chunks all around.
    public static int addNewVillageChunks(VillageData village, Player playerSource) {
        PlayerData player = ModEvents.getPlayerDatabase().getData(playerSource.getUUID());
        DataBase<UUID, VillageData> villageDatabase = ModEvents.getVillageDatabase();
        UUID villageId = village.getUUID();

        // Add in all new chunks.....
        DataBase<Long, ChunkData> chunkDatabase = ModEvents.getChunkDataDatabase();

        // STEP 1: Add extra chunks until you equal (4 + level) * 2 + 1 squared chunks.
        int currentLevelSize = (4 + village.getLevel()) * 2 + 1;
        int previousLevelSize = (4 + (village.getLevel() - 1)) * 2 + 1;
        // TODO count actual chunks instead we have!
        int chunksCount = (currentLevelSize * currentLevelSize) - (previousLevelSize * previousLevelSize);
        int newChunksCount = chunksCount;
        ChunkPos centerChunkPos = village.getPosition();
        LOGGER.info("Level up village: " + village.getLevel() + " chunksCount = " + chunksCount);
        LOGGER.info("Center chunk at " + centerChunkPos.toString());

        // STEP 2: Loop and add all new chunks until done.
        // Add them randomly along the edge of current chunks.
        int tries = 0;
        // Calculate the range based on the village level
        int range = getVillageRadius(village);
        while (chunksCount > 0 && tries < 5000) {
            tries++;

            // Generate random x and z positions within the range, centered around the
            // current position
            int x = centerChunkPos.x + (int) ((Math.random() * 2 - 1) * range);
            int z = centerChunkPos.z + (int) ((Math.random() * 2 - 1) * range);
            ChunkPos chunkPos = new ChunkPos(x, z);

            // TODO MAKE METHOD.
            // RULE 1: Must be touching claims.
            if (!touchingVillageChunk(village, chunkPos)) {
                // LOGGER.info("DEBUG not adding chunk not near claims... " +
                // chunkPos.toString());
                continue;
            }

            // LOGGER.info("DEBUG Checking from " + centerChunkPos.toString() + " at
            // chunkPos = " + chunkPos.toString() + ", chunksCount = " + chunksCount + ",
            // tries = " + tries);

            // RULE 2: If an ocean chunk 2/3 chance skip it and continue.
            Holder<Biome> biome = playerSource.level().getBiome(chunkPos.getWorldPosition());
            if (biome.is(BiomeTags.IS_OCEAN)) {
                LOGGER.info("DEBUG notice Ocean chunk at: " + chunkPos.toString());
                if (Math.random() < 0.66) {
                    LOGGER.info("DEBUG SKIPPING OCEAN CHUNK ");
                    continue;
                }
                LOGGER.info("DEBUG adding ocean chunk ");
            }

            // If already claimed, skip.
            if (village.getClaimedChunks().contains(chunkPos)) {
                continue;
            }

            // RULE 3: If claimed by another village 50% chance to take it over.
            // If taking over another village chunk send text to world chat now.
            // Get chunk village.
            ChunkData chunkData = chunkDatabase.getData(chunkPos.toLong());
            if (chunkData != null) {
                VillageData otherVillage = villageDatabase.getData(chunkData.getVillageId());
                if (otherVillage != null && !otherVillage.getUUID().equals(villageId)) {
                    LOGGER.info("DEBUG: Checking other village owns it... at " + chunkPos.toString()
                            + ", other village = " + otherVillage.getName());
                    if (Math.random() < 0.5) {
                        LOGGER.info("DEBUG SKIPPING chunk owned by another village: " + otherVillage.getName());
                        continue;
                    }
                    LOGGER.info("DEBUG taking over chunk owned by another village: " + otherVillage.getName());

                    // Change chunk data.
                    chunkData.setVillageId(villageId);
                    chunkData.setType("village");
                    chunkDatabase.putData(chunkPos.toLong(), chunkData);

                    // Remove from old village.
                    otherVillage.removeClaimedChunk(chunkPos);
                    villageDatabase.putData(otherVillage.getUUID(), otherVillage);
                    village.addClaimedChunk(chunkPos);
                    villageDatabase.putData(village.getUUID(), otherVillage);

                    // Update structure claim flags for this chunk
                    if (playerSource.level() instanceof ServerLevel serverLevel) {
                        StructureCommand.updateStructuresInChunk(chunkPos, true, serverLevel);
                    }

                    // Add world chat message. Show center chunk pos.
                    MutableComponent message = Component.literal("Village " + village.getName()
                            + " took over " + otherVillage.getName() + " chunk"
                            + " at " + chunkPos.getMiddleBlockPosition(64).getX() + ", "
                            + chunkPos.getMiddleBlockPosition(64).getZ());
                    message.withStyle(ChatFormatting.RED);
                    playerSource.sendSystemMessage(message);
                    chunksCount--;
                    continue;
                }
            }

            // Add now.
            LOGGER.info("DEBUG  Adding new chunk at: " + chunkPos.toString());
            ChunkData chunk = new ChunkData("village", player.getId(), villageId);
            chunkDatabase.putData(chunkPos.toLong(), chunk);
            village.addClaimedChunk(chunkPos);

            // Update structure claim flags for this chunk
            if (playerSource.level() instanceof ServerLevel serverLevel) {
                StructureCommand.updateStructuresInChunk(chunkPos, true, serverLevel);
            }

            chunksCount--;
        } // while

        // Log an alert if we hit max without getting the new ones done.
        if (tries >= 5000) {
            LOGGER.info("DEBUG TODO MAX tries hit, not adding new chunks. ");
            // TODO add message to player.
            MutableComponent message = Component.literal("Error Village " + village.getName() +
                    " has hit max tries for adding new chunks, please report to dev.");
            message.withStyle(ChatFormatting.RED);
            playerSource.sendSystemMessage(message);
        }
        villageDatabase.putData(village.getUUID(), village); // Save back.

        // TODO make a cleanup method that auto claims any chunk that has 3+ of four
        // sides claimed, makes sure there are no islands!!!
        // TODO make a cleanup method that auto claims any chunk that has 3+ of four
        // sides claimed, makes sure there are no islands!!!
        // TODO make a cleanup method that auto claims any chunk that has 3+ of four
        // sides claimed, makes sure there are no islands!!!

        cleanupVillageChunks(playerSource);

        return newChunksCount;
    }

    // TODO make this a command to test it with...
    // dont actually claim until test is good and clear.
    public static int cleanupVillageChunks(Player playerSource) {
        // TODO make a cleanup method that auto claims any chunk that has 3+ of four
        // sides claimed, makes sure there are no islands!!!
        // Loop over all chunks in the area near village by radius,
        // and check if they have 3+ sides claimed.
        // If so, claim them also.
        // DataBase<Long, ChunkData> chunkDatabase = ModEvents.getChunkDataDatabase();
        DataBase<UUID, VillageData> villageDatabase = ModEvents.getVillageDatabase();

        PlayerData playerData = ModEvents.getPlayerDatabase().getData(playerSource.getUUID());
        VillageData village = villageDatabase.getData(playerData.getHomeVillageUUID());

        int cleanedUpCount = 0;
        int radius = 10 + 3 * village.getLevel(); // Increase radius with level.

        for (int y = -radius; y <= radius; y++) {
            for (int x = -radius; x <= radius; x++) {
                ChunkPos chunkPos = new ChunkPos(village.getPosition().x + x, village.getPosition().z + y);

                // Check if chunk is already claimed.
                if (village.getClaimedChunks().contains(chunkPos)) {
                    continue; // Skip already claimed chunks.
                }

                // Check how many sides are claimed.
                int sidesClaimed = 0;
                // Check all 4 horizontal sides of the chunk.
                for (Direction direction : Direction.Plane.HORIZONTAL) {
                    ChunkPos neighborChunk = new ChunkPos(chunkPos.x + direction.getStepX(),
                            chunkPos.z + direction.getStepZ());
                    if (village.getClaimedChunks().contains(neighborChunk)) {
                        sidesClaimed++;
                    }
                }

                // If all 4 sides are claimed, auto-claim this chunk (it's fully surrounded).
                if (sidesClaimed >= 4) {
                    LOGGER.info(
                            "DEBUG Reclaiming surrounded chunk " + chunkPos.toString() + " with all 4 sides claimed.");

                    DataBase<Long, ChunkData> chunkDatabase = ModEvents.getChunkDataDatabase();
                    ChunkData existing = chunkDatabase.getData(chunkPos.toLong());
                    // Only claim if unclaimed or belongs to this village already.
                    if (existing == null || village.getUUID().equals(existing.getVillageId())) {
                        ChunkData chunkData = new ChunkData("village", playerData.getId(), village.getUUID());
                        chunkDatabase.putData(chunkPos.toLong(), chunkData);
                        village.addClaimedChunk(chunkPos);

                        // Update structure claim flags for this chunk.
                        if (playerSource.level() instanceof ServerLevel serverLevel) {
                            StructureCommand.updateStructuresInChunk(chunkPos, true, serverLevel);
                        }

                        cleanedUpCount++;
                    }
                }
            }
        }

        // Save the updated village data.
        villageDatabase.putData(village.getUUID(), village);

        return cleanedUpCount;
    }

    // Shared helper: calculate and charge upkeep for a single village. Returns
    // coins charged.
    public static int processVillageUpkeep(VillageData village, Level level) {
        int dailyCost = getDailyCost(village);
        int claimedStructures = countVillageClaimedStructures(village, level);
        int dailyCoins = claimedStructures * 100;
        int totalCost = Math.max(0, dailyCost - dailyCoins);
        village.subtractCoins(totalCost);
        village.markDailyRanToday(); // stamp the date so we don't charge again today
        ModEvents.getVillageDatabase().putData(village.getUUID(), village);
        LOGGER.info("Village upkeep: {} charged {} coins (cost={}, earned={}).",
                village.getName(), totalCost, dailyCost, dailyCoins);

        // Broadcast upkeep result to all online players.
        Component broadcastMsg = Component.literal("[Village] " + village.getName()
                + " daily upkeep: -" + totalCost + " coins (cost=" + dailyCost + ", earned=" + dailyCoins + ")")
                .withStyle(ChatFormatting.YELLOW);
        if (level.getServer() != null) {
            level.getServer().getPlayerList().broadcastSystemMessage(broadcastMsg, false);
        }

        return totalCost;
    }

    public static int runVillageDailyUpkeep(CommandSourceStack source) {
        try {
            Entity nullableSummoner = source.getEntity();
            Player playerSource = nullableSummoner instanceof Player ? (Player) nullableSummoner : null;

            // Load village from db that player is in.
            PlayerData player = ModEvents.getPlayerDatabase().getData(playerSource.getUUID());
            VillageData village = ModEvents.getVillageDatabase().getData(player.getHomeVillageUUID());

            // Calculate and charge upkeep via shared helper.
            int totalCost = processVillageUpkeep(village, playerSource.level());

            MutableComponent response = Component.literal("Village daily upkeep ran, charged " + totalCost + " coins.");
            MutableComponent finalResponse = response;
            source.sendSuccess(() -> finalResponse, false);
        } catch (Exception ex) {
            source.sendFailure(Component.literal("Exception thrown - see log"));
            ex.printStackTrace();
        }
        return 0;
    }

    // Method to loop over all chunks in a village, and get the center spot biome
    // there.
    // Then unique and sort count the list.
    // Then show the list of biomes.
    // Two methods, one to get one to show, later can store on an object.
    // Uses current player village.
    public static int showVillageBiomes(CommandSourceStack source) {
        try {
            Entity nullableSummoner = source.getEntity();
            Player playerSource = nullableSummoner instanceof Player ? (Player) nullableSummoner : null;

            // Get the village data for the player.
            DataBase<UUID, PlayerData> playerDataDB = ModEvents.getPlayerDatabase();
            PlayerData playerData = playerDataDB.getData(playerSource.getUUID());
            if (playerData.getHomeVillageUUID() == null) {
                source.sendFailure(Component.literal("Player is not in a village right now."));
                return 0;
            }

            // Get the village data for the player.
            DataBase<UUID, VillageData> villageDataDB = ModEvents.getVillageDatabase(source.getLevel());
            VillageData villageData = villageDataDB.getData(playerData.getHomeVillageUUID());

            // Get the list of biomes in the village.
            Map<String, Integer> biomes = getVillageBiomes(villageData);

            // Show the list of biomes.
            // TODO first line yellow.
            MutableComponent response = Component.literal("Biomes in village: ");
            for (Map.Entry<String, Integer> entry : biomes.entrySet()) {
                String biome = entry.getKey();
                // Remove mod tag, dont need really. With regex all before the ":"
                String biomeName = biome.toString().replaceAll("^[^:]+:", "");

                int count = entry.getValue();
                response.append(Component.literal(biomeName + " (" + count + "), \n"));
            }
            MutableComponent finalResponse = response;
            source.sendSuccess(() -> finalResponse, false);
        } catch (Exception ex) {
            source.sendFailure(Component.literal("Exception thrown - see log"));
            ex.printStackTrace();
        }
        return 0;
    }

    public static Map<String, Integer> getVillageBiomes(VillageData villageData) {
        if (villageData == null) {
            LOGGER.error("Village data is null, cannot get biomes.");
            return Collections.emptyMap();
        }
        Level level = Minecraft.getInstance().level;

        // Loop over each chunk in territory.
        // Get the biome for each chunk.
        List<ChunkPos> chunks = villageData.getClaimedChunks();
        // Count of each biome here.
        Map<String, Integer> biomeCounts = new HashMap<>();

        for (ChunkPos chunk : chunks) {
            // Get the biome for the chunk center.
            BlockPos blockPos = chunk.getMiddleBlockPosition(64); // default height notice.
            // Get height at that position.
            int height = level.getHeight(Heightmap.Types.MOTION_BLOCKING, blockPos.getX(), blockPos.getZ());
            blockPos = new BlockPos(blockPos.getX(), height, blockPos.getZ());

            // Update blockPos
            assert level != null;
            Biome biome = level.getBiome(blockPos).value();
            ResourceLocation biomeName = level.registryAccess().registryOrThrow(Registries.BIOME).getKey(biome);
            if (biomeName == null) {
                LOGGER.info("Biome name is null for chunk " + chunk);
                continue;
            }

            // Add to a count of biomes hash.
            String biomeString = biomeName.toString();
            LOGGER.info("DEBUG Biome name for chunk " + chunk + " is " + biomeString);
            if (biomeCounts.containsKey(biomeString)) {
                // Increment the count for this biome
                biomeCounts.put(biomeString, biomeCounts.get(biomeString) + 1);
            } else {
                // Add this biome to the map with an initial count of 1
                biomeCounts.put(biomeString, 1);
            }
        }

        // Sort the map by count in descending order
        Map<String, Integer> sortedBiomeCounts = biomeCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .collect(LinkedHashMap::new, (map, entry) -> map.put(entry.getKey(), entry.getValue()),
                        LinkedHashMap::putAll);

        return sortedBiomeCounts;
    }

    public static int showVillagefarms(CommandSourceStack source) {
        try {
            Entity nullableSummoner = source.getEntity();
            Player summoner = nullableSummoner instanceof Player ? (Player) nullableSummoner : null;
            if (summoner == null) {
                source.sendFailure(Component.literal("Player not found."));
                return 0;
            }

            // Get the home village.
            DataBase<UUID, PlayerData> playerDataDB = ModEvents.getPlayerDatabase();
            PlayerData playerData = playerDataDB.getData(summoner.getUUID());
            if (playerData.getHomeVillageUUID() == null) {
                source.sendFailure(Component.literal("Player is not in a village right now."));
                return 0;
            }

            // Get the village data.
            DataBase<UUID, VillageData> villageDataDB = ModEvents.getVillageDatabase(source.getLevel());
            VillageData villageData = villageDataDB.getData(playerData.getHomeVillageUUID());

            // Get the list of farms in the village.
            List<ChunkPos> farmChunks = getVillageFarms(villageData);

            // Show the list of farms.
            MutableComponent response = Component.literal("Farms in village: ");
            ServerLevel level = source.getLevel();
            for (ChunkPos chunkPos : farmChunks) {
                response.append(Component.literal(chunkPos.toString() + "\n "));

                // Get y highest point in center of chunk, not motion blocking.
                BlockPos blockPos = chunkPos.getMiddleBlockPosition(64); // default height notice.
                int height = level.getHeight(Heightmap.Types.WORLD_SURFACE, blockPos.getX(), blockPos.getZ());
                blockPos = chunkPos.getMiddleBlockPosition(height); // default height notice.
                LOGGER.info("DEBUG Chunk " + chunkPos.toString() + " has height " + height);
                // TODO TEST

                // Get all biomes in this chunk at surface level with counts.
                // TODO this is showing lush caves, our y value is still off somehow....
                PlotCommand.getChunkBiomes(blockPos, level)
                        .forEach((biomeName, count) -> response
                                .append(Component.literal(biomeName + " (" + count + "), ")));
                // TODO add line break..

                BlockPos cropBlockPos = chunkPos.getMiddleBlockPosition(height);
                String crops = getCropsPlanted(cropBlockPos, level);
                if (crops != null && !crops.isEmpty()) {
                    response.append(Component.literal("\n with crops: " + crops + ".\n"));
                } else {
                    response.append(Component.literal(" with no crops planted.\n"));
                }
            }
            // Show all veggies you can or cannot plant? thats alot, sub command?
            // And currently planted counts :} if on flat farm.

            MutableComponent finalResponse = response;
            source.sendSuccess(() -> finalResponse, false);
        } catch (Exception ex) {
            source.sendFailure(Component.literal("Exception thrown - see log"));
            ex.printStackTrace();
        }
        return 0;
    }

    // Helper methods
    public static List<ChunkPos> getVillageFarms(VillageData villageData) {
        // Get the list of claimed chunks in the village.
        List<ChunkPos> claimedChunks = villageData.getClaimedChunks();
        List<ChunkPos> farmChunks = new ArrayList<>();

        // Loop over each chunk and check if it is a farm.
        for (ChunkPos chunkPos : claimedChunks) {
            ChunkData chunkData = ModEvents.getChunkDataDatabase().getData(chunkPos.toLong());
            if (chunkData != null && chunkData.getType().equalsIgnoreCase("farm")) {
                farmChunks.add(chunkPos);
            }
        }

        return farmChunks;
    }

    // Make sure at least one neighbor is a village chunk we own.
    public static boolean touchingVillageChunk(VillageData village, ChunkPos chunkPos) {
        DataBase<Long, ChunkData> dataBase = ModEvents.getChunkDataDatabase();

        // Check four neighbors nearby.
        ChunkData chunkData = null;
        chunkData = dataBase.getData(new ChunkPos(chunkPos.x + 1, chunkPos.z).toLong());
        if (chunkData != null && chunkData.getVillageId().equals(village.getUUID())) {
            return true;
        }
        chunkData = dataBase.getData(new ChunkPos(chunkPos.x - 1, chunkPos.z).toLong());
        if (chunkData != null && chunkData.getVillageId().equals(village.getUUID())) {
            return true;
        }
        chunkData = dataBase.getData(new ChunkPos(chunkPos.x, chunkPos.z + 1).toLong());
        if (chunkData != null && chunkData.getVillageId().equals(village.getUUID())) {
            return true;
        }
        chunkData = dataBase.getData(new ChunkPos(chunkPos.x, chunkPos.z - 1).toLong());
        if (chunkData != null && chunkData.getVillageId().equals(village.getUUID())) {
            return true;
        }

        return false;
    }

    // TODO MOVE these over to a manager.
    // Get closest village to location.
    public static VillageData getClosestVillage(BlockPos pos) {
        DataBase<UUID, VillageData> dataBase = ModEvents.getVillageDatabase();
        Collection<VillageData> dataList = dataBase.getValues();
        if (dataList == null || dataList.isEmpty()) {
            return null;
        }

        // Loop and find the closest village.
        VillageData closestVillage = null;
        int closestDistance = Integer.MAX_VALUE;
        for (VillageData data : dataList) {
            closestVillage = data;
            // Use abs manhattan distance formula
            int distance = Math.abs(data.getPosition().x - pos.getX()) +
                    Math.abs(data.getPosition().z - pos.getZ());
            if (distance < closestDistance) {
                closestDistance = distance;
            }
        }

        return closestVillage;
    }

    // Check if village name is unique.
    public static boolean isVillageNameUnique(String villageName) {
        DataBase<UUID, VillageData> dataBase = ModEvents.getVillageDatabase();
        Collection<VillageData> dataList = dataBase.getValues();
        if (dataList == null || dataList.isEmpty()) {
            return true;
        }

        // Loop and check all village names.
        for (VillageData data : dataList) {
            if (data.getName().equalsIgnoreCase(villageName)) {
                return false;
            }
        }
        return true;
    }

    public static String findVillageByChunkPos(ChunkPos chunkPos) {
        DataBase<Long, ChunkData> chunkDataDatabase = ModEvents.getChunkDataDatabase();
        ChunkData chunkData = chunkDataDatabase.getData(chunkPos.toLong());
        if (chunkData == null) {
            return null;
        }

        DataBase<UUID, VillageData> villageDataDB = ModEvents.getVillageDatabase();
        VillageData village = villageDataDB.getData(chunkData.getVillageId());
        if (village == null) {
            return null;
        }

        return village.getName();
    }

    /**
     * Shows structures within the player's village (where chunk data exists).
     * Only shows structures that are in chunks claimed by the player's village.
     */
    public static int showVillageStructures(CommandSourceStack source, int count, String filter) {
        try {
            // Validate filter parameter
            if (!"all".equals(filter) && !"claimed".equals(filter) && !"unclaimed".equals(filter)) {
                source.sendFailure(Component.literal("Invalid filter: " + filter + ". Use: all, claimed, unclaimed"));
                return 0;
            }

            Entity nullablePlayer = source.getEntity();
            Player playerSource = nullablePlayer instanceof Player ? (Player) nullablePlayer : null;
            if (playerSource == null) {
                source.sendFailure(Component.literal("This command can only be used by a player."));
                return 0;
            }

            ServerLevel world = (ServerLevel) source.getLevel();
            var gameStructureDatabase = ModEvents.getGameStructureDatabase(world);

            if (gameStructureDatabase.getSize() == 0) {
                source.sendSystemMessage(
                        Component.literal("No structures found in database.").withStyle(ChatFormatting.YELLOW));
                return 0;
            }

            // Get player's village data
            DataBase<UUID, PlayerData> playerDatabase = ModEvents.getPlayerDatabase();
            PlayerData playerData = playerDatabase.getData(playerSource.getUUID());
            if (playerData == null || playerData.getHomeVillageUUID() == null) {
                source.sendFailure(Component.literal("You are not part of a village."));
                return 0;
            }

            DataBase<UUID, VillageData> villageDatabase = ModEvents.getVillageDatabase();
            VillageData village = villageDatabase.getData(playerData.getHomeVillageUUID());
            if (village == null) {
                source.sendFailure(Component.literal("No village data found."));
                return 0;
            }

            // Get village position for distance calculation
            BlockPos villagePos = village.getPosition().getWorldPosition();

            // STEP 2: Get list of structures in the village
            List<Map.Entry<Long, GameStructureData>> structuresList = getVillageStructuresList(village, world);
            if (structuresList.isEmpty()) {
                source.sendSystemMessage(
                        Component.literal("No structures found in your village.").withStyle(ChatFormatting.YELLOW));
                return 0;
            }

            // STEP 2.5: Apply filter if specified
            if (!"all".equals(filter)) {
                List<Map.Entry<Long, GameStructureData>> filteredList = new ArrayList<>();
                for (Map.Entry<Long, GameStructureData> entry : structuresList) {
                    GameStructureData structureData = entry.getValue();
                    boolean isClaimed = structureData.isOnClaimedPlot();

                    if ("claimed".equals(filter) && isClaimed) {
                        filteredList.add(entry);
                    } else if ("unclaimed".equals(filter) && !isClaimed) {
                        filteredList.add(entry);
                    }
                }
                structuresList = filteredList;

                if (structuresList.isEmpty()) {
                    source.sendSystemMessage(Component.literal("No " + filter + " structures found in your village.")
                            .withStyle(ChatFormatting.YELLOW));
                    return 0;
                }
            }

            // STEP 3: Sort by distance from current player position.
            BlockPos playerPos = playerSource.blockPosition();
            structuresList
                    .sort(Comparator.comparingDouble(entry -> entry.getValue().getCenterPos().distSqr(playerPos)));

            // STEP 4: Show the structures in nice format.
            String filterText = "all".equals(filter) ? "" : " (" + filter + ")";
            source.sendSystemMessage(Component.literal("Structures in village '" + village.getName() + "'" + filterText
                    + " (showing first " + count + " by distance):").withStyle(ChatFormatting.GOLD));
            int displayed = 0;

            // Get player's current chunk position for comparison
            ChunkPos playerChunk = new ChunkPos(playerSource.blockPosition());

            for (Map.Entry<Long, GameStructureData> entry : structuresList) {
                if (displayed >= count)
                    break;

                Long structureId = entry.getKey();
                GameStructureData structureData = entry.getValue();

                // Calculate distance for display (from player position)
                int distance = (int) Math.sqrt(structureData.getCenterPos().distSqr(playerPos));

                // Check if player is standing in the same chunk as this structure
                ChunkPos structureChunk = new ChunkPos(structureData.getCenterPos());
                boolean isPlayerInSameChunk = playerChunk.equals(structureChunk);

                MutableComponent message = Component.literal(
                        (displayed + 1) + ". " + structureData.getName() +
                                ": " + structureData.getCenterPos().toShortString() +
                                " d=" + distance);

                // Color the entire line light blue if player is in the same chunk
                if (isPlayerInSameChunk) {
                    message = message.withStyle(ChatFormatting.AQUA);
                } else {
                    message = message.withStyle(ChatFormatting.WHITE);
                }

                // STEP 5: Add status with conditional color.
                String status = "";
                if (structureData.isOnClaimedPlot())
                    status = " CLAIMED";
                else if (structureData.wasVisited())
                    status = " VISITED";
                if (!status.isEmpty()) {
                    ChatFormatting statusColor;
                    if (structureData.isOnClaimedPlot() && structureData.wasVisited()) {
                        statusColor = ChatFormatting.GREEN; // Both claimed and visited
                    } else if (structureData.isOnClaimedPlot() || structureData.wasVisited()) {
                        statusColor = ChatFormatting.YELLOW; // Either claimed or visited
                    } else {
                        statusColor = ChatFormatting.WHITE; // Neither claimed nor visited
                    }
                    message.append(Component.literal(status).withStyle(statusColor));
                }

                // Log all fields with id and type
                LOGGER.info("VillageStructureData: " + structureData.getName() + " (" + structureData.getType()
                        + ") at " + structureData.getCenterPos().toShortString() + " ID: " + structureId + " d="
                        + distance + status);

                source.sendSystemMessage(message);
                displayed++;
            }

            source.sendSystemMessage(
                    Component
                            .literal("Total structures in village" + filterText + ": " + structuresList.size() + " (of "
                                    + gameStructureDatabase.getSize() + " total in database)")
                            .withStyle(ChatFormatting.GREEN));

            return 0;

        } catch (Exception ex) {
            LOGGER.error("Error showing village structures: " + ex.getMessage());
            ex.printStackTrace();
            source.sendFailure(Component.literal("Error showing village structures: " + ex.getMessage()));
            return 0;
        }
    }

    // Helper method to get structures in the village
    public static List<Map.Entry<Long, GameStructureData>> getVillageStructuresList(VillageData village, Level level) {
        List<Map.Entry<Long, GameStructureData>> structuresList = new ArrayList<>();
        for (Long structureId : ModEvents.getGameStructureDatabase(level).getKeys()) {
            GameStructureData structureData = ModEvents.getGameStructureDatabase(level).getData(structureId);
            if (structureData == null) {
                continue;
            }

            // TODO need to show these sometimes, add another filter for this method...

            // Skip underground structures (below Y=60) unless they have been visited
            if (structureData.getCenterPos().getY() < 60 && !structureData.wasVisited()) {
                continue;
            }

            // Check if this structure's chunk belongs to THIS village specifically.
            ChunkPos structureChunk = new ChunkPos(structureData.getCenterPos());
            DataBase<Long, ChunkData> chunkDatabase = ModEvents.getChunkDataDatabase();
            ChunkData chunkData = chunkDatabase.getData(structureChunk.toLong());

            // Only include structures in chunks owned by this village (not any village).
            if (chunkData != null && village.getUUID().equals(chunkData.getVillageId())) {
                structuresList.add(new AbstractMap.SimpleEntry<>(structureId, structureData));
            }
        }
        return structuresList;
    }

    /**
     * Counts structures in the village based on the specified filter.
     * 
     * @param village The village to count structures for
     * @param level   The server level for database access
     * @param filter  The filter to apply ("claimed", "unclaimed", or "all")
     * @return The number of structures matching the filter
     */
    public static int countVillageStructures(VillageData village, Level level, String filter) {
        List<Map.Entry<Long, GameStructureData>> structuresList = getVillageStructuresList(village, level);

        if ("all".equals(filter)) {
            return structuresList.size();
        }

        int count = 0;
        for (Map.Entry<Long, GameStructureData> entry : structuresList) {
            GameStructureData structureData = entry.getValue();
            boolean isClaimed = structureData.isOnClaimedPlot();

            if ("claimed".equals(filter) && isClaimed) {
                count++;
            } else if ("unclaimed".equals(filter) && !isClaimed) {
                count++;
            }
        }

        return count;
    }

    /**
     * Counts how many structures in the village are claimed.
     * 
     * @param village The village to count claimed structures for
     * @param level   The server level for database access
     * @return The number of claimed structures in the village
     */
    public static int countVillageClaimedStructures(VillageData village, Level level) {
        int claimedCount = countVillageStructures(village, level, "claimed");

        // NOTICE: Includes the village center.
        return claimedCount;
    }

    /**
     * Counts how many structures in the village are unclaimed.
     * 
     * @param village The village to count unclaimed structures for
     * @param level   The server level for database access
     * @return The number of unclaimed structures in the village
     */
    public static int countVillageUnclaimedStructures(VillageData village, Level level) {
        return countVillageStructures(village, level, "unclaimed");
    }

    /**
     * Finds and displays nearby villagers within a specified chunk radius.
     * 
     * @param source The command source
     * @return 0 on success
     */
    public static int findNearVillagers(CommandSourceStack source) {
        Entity nullableSummoner = source.getEntity();
        Player playerSource = nullableSummoner instanceof Player ? (Player) nullableSummoner : null;
        if (playerSource == null) {
            source.sendFailure(Component.literal("This command can only be used by a player."));
            return 0;
        }

        // RULE: Player MUST be standing within village chunks.
        if (!isPlayerInVillage(source.getLevel(), playerSource)) {
            source.sendFailure(Component.literal("You must be standing within a village chunk to use this command."));
            return 0;
        }

        // Get list of nearby villagers with default 10 chunk radius
        List<LivingEntity> villagersList = getVillagersList(playerSource.blockPosition(), source.getLevel(), 10);
        int count = villagersList.size();

        MutableComponent response = Component.literal("Found " + count + " villagers nearby.");
        source.sendSuccess(() -> response, false);

        // Sort villagers by distance
        List<LivingEntity> sortedList = new ArrayList<>(villagersList);
        sortedList.sort(Comparator.comparingDouble(v -> v.distanceToSqr(playerSource)));

        // Show details for each villager
        for (LivingEntity v : sortedList) {
            LOGGER.info("\nLIST Villager found: ");
            LOGGER.info("Villager name: *" + v.getName().getString() + "*");
            LOGGER.info("Villager position: " + v.blockPosition());

            // Get villager data
            Villager villager = (Villager) v;
            VillagerData d = villager.getVillagerData();
            String profession = d.getProfession().toString();
            String type = d.getType().toString();
            LOGGER.info("Villager type: " + type);

            // Calculate distance
            BlockPos villagerPos = v.blockPosition();
            int distance = Math.abs(villagerPos.getX() - playerSource.blockPosition().getX()) +
                    Math.abs(villagerPos.getY() - playerSource.blockPosition().getY()) +
                    Math.abs(villagerPos.getZ() - playerSource.blockPosition().getZ());

            // Log name + UUID so you can copy them for npcData/ profile files.
            LOGGER.info("Villager: name='{}', uuid={}, profession={}", v.getName().getString(), villager.getUUID(),
                    profession);

            // Chat shows name, profession, coordinates, and a clickable [TP] link in
            // creative.
            MutableComponent villagerResponse = Component
                    .literal(" - " + v.getName().getString() + " (" + profession + ") d=" + distance
                            + " [" + villagerPos.getX() + "," + villagerPos.getY() + "," + villagerPos.getZ() + "]");
            // Append a clickable [TP] link in creative mode.
            if (playerSource instanceof ServerPlayer sp && sp.getAbilities().instabuild) {
                String tpCmd = "/tp " + villagerPos.getX() + " " + villagerPos.getY() + " " + villagerPos.getZ();
                MutableComponent tpLink = Component.literal(" [TP]")
                        .withStyle(s -> s
                                .withColor(ChatFormatting.AQUA)
                                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, tpCmd)));
                villagerResponse.append(tpLink);
            }
            source.sendSuccess(() -> villagerResponse, false);
        }

        return 0;
    }

    /**
     * Gets a list of nearby villagers within a specified chunk radius.
     * 
     * @param centerPos      The center position to search around
     * @param level          The level to search in
     * @param radiusInChunks The radius to search in chunks
     * @return List of nearby villagers
     */
    public static List<LivingEntity> getVillagersList(BlockPos centerPos, Level level, int radiusInChunks) {
        TargetingConditions playersTarget = TargetingConditions.forNonCombat().ignoreInvisibilityTesting()
                .ignoreLineOfSight();
        List<? extends LivingEntity> list = level.getNearbyEntities(
                Villager.class,
                playersTarget,
                null, // No specific entity to exclude
                new net.minecraft.world.phys.AABB(centerPos).inflate(radiusInChunks * 16) // Convert chunks to blocks
        );
        return new ArrayList<>(list);
    }

    /**
     * Counts the number of villagers within a specified chunk radius.
     * 
     * @param centerPos      The center position to search around
     * @param level          The level to search in
     * @param radiusInChunks The radius to search in chunks
     * @return The number of villagers
     */
    public static int countVillagers(BlockPos centerPos, Level level, int radiusInChunks) {
        List<LivingEntity> villagersList = getVillagersList(centerPos, level, radiusInChunks);
        return villagersList.size();
    }

    /**
     * Checks if a player is standing within their home village chunk.
     * 
     * @param level  The level to check in
     * @param player The player to check for
     * @return true if the player is in their home village chunk, false otherwise
     */
    public static boolean isPlayerInVillage(Level level, Player player) {
        ChunkPos playerChunk = new ChunkPos(player.blockPosition());
        DataBase<Long, ChunkData> chunkDatabase = ModEvents.getChunkDataDatabase();
        ChunkData chunkData = chunkDatabase.getData(playerChunk.toLong());

        // Check if the chunk has data
        if (chunkData == null) {
            return false;
        }

        // Check if the chunk belongs to the player's home village
        PlayerData playerData = ModEvents.getPlayerDatabase().getData(player.getUUID());
        if (playerData == null || playerData.getHomeVillageUUID() == null) {
            return false;
        }

        return chunkData.getVillageId().equals(playerData.getHomeVillageUUID());
    }
}
