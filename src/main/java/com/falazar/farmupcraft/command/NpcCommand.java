package com.falazar.farmupcraft.command;

import com.falazar.farmupcraft.AIManager;
import com.falazar.farmupcraft.currency.Coin;
import com.falazar.farmupcraft.data.ChunkData;
import com.falazar.farmupcraft.data.NpcData;
import com.falazar.farmupcraft.data.PlayerData;
import com.falazar.farmupcraft.data.VillageData;
import com.falazar.farmupcraft.database.DataBase;
import com.falazar.farmupcraft.events.ModEvents;
import com.falazar.farmupcraft.registry.CoinRegistry;
import com.falazar.farmupcraft.registry.FUCRegistries;
import com.falazar.farmupcraft.util.CustomLogger;
import com.falazar.farmupcraft.util.NpcDataLoader;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.world.ForgeChunkManager;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static com.falazar.farmupcraft.FarmUpCraft.MODID;
import static com.falazar.farmupcraft.command.PlayerCommand.getPlayer;

public class NpcCommand {
    public static final CustomLogger LOGGER = new CustomLogger(NpcCommand.class.getSimpleName());

    public static void register(CommandDispatcher<CommandSourceStack> pDispatcher) {
        // Define the base command "npc"
        LiteralArgumentBuilder<CommandSourceStack> builder = Commands.literal("npc");

        // Define the "create" sub-command (admin only)
        LiteralArgumentBuilder<CommandSourceStack> createBuilder = Commands.literal("create")
                .requires(source -> source.hasPermission(2)) // Permission level 2+ (admin)
                .then(Commands.argument("name", StringArgumentType.string())
                        .executes(context -> {
                            String name = StringArgumentType.getString(context, "name");
                            return createNpc(context.getSource(), name);
                        }));
        builder.then(createBuilder);

        // Define the "info" sub-commands
        LiteralArgumentBuilder<CommandSourceStack> infoBuilder = Commands.literal("info")
                .then(Commands.argument("name", StringArgumentType.string())
                        .executes(context -> {
                            String name = StringArgumentType.getString(context, "name");
                            return showNpcInfo(context.getSource(), name);
                        }));
        builder.then(infoBuilder);

        // Define the "setdescription" sub-command (admin only)
        LiteralArgumentBuilder<CommandSourceStack> setDescriptionBuilder = Commands.literal("setdescription")
                .requires(source -> source.hasPermission(2)) // Permission level 2+ (admin)
                .then(Commands.argument("name", StringArgumentType.string())
                        .then(Commands.argument("description", StringArgumentType.string())
                                .executes(context -> {
                                    String name = StringArgumentType.getString(context, "name");
                                    String description = StringArgumentType.getString(context, "description");
                                    return setNpcDescription(context.getSource(), name, description);
                                })));
        builder.then(setDescriptionBuilder);

        // Define the "createskleleton" sub-command (admin only)
        LiteralArgumentBuilder<CommandSourceStack> createSkeletonBuilder = Commands.literal("createskeleton")
                .requires(source -> source.hasPermission(2)) // Permission level 2+ (admin)
                .then(Commands.argument("name", StringArgumentType.string())
                        .executes(context -> {
                            String name = StringArgumentType.getString(context, "name");
                            return createNpcSkeleton(context.getSource(), name);
                        }));
        builder.then(createSkeletonBuilder);

        // Define the "createcow" sub-command (admin only)
        LiteralArgumentBuilder<CommandSourceStack> createCowBuilder = Commands.literal("createcow")
                .requires(source -> source.hasPermission(2)) // Permission level 2+ (admin)
                .then(Commands.argument("name", StringArgumentType.string())
                        .executes(context -> {
                            String name = StringArgumentType.getString(context, "name");
                            return createNpcCow(context.getSource(), name);
                        }));
        builder.then(createCowBuilder);

        // initvillagers — scan village, create AI profiles for all that lack one (admin
        // only)
        LiteralArgumentBuilder<CommandSourceStack> initVillagersBuilder = Commands.literal("initvillagers")
                .requires(source -> source.hasPermission(2))
                .executes(context -> initVillagers(context.getSource()));
        builder.then(initVillagersBuilder);

        // Define the "rename" sub-command (admin only)
        LiteralArgumentBuilder<CommandSourceStack> renameBuilder = Commands.literal("rename")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("oldName", StringArgumentType.string())
                        .then(Commands.argument("newName", StringArgumentType.string())
                                .executes(context -> {
                                    String oldName = StringArgumentType.getString(context, "oldName");
                                    String newName = StringArgumentType.getString(context, "newName");
                                    return renameVillager(context.getSource(), oldName, newName);
                                })));
        builder.then(renameBuilder);

