package com.falazar.farmupcraft.command;

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
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.world.ForgeChunkManager;

import java.util.Arrays;
import java.util.List;
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

        // Register the main "npc" command with the dispatcher
        pDispatcher.register(builder);
    }

    // TODO move setname from testecommands.
    // as setvillagername
    // another for npc, or just merge?  hmmm.

    public static int showNpcInfo(CommandSourceStack source, String name) {
        try {
            Entity nullableSummoner = source.getEntity();
            Player summoner = nullableSummoner instanceof Player ? (Player) nullableSummoner : null;
            if (summoner == null) {
                source.sendFailure(Component.literal("Player not found."));
                return 0;
            }


            LOGGER.info("DEBUG showNpcInfo command executed by " + summoner.getName().getString());


//[15:54:02] [Server thread/WARN] [co.fa.fa.da.DataBaseManager/]: DataBaseAccess for database 'farmupcraft:npc_database' not found
//            15:54:02.733
//            game
//            java.lang.NullPointerException: Cannot invoke "com.falazar.farmupcraft.database.DataBaseAccess.get(net.minecraft.world.level.Level)" because "dataBaseAccess" is null

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
                    summoner.getX() + radius, summoner.getY() + radius, summoner.getZ() + radius
            );
            List<Villager> nearVillagers = summoner.level().getEntitiesOfClass(
                    Villager.class,
                    searchBox,
                    villager -> villager.getName().getString().equals(name)
            );
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
//            skeleton.targetSelector.addGoal(1, new net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal<>(
//                    skeleton,
//                    net.minecraft.world.entity.player.Player.class,
//                    true
//            ));

            // Create a new NPC data object with a random UUID.
//            NpcData npc = new NpcData(UUID.randomUUID(), name, UUID.randomUUID(), "a skeleton npc");
//            ModEvents.getNpcDatabase().putData(npc.getUUID(), npc);

            // Send success message to the player.
//            source.sendSuccess(() -> Component.literal("NPC skeleton created: " + npc.getName()), false);
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
//            if (cow.getAttribute(Attributes.ATTACK_DAMAGE) == null) {
//                cow.getAttributes().addTransientAttribute(Attributes.ATTACK_DAMAGE);
//            }
//            cow.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(4.0);

            // Add melee attack goal
            cow.goalSelector.addGoal(1, new MeleeAttackGoal(cow, 1.2D, false));
            // Target players
            cow.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(cow, Player.class, true));
            // Add target goal for villagers
            //cow.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(cow, Villager.class, true));

            source.sendSuccess(() -> Component.literal("Aggressive cow created."), false);
        } catch (Exception ex) {
            source.sendFailure(Component.literal("Exception thrown - see log"));
            ex.printStackTrace();
        }
        return 0;
    }

    /*

18:16:30.961
game
[18:16:30] [Server thread/INFO] [co.fa.fa.ut.CustomLogger/]: [INFO] [farmupcraft] [NpcCommand:288]
DEBUG createNpcCow command executed by BossPanda96366
18:16:30.963
game
java.lang.NullPointerException:
Cannot invoke "net.minecraft.world.entity.ai.attributes.AttributeInstance.m_22100_(double)" because the
return value of "net.minecraft.world.entity.animal.Cow.m_21051_(net.minecraft.world.entity.ai.attributes.Attribute)" is null
18:16:30.963
game
at TRANSFORMER/farmupcraft@1.0.2/com.falazar.farmupcraft.command.NpcCommand.createNpcCow(NpcCommand.java:305)
18:16:30.963
game
at TRANSFORMER/farmupcraft@1.0.2/com.falazar.farmupcraft.command.NpcCommand.lambda$register$8(NpcCommand.java:1
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

}