        // redoprofile — regenerate AI profile for one villager, keeping existing text
        // as context
        LiteralArgumentBuilder<CommandSourceStack> redoProfileBuilder = Commands.literal("redoprofile")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("name", StringArgumentType.string())
                        .executes(context -> {
                            String name = StringArgumentType.getString(context, "name");
                            return redoProfile(context.getSource(), name);
                        }));
        builder.then(redoProfileBuilder);

        // cleanprofiles — delete error-state profiles from npcData/
        LiteralArgumentBuilder<CommandSourceStack> cleanProfilesBuilder = Commands.literal("cleanprofiles")
                .requires(source -> source.hasPermission(2))
                .executes(context -> cleanProfiles(context.getSource()));
        builder.then(cleanProfilesBuilder);

        // setkid — toggle forever-kid flag on a named villager
        LiteralArgumentBuilder<CommandSourceStack> setKidBuilder = Commands.literal("setkid")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("name", StringArgumentType.string())
                        .executes(context -> {
                            String name = StringArgumentType.getString(context, "name");
                            return setForeverKid(context.getSource(), name, true);
                        }));
        builder.then(setKidBuilder);

        LiteralArgumentBuilder<CommandSourceStack> unsetKidBuilder = Commands.literal("unsetkid")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("name", StringArgumentType.string())
                        .executes(context -> {
                            String name = StringArgumentType.getString(context, "name");
                            return setForeverKid(context.getSource(), name, false);
                        }));
        builder.then(unsetKidBuilder);

        // shownames / hidenames — toggle floating name tags on ALL village villagers
        LiteralArgumentBuilder<CommandSourceStack> showNamesBuilder = Commands.literal("shownames")
                .executes(context -> setVillagerNamesVisible(context.getSource(), true));
        builder.then(showNamesBuilder);

        LiteralArgumentBuilder<CommandSourceStack> hideNamesBuilder = Commands.literal("hidenames")
                .executes(context -> setVillagerNamesVisible(context.getSource(), false));
        builder.then(hideNamesBuilder);

        // showname / hidename <name> — toggle name tag for a single villager
        LiteralArgumentBuilder<CommandSourceStack> showNameBuilder = Commands.literal("showname")
                .then(Commands.argument("name", StringArgumentType.string())
                        .executes(context -> {
                            String name = StringArgumentType.getString(context, "name");
                            return setOneVillagerNameVisible(context.getSource(), name, true);
                        }));
        builder.then(showNameBuilder);

        LiteralArgumentBuilder<CommandSourceStack> hideNameBuilder = Commands.literal("hidename")
                .then(Commands.argument("name", StringArgumentType.string())
                        .executes(context -> {
                            String name = StringArgumentType.getString(context, "name");
                            return setOneVillagerNameVisible(context.getSource(), name, false);
                        }));
        builder.then(hideNameBuilder);

        // Register the main "npc" command with the dispatcher
        pDispatcher.register(builder);
    }

    // TODO move setname from testecommands.
    // as setvillagername
    // another for npc, or just merge? hmmm.

    public static int showNpcInfo(CommandSourceStack source, String name) {
        try {
            Entity nullableSummoner = source.getEntity();
            Player summoner = nullableSummoner instanceof Player ? (Player) nullableSummoner : null;
            if (summoner == null) {
                source.sendFailure(Component.literal("Player not found."));
                return 0;
            }

            LOGGER.info("DEBUG showNpcInfo command executed by " + summoner.getName().getString());

            // [15:54:02] [Server thread/WARN] [co.fa.fa.da.DataBaseManager/]:
            // DataBaseAccess for database 'farmupcraft:npc_database' not found
            // 15:54:02.733
            // game
            // java.lang.NullPointerException: Cannot invoke
            // "com.falazar.farmupcraft.database.DataBaseAccess.get(net.minecraft.world.level.Level)"
            // because "dataBaseAccess" is null

            // Load the npc data from the database.
            DataBase<UUID, NpcData> npcDataDB = ModEvents.getNpcDatabase();
            // Search for npc by name.
            NpcData npc = npcDataDB.getValues().stream()
                    .filter(npcData -> npcData.getName().equals(name))
                    .findFirst()
                    .orElse(null);
            if (npc == null) {
                source.sendFailure(Component.literal("NPC not found."));
                return 0;
            }

            // Send the npc info to the player.
            source.sendSuccess(() -> Component.literal("NPC info: " + npc.getName()), false);
            source.sendSuccess(() -> Component.literal("NPC description: " + npc.getDescription()), false);

        } catch (Exception ex) {
            source.sendFailure(Component.literal("Exception thrown - see log"));
            ex.printStackTrace();
        }
        return 0;
    }

    public static int createNpc(CommandSourceStack source, String name) {
        try {
            Entity nullableSummoner = source.getEntity();
            Player summoner = nullableSummoner instanceof Player ? (Player) nullableSummoner : null;
            if (summoner == null) {
                source.sendFailure(Component.literal("Player not found."));
                return 0;
            }
            PlayerData player = getPlayer(source);
            VillageData village = player.getHomeVillage();

            LOGGER.info("DEBUG createNpc command executed by " + summoner.getName().getString());

            // TODO MAKE HELPER METHOD.
            // STEP 1: Find a nearby villager matching this name.
            double radius = 64.0;
            AABB searchBox = new AABB(
                    summoner.getX() - radius, summoner.getY() - radius, summoner.getZ() - radius,
                    summoner.getX() + radius, summoner.getY() + radius, summoner.getZ() + radius);
            List<Villager> nearVillagers = summoner.level().getEntitiesOfClass(
                    Villager.class,
                    searchBox,
                    villager -> villager.getName().getString().equals(name));
            Villager villager = nearVillagers.stream().findFirst().orElse(null);
            if (villager == null) {
                source.sendFailure(Component.literal("No nearby villager found with that name."));
                return 0;
            }

            // STEP 2: Create a new NPC data object with that UUID.
            NpcData npc = new NpcData(villager.getUUID(), name, village.getUUID(), "a villager");
            ModEvents.getNpcDatabase().putData(npc.getUUID(), npc);

            // Send success message to the player.
            source.sendSuccess(() -> Component.literal("NPC created: " + npc.getName()), false);

        } catch (Exception ex) {
            source.sendFailure(Component.literal("Exception thrown - see log"));
            ex.printStackTrace();
        }
        return 0;
    }

    public static int setNpcDescription(CommandSourceStack source, String name, String description) {
        try {
            Entity nullableSummoner = source.getEntity();
            Player summoner = nullableSummoner instanceof Player ? (Player) nullableSummoner : null;
            if (summoner == null) {
                source.sendFailure(Component.literal("Player not found."));
                return 0;
            }

            LOGGER.info("DEBUG setNpcDescription command executed by " + summoner.getName().getString());

            NpcData npc = getNpc(name);
            if (npc == null) {
                source.sendFailure(Component.literal("NPC not found."));
                return 0;
            }

            // Update the description.
            npc.setDescription(description);
            ModEvents.getNpcDatabase().putData(npc.getUUID(), npc);

            // Send success message to the player.
            source.sendSuccess(() -> Component.literal("NPC description updated: " + npc.getName()), false);
        } catch (Exception ex) {
            source.sendFailure(Component.literal("Exception thrown - see log"));
            ex.printStackTrace();
        }
        return 0;
    }

    public static int createNpcSkeleton(CommandSourceStack source, String name) {
        try {
            Entity nullableSummoner = source.getEntity();
            Player summoner = nullableSummoner instanceof Player ? (Player) nullableSummoner : null;
            if (summoner == null) {
                source.sendFailure(Component.literal("Player not found."));
                return 0;
            }

            LOGGER.info("DEBUG createNpcSkeleton command executed by " + summoner.getName().getString());

            // Create an in game skeleton NPC entity.
            ServerLevel level = (ServerLevel) summoner.level();

            // Inside your createNpcSkeleton method:
            Vec3 pos = summoner.position();

            // Create and spawn the skeleton
            Skeleton skeleton = EntityType.SKELETON.create(level);
            if (skeleton != null) {
                skeleton.moveTo(pos.x, pos.y, pos.z, summoner.getYRot(), 0.0F);
                level.addFreshEntity(skeleton);

                // Remove all attack and target goals to make it passive
                skeleton.goalSelector.removeAllGoals(goal -> true);
                skeleton.targetSelector.removeAllGoals(goal -> true);
            }

            // Add target goal for players only
            // skeleton.targetSelector.addGoal(1, new
            // net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal<>(
            // skeleton,
            // net.minecraft.world.entity.player.Player.class,
            // true
            // ));

            // Create a new NPC data object with a random UUID.
            // NpcData npc = new NpcData(UUID.randomUUID(), name, UUID.randomUUID(), "a
            // skeleton npc");
            // ModEvents.getNpcDatabase().putData(npc.getUUID(), npc);

            // Send success message to the player.
            // source.sendSuccess(() -> Component.literal("NPC skeleton created: " +
            // npc.getName()), false);
            source.sendSuccess(() -> Component.literal("NON NPC skeleton created. "), false);

        } catch (Exception ex) {
            source.sendFailure(Component.literal("Exception thrown - see log"));
            ex.printStackTrace();
        }
        return 0;
    }

    public static int createNpcCow(CommandSourceStack source, String name) {
        try {
            Entity nullableSummoner = source.getEntity();
            Player summoner = nullableSummoner instanceof Player ? (Player) nullableSummoner : null;
            if (summoner == null) {
                source.sendFailure(Component.literal("Player not found."));
                return 0;
            }

            LOGGER.info("DEBUG createNpcCow command executed by " + summoner.getName().getString());

            ServerLevel level = (ServerLevel) summoner.level();
            Vec3 pos = summoner.position();

            Cow cow = EntityType.COW.create(level);
            if (cow == null) {
                return 1;
            }
            cow.moveTo(pos.x, pos.y, pos.z, summoner.getYRot(), 0.0F);
            level.addFreshEntity(cow);

            // Remove all default goals
            cow.goalSelector.removeAllGoals(goal -> true);
            cow.targetSelector.removeAllGoals(goal -> true);

            // Set attack damage (e.g., 4.0)
            // Ensure the cow has the ATTACK_DAMAGE attribute before setting it
            // if (cow.getAttribute(Attributes.ATTACK_DAMAGE) == null) {
            // cow.getAttributes().addTransientAttribute(Attributes.ATTACK_DAMAGE);
            // }
            // cow.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(4.0);

            // Add melee attack goal
            cow.goalSelector.addGoal(1, new MeleeAttackGoal(cow, 1.2D, false));
            // Target players
            cow.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(cow, Player.class, true));
            // Add target goal for villagers
            // cow.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(cow,
            // Villager.class, true));

            source.sendSuccess(() -> Component.literal("Aggressive cow created."), false);
        } catch (Exception ex) {
            source.sendFailure(Component.literal("Exception thrown - see log"));
            ex.printStackTrace();
        }
        return 0;
    }

    /*
     * 
     * 18:16:30.961
     * game
     * [18:16:30] [NpcCommand:288]
     * DEBUG createNpcCow command executed by BossPanda96366
     * 18:16:30.963
     * game
     * java.lang.NullPointerException:
     * Cannot invoke
     * "net.minecraft.world.entity.ai.attributes.AttributeInstance.m_22100_(double)"
     * because the
     * return value of
     * "net.minecraft.world.entity.animal.Cow.m_21051_(net.minecraft.world.entity.ai.attributes.Attribute)"
     * is null
     * 18:16:30.963
     * game
     * at TRANSFORMER/farmupcraft@1.0.2/com.falazar.farmupcraft.command.NpcCommand.
     * createNpcCow(NpcCommand.java:305)
     * 18:16:30.963
     * game
     * at TRANSFORMER/farmupcraft@1.0.2/com.falazar.farmupcraft.command.NpcCommand.
     * lambda$register$8(NpcCommand.java:1
     */

    public static NpcData getNpc(String name) {
        // Load the npc data from the database.
        DataBase<UUID, NpcData> npcDataDB = ModEvents.getNpcDatabase();
        // Search for npc by name.
        return npcDataDB.getValues().stream()
                .filter(npcData -> npcData.getName().equals(name))
                .findFirst()
                .orElse(null);
    }

    /**
     * /npc initvillagers — scans all villagers within the player's village radius,
     * creates an AI-generated personality profile for any that don't already have
     * one, and saves the profile both to run/npcData/ and the NPC database.
     */
    public static int initVillagers(CommandSourceStack source) {
        try {
            Entity nullableSummoner = source.getEntity();
            Player summoner = nullableSummoner instanceof Player ? (Player) nullableSummoner : null;
            if (summoner == null) {
                source.sendFailure(Component.literal("This command can only be run by a player."));
                return 0;
            }

            PlayerData playerData = getPlayer(source);
            VillageData village = playerData != null ? playerData.getHomeVillage() : null;
            if (village == null) {
                source.sendFailure(Component.literal("You are not a member of any village."));
                return 0;
            }

            // Scan villagers within 10-chunk radius of the village center.
            Level level = summoner.level();
            List<LivingEntity> villagers = VillageCommand.getVillagersList(summoner.blockPosition(), level, 10);
            if (villagers.isEmpty()) {
                source.sendFailure(Component.literal("No villagers found nearby."));
                return 0;
            }

            source.sendSuccess(() -> Component.literal("Found " + villagers.size()
                    + " villager(s). Generating profiles for those without one..."), false);

            // Collect all villager names in advance so the AI knows who's in the village.
            List<String> allNames = villagers.stream()
                    .filter(e -> e instanceof Villager)
                    .map(e -> e.getName().getString())
                    .collect(java.util.stream.Collectors.toList());

            // Gather biome context for the village.
            Map<String, Integer> biomeMap = VillageCommand.getVillageBiomes(village, level);
            List<String> biomeNames = biomeMap.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .limit(2)
                    .map(Map.Entry::getKey)
                    .collect(java.util.stream.Collectors.toList());

            // Gather surface structure names for curiosity hints (shuffle so each villager
            // can get a different one).
            List<String> structureNames = VillageCommand.getVillageStructuresList(village, level)
                    .stream()
                    .map(e -> e.getValue().getName())
                    .filter(n -> n != null && !n.isBlank())
                    .collect(java.util.stream.Collectors.toList());
            Collections.shuffle(structureNames);

            MinecraftServer server = source.getServer();
            String villageName = village.getName();
            UUID villageUUID = village.getUUID();

            int queued = 0;
            int structureIndex = 0;
            for (LivingEntity entity : villagers) {
                if (!(entity instanceof Villager villager))
                    continue;
                String npcName = villager.getName().getString();
                UUID npcUUID = villager.getUUID();

                // Skip if a profile file already exists.
                if (NpcDataLoader.hasProfile(npcName, npcUUID)) {
                    LOGGER.info("initVillagers: skipping '{}' — profile already exists.", npcName);
                    source.sendSuccess(() -> Component.literal(
                            "[NPC] Skipped " + npcName + " — profile already exists.").withStyle(ChatFormatting.GRAY),
                            false);
                    continue;
                }

                String profession = villager.getVillagerData().getProfession().toString();
                // Assign one structure to be curious about (cycle through the list).
                String structureCuriosity = structureNames.isEmpty() ? null
                        : structureNames.get(structureIndex % structureNames.size());
                structureIndex++;
                queued++;

                generateProfileForVillager(source, server, npcName, npcUUID, profession,
                        allNames, villageName, biomeNames, structureCuriosity, villageUUID, false, null);
            }

            if (queued == 0) {
                source.sendSuccess(() -> Component.literal(
                        "All nearby villagers already have profiles."), false);
            } else {
                int finalQueued = queued;
                source.sendSuccess(() -> Component.literal(
                        "Queued AI profile generation for " + finalQueued
                                + " villager(s). Profiles will appear as each finishes."),
                        false);
            }
        } catch (Exception ex) {
            source.sendFailure(Component.literal("initVillagers exception — see log"));
            ex.printStackTrace();
        }
        return 0;
    }

    /**
     * /npc rename "OldName" "NewName"
     * Renames the villager entity in-world, renames the npcData JSON file, and
     * updates the in-memory NPC database entry.
     */
    public static int renameVillager(CommandSourceStack source, String oldName, String newName) {
        try {
            Entity nullableSummoner = source.getEntity();
            Player summoner = nullableSummoner instanceof Player ? (Player) nullableSummoner : null;
            if (summoner == null) {
                source.sendFailure(Component.literal("This command can only be run by a player."));
                return 0;
            }

            // Search for villager by name within 320-block radius.
            double radius = 320.0;
            AABB searchBox = new AABB(
                    summoner.getX() - radius, summoner.getY() - radius, summoner.getZ() - radius,
                    summoner.getX() + radius, summoner.getY() + radius, summoner.getZ() + radius);
            List<Villager> nearby = summoner.level().getEntitiesOfClass(
                    Villager.class, searchBox,
                    v -> v.getName().getString().trim().equals(oldName));
            Villager villager = nearby.stream().findFirst().orElse(null);
            if (villager == null) {
                source.sendFailure(Component.literal(
                        "No villager named '" + oldName + "' found within 320 blocks."));
                return 0;
            }

            UUID uuid = villager.getUUID();

            // Rename entity in-world.
            villager.setCustomName(Component.literal(newName));

            // Rename npcData JSON file (updates filename + "name" field inside).
            boolean fileRenamed = NpcDataLoader.renameProfile(oldName, newName, uuid);

            // Update in-memory NPC database entry if present.
            DataBase<UUID, NpcData> npcDB = ModEvents.getNpcDatabase();
            NpcData npcData = npcDB.getValues().stream()
                    .filter(n -> n.getUUID().equals(uuid))
                    .findFirst().orElse(null);
            if (npcData != null) {
                npcData.setName(newName);
                npcDB.putData(uuid, npcData);
            }

            String fileMsg = fileRenamed
                    ? " Profile file renamed."
                    : " (No profile file found — in-world name updated only.)";
            String msg = "Renamed '" + oldName + "' → '" + newName + "'." + fileMsg;
            LOGGER.info("renameVillager: {} → {} (uuid={})", oldName, newName, uuid);
            source.sendSuccess(() -> Component.literal(msg), true);
        } catch (Exception ex) {
            source.sendFailure(Component.literal("renameVillager exception — see log"));
            ex.printStackTrace();
        }
        return 0;
    }

    /**
     * /npc redoprofile "Name"
     * Re-generates the AI personality for a nearby villager.
     * If an existing profile file is found, its content is sent to the AI as
     * context so the update builds on what was already written.
     * Also stamps village_name into the new file.
     */
    public static int redoProfile(CommandSourceStack source, String name) {
        try {
            Entity nullableSummoner = source.getEntity();
            Player summoner = nullableSummoner instanceof Player ? (Player) nullableSummoner : null;
            if (summoner == null) {
                source.sendFailure(Component.literal("This command can only be run by a player."));
                return 0;
            }

            // Find the villager by name.
            double radius = 320.0;
            AABB searchBox = new AABB(
                    summoner.getX() - radius, summoner.getY() - radius, summoner.getZ() - radius,
                    summoner.getX() + radius, summoner.getY() + radius, summoner.getZ() + radius);
            List<Villager> nearby = summoner.level().getEntitiesOfClass(
                    Villager.class, searchBox,
                    v -> v.getName().getString().trim().equals(name));
            Villager villager = nearby.stream().findFirst().orElse(null);
            if (villager == null) {
                source.sendFailure(Component.literal("No villager named '" + name + "' found within 320 blocks."));
                return 0;
            }

            UUID uuid = villager.getUUID();
            String profession = villager.getVillagerData().getProfession().toString();

            // Read existing profile (if any) to pass as context.
            com.google.gson.JsonObject existing = NpcDataLoader.readRawProfile(name, uuid);
            String existingText = existing != null ? existing.toString() : null;

            // Get village context.
            PlayerData playerData = ModEvents.getPlayerDatabase().getData(summoner.getUUID());
            VillageData village = (playerData != null && playerData.getHomeVillageUUID() != null)
                    ? ModEvents.getVillageDatabase().getData(playerData.getHomeVillageUUID())
                    : null;
            String villageName = village != null ? village.getName() : "";
            UUID villageUUID = village != null ? village.getUUID() : new UUID(0, 0);

            Map<String, Integer> biomeMap = village != null
                    ? VillageCommand.getVillageBiomes(village, summoner.level()) : Map.of();
            List<String> biomeNames = biomeMap.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .limit(2)
                    .map(Map.Entry::getKey)
                    .collect(java.util.stream.Collectors.toList());

            List<String> structureNames = village != null
                    ? VillageCommand.getVillageStructuresList(village, summoner.level())
                            .stream().map(e -> e.getValue().getName())
                            .filter(n -> n != null && !n.isBlank())
                            .collect(java.util.stream.Collectors.toList())
                    : List.of();
            String structureCuriosity = structureNames.isEmpty() ? null
                    : structureNames.get((int) (Math.random() * structureNames.size()));

            // Collect sibling names.
            double namesRadius = 320.0;
            AABB namesBox = new AABB(
                    summoner.getX() - namesRadius, summoner.getY() - namesRadius, summoner.getZ() - namesRadius,
                    summoner.getX() + namesRadius, summoner.getY() + namesRadius, summoner.getZ() + namesRadius);
            List<String> siblingNames = summoner.level().getEntitiesOfClass(Villager.class, namesBox)
                    .stream().map(v -> v.getName().getString())
                    .collect(java.util.stream.Collectors.toList());

            source.sendSuccess(() -> Component.literal(
                    "[NPC] Regenerating profile for " + name + "...").withStyle(ChatFormatting.YELLOW), false);

            MinecraftServer server = source.getServer();
            generateProfileForVillager(source, server, name, uuid, profession,
                    siblingNames, villageName, biomeNames, structureCuriosity, villageUUID, true, existingText);
        } catch (Exception ex) {
            source.sendFailure(Component.literal("redoProfile exception — see log"));
            ex.printStackTrace();
        }
        return 0;
    }

    /**
     * Fires an async AI profile generation (or regeneration) for one villager and
     * handles the result: guards against [AI Error], writes the file, updates the
     * in-memory DB, and sends success/failure feedback to the command source.
     *
     * @param isRegen       true = regenerate (uses existing profile text), false = generate fresh
     * @param existingText  existing profile JSON text; only used when isRegen=true
     */
    private static void generateProfileForVillager(
            CommandSourceStack source,
            MinecraftServer server,
            String npcName,
            UUID npcUUID,
            String profession,
            List<String> villagerNames,
            String villageName,
            List<String> biomes,
            String structureCuriosity,
            UUID villageUUID,
            boolean isRegen,
            String existingText) {

        java.util.concurrent.CompletableFuture<String> future = isRegen
                ? AIManager.regenerateNpcPersonality(npcName, profession, existingText,
                        villagerNames, villageName, biomes, structureCuriosity)
                : AIManager.generateNpcPersonality(npcName, profession,
                        villagerNames, villageName, biomes, structureCuriosity);

        future.thenAccept(personality -> server.execute(() -> {
            if (personality != null && personality.startsWith("[AI Error]")) {
                source.sendFailure(Component.literal(
                        "[NPC] AI failed for " + npcName + ": " + personality));
                return;
            }
            String description = "A " + profession.replace("minecraft:", "")
                    + " living in " + villageName + ".";
            boolean wrote = NpcDataLoader.writeProfile(npcName, npcUUID, description, personality, villageName);
            String filename = npcName + "-" + npcUUID + ".json";

            // Preserve foreverKid flag when updating.
            NpcData existingNpc = ModEvents.getNpcDatabase().getData(npcUUID);
            boolean keepForeverKid = existingNpc != null && existingNpc.isForeverKid();
            NpcData npcData = new NpcData(npcUUID, npcName, villageUUID, description,
                    personality, villageName, keepForeverKid);
            ModEvents.getNpcDatabase().putData(npcUUID, npcData);

            String verb = isRegen ? "updated" : "created";
            if (wrote) {
                source.sendSuccess(() -> Component.literal(
                        "[NPC] Profile " + verb + " for " + npcName + " → npcData/" + filename)
                        .withStyle(ChatFormatting.GREEN), false);
            } else {
                source.sendFailure(Component.literal(
                        "[NPC] AI done but failed to write file for " + npcName));
            }
        })).exceptionally(err -> {
            server.execute(() -> source.sendFailure(Component.literal(
                    "[NPC] Failed to generate profile for " + npcName + ": " + err.getMessage())));
            return null;
        });
    }

    /**
     * /npc cleanprofiles
     * Deletes any npcData/ profile files that contain "[AI Error]" in personality.
     */
    public static int cleanProfiles(CommandSourceStack source) {
        try {
            int deleted = NpcDataLoader.deleteErrorProfiles();
            if (deleted == 0) {
                source.sendSuccess(
                        () -> Component.literal("[NPC] No error profiles found.").withStyle(ChatFormatting.GRAY),
                        false);
            } else {
                int d = deleted;
                source.sendSuccess(() -> Component.literal("[NPC] Deleted " + d + " error profile(s).")
                        .withStyle(ChatFormatting.GREEN), false);
            }
        } catch (Exception ex) {
            source.sendFailure(Component.literal("cleanProfiles exception — see log"));
            ex.printStackTrace();
        }
        return 0;
    }

    /**
     * /npc setkid "Name" / /npc unsetkid "Name"
     * Marks or unmarks a villager as a forever-kid (stays baby indefinitely).
     */
    public static int setForeverKid(CommandSourceStack source, String name, boolean foreverKid) {
        try {
            Entity nullableSummoner = source.getEntity();
            Player summoner = nullableSummoner instanceof Player ? (Player) nullableSummoner : null;
            if (summoner == null) {
                source.sendFailure(Component.literal("This command can only be run by a player."));
                return 0;
            }

            double radius = 320.0;
            AABB searchBox = new AABB(
                    summoner.getX() - radius, summoner.getY() - radius, summoner.getZ() - radius,
                    summoner.getX() + radius, summoner.getY() + radius, summoner.getZ() + radius);
            List<Villager> nearby = summoner.level().getEntitiesOfClass(
                    Villager.class, searchBox,
                    v -> v.getName().getString().trim().equals(name));
            Villager villager = nearby.stream().findFirst().orElse(null);
            if (villager == null) {
                source.sendFailure(Component.literal("No villager named '" + name + "' found within 320 blocks."));
                return 0;
            }

            if (foreverKid) {
                villager.setBaby(true);
            }

            DataBase<UUID, NpcData> npcDB = ModEvents.getNpcDatabase();
            NpcData npcData = npcDB.getData(villager.getUUID());
            if (npcData == null) {
                source.sendFailure(Component.literal("No NPC data found for '" + name + "'."));
                return 0;
            }
            npcData.setForeverKid(foreverKid);
            npcDB.putData(villager.getUUID(), npcData);

            String label = foreverKid ? "forever kid" : "normal (can grow up)";
            source.sendSuccess(() -> Component.literal("[NPC] " + name + " is now " + label + "."), true);
        } catch (Exception ex) {
            source.sendFailure(Component.literal("setkid exception — see log"));
            ex.printStackTrace();
        }
        return 0;
    }

    /**
     * /npc shownames / /npc hidenames
     * Sets setCustomNameVisible on all villagers in the player's village.
     * This controls the always-on floating name tag, which is also shown on the
     * minimap radar.
     */
    public static int setVillagerNamesVisible(CommandSourceStack source, boolean visible) {
        try {
            Entity nullableSummoner = source.getEntity();
            Player summoner = nullableSummoner instanceof Player ? (Player) nullableSummoner : null;
            if (summoner == null) {
                source.sendFailure(Component.literal("This command can only be run by a player."));
                return 0;
            }

            List<LivingEntity> villagers = VillageCommand.getVillagersInVillage(summoner, summoner.level(), 10);
            if (villagers.isEmpty()) {
                source.sendFailure(Component.literal("No villagers found in your village."));
                return 0;
            }

            int count = 0;
            for (LivingEntity e : villagers) {
                if (e instanceof Villager v) {
                    v.setCustomNameVisible(visible);
                    count++;
                }
            }

            int finalCount = count;
            String label = visible ? "shown" : "hidden";
            source.sendSuccess(() -> Component.literal(
                    "[NPC] Villager names " + label + " on minimap for " + finalCount + " villager(s).")
                    .withStyle(visible ? ChatFormatting.GREEN : ChatFormatting.GRAY), true);
        } catch (Exception ex) {
            source.sendFailure(Component.literal("shownames/hidenames exception — see log"));
            ex.printStackTrace();
        }
        return 0;
    }

    /**
     * /npc showname "Name" / /npc hidename "Name"
     * Sets the floating name tag on a single named villager within 320 blocks.
     */
    public static int setOneVillagerNameVisible(CommandSourceStack source, String name, boolean visible) {
        try {
            Entity nullableSummoner = source.getEntity();
            Player summoner = nullableSummoner instanceof Player ? (Player) nullableSummoner : null;
            if (summoner == null) {
                source.sendFailure(Component.literal("This command can only be run by a player."));
                return 0;
            }

            double radius = 320.0;
            AABB searchBox = new AABB(
                    summoner.getX() - radius, summoner.getY() - radius, summoner.getZ() - radius,
                    summoner.getX() + radius, summoner.getY() + radius, summoner.getZ() + radius);
            List<Villager> nearby = summoner.level().getEntitiesOfClass(
                    Villager.class, searchBox,
                    v -> v.getName().getString().trim().equals(name));
            Villager villager = nearby.stream().findFirst().orElse(null);
            if (villager == null) {
                source.sendFailure(Component.literal("No villager named '" + name + "' found within 320 blocks."));
                return 0;
            }

            villager.setCustomNameVisible(visible);
            String label = visible ? "shown" : "hidden";
            source.sendSuccess(() -> Component.literal(
                    "[NPC] " + name + "'s name tag is now " + label + ".")
                    .withStyle(visible ? ChatFormatting.GREEN : ChatFormatting.GRAY), false);
        } catch (Exception ex) {
            source.sendFailure(Component.literal("showname/hidename exception — see log"));
            ex.printStackTrace();
        }
        return 0;
    }

}
