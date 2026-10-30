package com.falazar.farmupcraft.command;

import com.falazar.farmupcraft.AIManager;
import com.falazar.farmupcraft.data.NpcData;
import com.falazar.farmupcraft.data.VillageData;
import com.falazar.farmupcraft.database.DataBase;
import com.falazar.farmupcraft.events.ModEvents;
import com.falazar.farmupcraft.util.CustomLogger;
import com.falazar.farmupcraft.util.NpcDataLoader;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.ToIntFunction;

/** NPC commands and server-owned AI jobs. No worker thread reads or mutates a world. */
public final class NpcCommand {
    public static final CustomLogger LOGGER = new CustomLogger(NpcCommand.class.getSimpleName());
    private static final Set<String> NPC_BEHAVIORS = Set.of("nonhostile", "wary", "hostile");
    private static final String[] NOTE_STRUCTURE_TYPES = {
            "ancient ruins", "jungle temple", "graveyard", "haunted manor",
            "dungeon", "mountain fortress", "cursed chapel", "abandoned keep"
    };
    public static final ConcurrentLinkedQueue<ProfileRequest> PENDING_PROFILES = new ConcurrentLinkedQueue<>();
    private static final Map<MinecraftServer, Jobs> JOBS = new IdentityHashMap<>();

    public record ProfileRequest(String name, UUID uuid, String profession, List<String> villagerNames,
                                 String villageName, List<String> biomes, @Nullable String structureCuriosity,
                                 UUID villageUUID, boolean isRegen, @Nullable String existingText) {
        public ProfileRequest {
            villagerNames = List.copyOf(villagerNames);
            biomes = List.copyOf(biomes);
        }
    }

    private record RequestContext(CommandSourceStack source, ServerLevel level, ProfileRequest request) {}

    private static final class Jobs {
        final Map<UUID, RequestContext> requests = new HashMap<>();
        final Set<UUID> generating = new HashSet<>();
        final Set<CompletableFuture<?>> futures = new HashSet<>();
        boolean replenishing;
        boolean stopped;
    }

    private NpcCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var root = Commands.literal("npc");
        root.then(named("create", true, (s, n) -> createNpc(s, n)));
        root.then(Commands.literal("findnearestliving").requires(NpcCommand::admin)
                .executes(c -> run(c, s -> findNearestLiving(s, 16))));
        root.then(Commands.literal("admin").requires(NpcCommand::admin)
                .then(named("create", false, (s, n) -> createNpc(s, n)))
                .then(Commands.literal("findnearestliving").executes(c -> run(c, s -> findNearestLiving(s, 16)))));
        root.then(Commands.literal("claim").requires(NpcCommand::admin)
                .then(Commands.argument("name", StringArgumentType.string())
                        .executes(c -> run(c, s -> claimNpc(s, arg(c, "name"))))
                        .then(Commands.argument("behavior", StringArgumentType.word())
                                .executes(c -> run(c, s -> claimNpc(s, arg(c, "name"), arg(c, "behavior")))))));
        root.then(named("info", false, NpcCommand::showNpcInfo));
        root.then(Commands.literal("list").executes(c -> run(c, s -> listNearbyNpcs(s, 64)))
                .then(Commands.argument("radius", IntegerArgumentType.integer(4, 256))
                        .executes(c -> run(c, s -> listNearbyNpcs(s, IntegerArgumentType.getInteger(c, "radius"))))));
        root.then(Commands.literal("setdescription").requires(NpcCommand::admin)
                .then(Commands.argument("name", StringArgumentType.string())
                        .then(Commands.argument("description", StringArgumentType.string())
                                .executes(c -> run(c, s -> setNpcDescription(s, arg(c, "name"), arg(c, "description")))))));
        root.then(named("createskeleton", true, NpcCommand::createNpcSkeleton));
        // Retain the initial port's typo as an alias.
        root.then(named("createskleleton", true, NpcCommand::createNpcSkeleton));
        root.then(named("createcow", true, NpcCommand::createNpcCow));
        root.then(Commands.literal("initvillagers").requires(NpcCommand::admin)
                .executes(c -> run(c, NpcCommand::initVillagers)));
        root.then(Commands.literal("rename").requires(NpcCommand::admin)
                .then(Commands.argument("oldName", StringArgumentType.string())
                        .then(Commands.argument("newName", StringArgumentType.string())
                                .executes(c -> run(c, s -> renameVillager(s, arg(c, "oldName"), arg(c, "newName")))))));
        root.then(named("redoprofile", true, NpcCommand::redoProfile));
        root.then(Commands.literal("cleanprofiles").requires(NpcCommand::admin)
                .executes(c -> run(c, NpcCommand::cleanProfiles)));
        root.then(named("setkid", true, (s, n) -> setForeverKid(s, n, true)));
        root.then(named("unsetkid", true, (s, n) -> setForeverKid(s, n, false)));
        root.then(Commands.literal("shownames").executes(c -> run(c, s -> setVillagerNamesVisible(s, true))));
        root.then(Commands.literal("hidenames").executes(c -> run(c, s -> setVillagerNamesVisible(s, false))));
        root.then(named("showname", false, (s, n) -> setOneVillagerNameVisible(s, n, true)));
        root.then(named("hidename", false, (s, n) -> setOneVillagerNameVisible(s, n, false)));
        root.then(Commands.literal("fillnotescache").requires(NpcCommand::admin)
                .executes(c -> run(c, s -> fillNotesCache(s, 5)))
                .then(Commands.argument("count", IntegerArgumentType.integer(1, 20))
                        .executes(c -> run(c, s -> fillNotesCache(s, IntegerArgumentType.getInteger(c, "count"))))));
        dispatcher.register(root);
    }

    @FunctionalInterface
    private interface NamedCommand { int run(CommandSourceStack source, String name); }

    private static LiteralArgumentBuilder<CommandSourceStack> named(String command, boolean op, NamedCommand action) {
        var builder = Commands.literal(command);
        if (op) builder.requires(NpcCommand::admin);
        return builder.then(Commands.argument("name", StringArgumentType.string())
                .executes(c -> run(c, s -> action.run(s, arg(c, "name")))));
    }

    private static String arg(CommandContext<CommandSourceStack> context, String name) {
        return StringArgumentType.getString(context, name);
    }

    private static boolean admin(CommandSourceStack source) {
        return hasAdminPermission(source.permissions());
    }

    static boolean hasAdminPermission(PermissionSet permissions) {
        return permissions.hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }

    private static int run(CommandContext<CommandSourceStack> context, ToIntFunction<CommandSourceStack> command) {
        CommandSourceStack source = context.getSource();
        requireServerThread(source.getServer());
        try {
            return command.applyAsInt(source);
        } catch (IllegalArgumentException | IllegalStateException e) {
            LOGGER.error("NPC command failed: {}", e.getMessage(), e);
            source.sendFailure(Component.literal("[NPC] " + e.getMessage()));
            return 0;
        }
    }

    private static ServerPlayer player(CommandSourceStack source) {
        requireServerThread(source.getServer());
        if (!(source.getEntity() instanceof ServerPlayer player))
            throw new IllegalArgumentException("This command can only be run by a player.");
        return player;
    }

    private static DataBase<UUID, NpcData> database(CommandSourceStack source) {
        requireServerThread(source.getServer());
        return ModEvents.getNpcDatabase(source.getLevel());
    }

    public static @Nullable NpcData getNpc(String name) {
        return ModEvents.getNpcDatabase().getValues().stream().filter(n -> n.getName().equals(name))
                .findFirst().orElse(null);
    }

    public static @Nullable NpcData getNpc(CommandSourceStack source, String name) {
        return database(source).getValues().stream().filter(n -> n.getName().equals(name)).findFirst().orElse(null);
    }

    public static int showNpcInfo(CommandSourceStack source, String name) {
        player(source);
        NpcData npc = getNpc(source, name);
        if (npc == null) return fail(source, "NPC not found.");
        success(source, "NPC info: " + npc.getName());
        success(source, "NPC description: " + npc.getDescription());
        return 1;
    }

    public static int setNpcDescription(CommandSourceStack source, String name, String description) {
        player(source);
        NpcData npc = getNpc(source, name);
        if (npc == null) return fail(source, "NPC not found.");
        npc.setDescription(description);
        database(source).putData(npc.getUUID(), npc);
        success(source, "NPC description updated: " + npc.getName());
        return 1;
    }

    private static @Nullable LivingEntity nearest(ServerPlayer player, double radius) {
        return player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(radius),
                e -> e.isAlive() && !(e instanceof Player))
                .stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(player))).orElse(null);
    }

    private static String typeName(LivingEntity entity) {
        Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        return id.toString();
    }

    private static void nameEntity(LivingEntity target, String name) {
        if (name.isBlank()) throw new IllegalArgumentException("NPC name must not be blank.");
        target.setCustomName(Component.literal(name));
        target.setCustomNameVisible(true);
        if (target instanceof Mob mob) mob.setPersistenceRequired();
    }

    public static int createNpc(CommandSourceStack source, String name) {
        LivingEntity target = nearest(player(source), 16);
        if (target == null) return fail(source, "No nearby living entity found (range 16 blocks).");
        nameEntity(target, name);
        NpcData npc = new NpcData(target.getUUID(), name, null, "Created NPC " + name + " (" + typeName(target) + ")");
        database(source).putData(npc.getUUID(), npc);
        success(source, "NPC created: " + name);
        return 1;
    }

    public static int claimNpc(CommandSourceStack source, String name) {
        return claimNpc(source, name, "nonhostile");
    }

    public static int claimNpc(CommandSourceStack source, String name, String behavior) {
        ServerPlayer player = player(source);
        String normalized = behavior.toLowerCase(Locale.ROOT);
        if (!NPC_BEHAVIORS.contains(normalized))
            return fail(source, "Behavior must be one of: nonhostile, wary, hostile.");
        LivingEntity target = nearest(player, 8);
        if (target == null) return fail(source, "No nearby mob found to claim (range 8 blocks).");
        VillageData village = homeVillage(source);
        UUID villageId = village == null ? new UUID(0, 0) : village.getUUID();
        String villageName = village == null ? "" : village.getName();
        String filename = defaultProfileFileName(name, target.getUUID());
        String description = "Claimed NPC " + name + " (" + typeName(target) + ")";
        if (!NpcDataLoader.profileFileExists(filename)) {
            String personality = switch (normalized) {
                case "hostile" -> "You are volatile and intimidating, but still speak briefly and clearly.";
                case "wary" -> "You are cautious and distrustful, avoid direct conflict unless cornered.";
                default -> "You are peaceful, calm, and helpful to nearby villagers and players.";
            };
            if (!NpcDataLoader.writeProfileToFile(filename, name, target.getUUID(), description, personality, villageName))
                return fail(source, "Could not write NPC profile; claim was not applied.");
        }
        nameEntity(target, name);
        if (target instanceof Mob mob && !"hostile".equals(normalized)) mob.setTarget(null);
        NpcData npc = database(source).getData(target.getUUID());
        if (npc == null) npc = new NpcData(target.getUUID(), name, villageId, description);
        npc.setName(name);
        npc.setHomeVillageId(villageId);
        npc.setDescription(description);
        npc.setVillageName(villageName);
        npc.setBehavior(normalized);
        npc.setAiProfileFile(filename);
        database(source).putData(target.getUUID(), npc);
        success(source, "NPC claimed: " + name + " | entity=" + typeName(target) + " | behavior=" + normalized
                + " | profile=npcData/" + filename);
        return 1;
    }

    static String defaultProfileFileName(String name, UUID uuid) {
        String base = name == null ? "npc" : name.trim().toLowerCase(Locale.ROOT);
        if (base.isEmpty()) base = "npc";
        return base.replaceAll("[^a-z0-9_-]+", "_") + "-" + uuid + ".json";
    }

    public static int findNearestLiving(CommandSourceStack source, double radius) {
        ServerPlayer player = player(source);
        LivingEntity target = nearest(player, radius);
        if (target == null) return fail(source, "No nearby living entity found (range " + (int) radius + " blocks).");
        success(source, "[NPC] nearest living | type=" + typeName(target) + " | uuid=" + target.getUUID()
                + " | dist=" + Math.round(Math.sqrt(target.distanceToSqr(player))));
        return 1;
    }

    public static int createNpcSkeleton(CommandSourceStack source, String name) {
        ServerPlayer player = player(source);
        Skeleton skeleton = EntityTypes.SKELETON.create(player.level(), EntitySpawnReason.COMMAND);
        if (skeleton == null) return fail(source, "Skeleton creation failed.");
        skeleton.snapTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0);
        skeleton.goalSelector.removeAllGoals(g -> true);
        skeleton.targetSelector.removeAllGoals(g -> true);
        if (!player.level().addFreshEntity(skeleton)) return fail(source, "Skeleton spawn was rejected.");
        success(source, "NON NPC skeleton created.");
        return 1;
    }

    public static int createNpcCow(CommandSourceStack source, String name) {
        ServerPlayer player = player(source);
        Cow cow = EntityTypes.COW.create(player.level(), EntitySpawnReason.COMMAND);
        if (cow == null) return fail(source, "Cow creation failed.");
        cow.snapTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0);
        cow.goalSelector.removeAllGoals(g -> true);
        cow.targetSelector.removeAllGoals(g -> true);
        cow.goalSelector.addGoal(1, new MeleeAttackGoal(cow, 1.2, false) {
            @Override
            protected void checkAndPerformAttack(LivingEntity target) {
                if (cow.getAttributes().hasAttribute(Attributes.ATTACK_DAMAGE)) {
                    super.checkAndPerformAttack(target);
                } else if (canPerformAttack(target)) {
                    // Vanilla cows lack ATTACK_DAMAGE; the stock melee goal would throw on their first hit.
                    resetAttackCooldown();
                    cow.swing(InteractionHand.MAIN_HAND);
                    target.hurtServer(getServerLevel(cow), cow.damageSources().mobAttack(cow), 4.0F);
                }
            }
        });
        cow.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(cow, Player.class, true));
        if (!player.level().addFreshEntity(cow)) return fail(source, "Cow spawn was rejected.");
        success(source, "Aggressive cow created.");
        return 1;
    }

    private static @Nullable VillageData homeVillage(CommandSourceStack source) {
        var data = ModEvents.getPlayerDatabase(source.getLevel()).getData(player(source).getUUID());
        return data == null || data.getHomeVillageUUID() == null ? null
                : ModEvents.getVillageDatabase(source.getLevel()).getData(data.getHomeVillageUUID());
    }

    private static @Nullable Villager namedVillager(CommandSourceStack source, String name) {
        ServerPlayer player = player(source);
        return player.level().getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(320),
                v -> v.getName().getString().trim().equals(name)).stream().findFirst().orElse(null);
    }

    public static int renameVillager(CommandSourceStack source, String oldName, String newName) {
        Villager villager = namedVillager(source, oldName);
        if (villager == null) return fail(source, "No villager named '" + oldName + "' found within 320 blocks.");
        if (newName.isBlank()) return fail(source, "NPC name must not be blank.");
        if (jobs(source.getServer()).requests.containsKey(villager.getUUID()))
            return fail(source, "Profile generation/retry pending for this NPC. Finish it before renaming.");
        boolean existed = NpcDataLoader.hasProfile(source.getLevel(), oldName, villager.getUUID());
        boolean renamed = NpcDataLoader.renameProfile(source.getLevel(), oldName, newName, villager.getUUID());
        if (existed && !renamed) return fail(source, "Profile rename failed; NPC name was not changed.");
        villager.setCustomName(Component.literal(newName));
        NpcData data = database(source).getData(villager.getUUID());
        if (data != null) {
            data.setName(newName);
            database(source).putData(data.getUUID(), data);
        }
        success(source, "Renamed '" + oldName + "' → '" + newName + "'."
                + (renamed ? " Profile file updated." : " No profile file found; in-world name updated only."));
        return 1;
    }

    public static int setForeverKid(CommandSourceStack source, String name, boolean foreverKid) {
        Villager villager = namedVillager(source, name);
        if (villager == null) return fail(source, "No villager named '" + name + "' found within 320 blocks.");
        NpcData data = database(source).getData(villager.getUUID());
        if (data == null) return fail(source, "No NPC data found for '" + name + "'.");
        if (foreverKid) villager.setBaby(true);
        data.setForeverKid(foreverKid);
        database(source).putData(data.getUUID(), data);
        success(source, "[NPC] " + name + " is now " + (foreverKid ? "forever kid" : "normal (can grow up)") + ".");
        return 1;
    }

    public static int setVillagerNamesVisible(CommandSourceStack source, boolean visible) {
        ServerPlayer player = player(source);
        var villagers = VillageCommand.getVillagersInVillage(player, player.level(), 10);
        if (villagers.isEmpty()) return fail(source, "No villagers found in your village.");
        int count = 0;
        for (LivingEntity entity : villagers) {
            if (entity instanceof Villager villager) {
                villager.setCustomNameVisible(visible);
                count++;
            }
        }
        success(source, "[NPC] Villager names " + (visible ? "shown" : "hidden") + " on minimap for " + count + " villager(s).");
        return 1;
    }

    public static int setOneVillagerNameVisible(CommandSourceStack source, String name, boolean visible) {
        Villager villager = namedVillager(source, name);
        if (villager == null) return fail(source, "No villager named '" + name + "' found within 320 blocks.");
        villager.setCustomNameVisible(visible);
        success(source, "[NPC] " + name + "'s name tag is now " + (visible ? "shown" : "hidden") + ".");
        return 1;
    }

    public static int listNearbyNpcs(CommandSourceStack source, int radius) {
        ServerPlayer player = player(source);
        var db = database(source);
        var matches = player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(radius),
                e -> e.isAlive() && !(e instanceof Player)
                        && (e instanceof Villager || db.getData(e.getUUID()) != null))
                .stream().sorted(Comparator.comparingDouble(e -> e.distanceToSqr(player))).toList();
        if (matches.isEmpty()) return fail(source, "No nearby NPCs found within " + radius + " blocks.");
        success(source, "[NPC] Nearby NPCs (" + matches.size() + ") within " + radius + " blocks:");
        for (LivingEntity entity : matches.stream().limit(20).toList()) {
            NpcData data = db.getData(entity.getUUID());
            String name = data == null || data.getName().isBlank() ? entity.getName().getString() : data.getName();
            success(source, " - " + name + " | type=" + typeName(entity)
                    + " | " + (entity instanceof Villager ? "villager" : "claimed")
                    + " | dist=" + Math.round(Math.sqrt(entity.distanceToSqr(player)))
                    + (data == null ? "" : " | behavior=" + data.getBehavior()));
        }
        if (matches.size() > 20) success(source, "[NPC] ... and " + (matches.size() - 20) + " more.");
        return 1;
    }

    public static int cleanProfiles(CommandSourceStack source) {
        requireServerThread(source.getServer());
        success(source, "[NPC] Deleted " + NpcDataLoader.deleteErrorProfiles() + " error profile(s).");
        return 1;
    }

    private static List<String> biomeNames(@Nullable VillageData village, ServerLevel level) {
        if (village == null) return List.of();
        return VillageCommand.getVillageBiomes(village, level).entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()).limit(2)
                .map(Map.Entry::getKey).toList();
    }

    private static List<String> structureNames(@Nullable VillageData village, ServerLevel level) {
        if (village == null) return List.of();
        return VillageCommand.getVillageStructuresList(village, level).stream()
                .map(e -> e.getValue().getName()).filter(n -> n != null && !n.isBlank()).toList();
    }

    private static ProfileRequest request(Villager villager, @Nullable VillageData village, List<String> names,
                                           List<String> biomes, @Nullable String curiosity, boolean regen) {
        var existing = regen ? NpcDataLoader.readRawProfile(villager.level(), villager.getName().getString(),
                villager.getUUID()) : null;
        return new ProfileRequest(villager.getName().getString(), villager.getUUID(),
                villager.getVillagerData().profession().unwrapKey().map(k -> k.identifier().toString()).orElse("none"),
                names, village == null ? "" : village.getName(), biomes, curiosity,
                village == null ? new UUID(0, 0) : village.getUUID(), regen, existing == null ? null : existing.toString());
    }

    public static int initVillagers(CommandSourceStack source) {
        ServerPlayer player = player(source);
        VillageData village = homeVillage(source);
        if (village == null) return fail(source, "You are not a member of any village.");
        var villagers = VillageCommand.getVillagersList(player.blockPosition(), player.level(), 10).stream()
                .filter(Villager.class::isInstance).map(Villager.class::cast).toList();
        if (villagers.isEmpty()) return fail(source, "No villagers found nearby.");
        var names = villagers.stream().map(v -> v.getName().getString()).toList();
        var biomes = biomeNames(village, player.level());
        var structures = new ArrayList<>(structureNames(village, player.level()));
        Collections.shuffle(structures);
        int queued = 0;
        Jobs jobs = jobs(source.getServer());
        for (Villager villager : villagers) {
            if (NpcDataLoader.hasProfile(player.level(), villager.getName().getString(), villager.getUUID())) {
                success(source, "[NPC] Skipped " + villager.getName().getString() + " — profile already exists.");
                continue;
            }
            if (jobs.requests.containsKey(villager.getUUID())) continue;
            ProfileRequest request = request(villager, village, names, biomes,
                    structures.isEmpty() ? null : structures.get(queued % structures.size()), false);
            schedule(source, jobs, request, queued * 60L);
            queued++;
        }
        success(source, queued == 0 ? "All nearby villagers already have profiles or pending requests."
                : "Queued AI profile generation for " + queued + " villager(s). Profiles will appear as each finishes.");
        return 1;
    }

    public static int redoProfile(CommandSourceStack source, String name) {
        Villager villager = namedVillager(source, name);
        if (villager == null) return fail(source, "No villager named '" + name + "' found within 320 blocks.");
        Jobs jobs = jobs(source.getServer());
        if (jobs.requests.containsKey(villager.getUUID())) return fail(source, "This NPC already has a pending profile request.");
        VillageData village = homeVillage(source);
        var names = source.getLevel().getEntitiesOfClass(Villager.class, player(source).getBoundingBox().inflate(320))
                .stream().map(v -> v.getName().getString()).toList();
        var structures = structureNames(village, source.getLevel());
        String curiosity = structures.isEmpty() ? null : structures.get(source.getLevel().getRandom().nextInt(structures.size()));
        schedule(source, jobs, request(villager, village, names, biomeNames(village, source.getLevel()), curiosity, true), 0);
        success(source, "[NPC] Regenerating profile for " + name + "...");
        return 1;
    }

    private static Jobs jobs(MinecraftServer server) {
        requireServerThread(server);
        return JOBS.computeIfAbsent(server, ignored -> new Jobs());
    }

    private static boolean current(MinecraftServer server, Jobs jobs, ServerLevel level) {
        return !jobs.stopped && JOBS.get(server) == jobs && server.getLevel(level.dimension()) == level;
    }

    private static void schedule(CommandSourceStack source, Jobs jobs, ProfileRequest request, long delaySeconds) {
        MinecraftServer server = source.getServer();
        RequestContext context = new RequestContext(source, source.getLevel(), request);
        jobs.requests.put(request.uuid(), context);
        if (delaySeconds == 0) {
            generate(server, jobs, context);
            return;
        }
        CompletableFuture<Void> delayed = CompletableFuture.runAsync(() -> server.execute(() -> {
            if (current(server, jobs, context.level)) generate(server, jobs, context);
            else discard(jobs, context);
        }), CompletableFuture.delayedExecutor(delaySeconds, TimeUnit.SECONDS));
        jobs.futures.add(delayed);
        delayed.whenComplete((unused, error) -> server.execute(() -> {
            jobs.futures.remove(delayed);
            if (!current(server, jobs, context.level)) {
                discard(jobs, context);
                return;
            }
            if (error != null) retry(jobs, context, "Delayed request failed: " + error.getMessage());
        }));
    }

    private static void discard(Jobs jobs, RequestContext context) {
        if (jobs.requests.remove(context.request.uuid(), context)) {
            jobs.generating.remove(context.request.uuid());
            PENDING_PROFILES.removeIf(r -> r == context.request);
        }
    }

    static boolean shouldRetry(@Nullable String personality, @Nullable Throwable error) {
        return error != null || personality == null || personality.isBlank() || personality.contains("[AI Error]");
    }

    private static void retry(Jobs jobs, RequestContext context, String reason) {
        UUID id = context.request.uuid();
        jobs.generating.remove(id);
        if (PENDING_PROFILES.stream().noneMatch(r -> r.uuid().equals(id))) PENDING_PROFILES.offer(context.request);
        LOGGER.warn("NPC profile {} queued for retry: {}", id, reason);
        fail(context.source, "[NPC] Profile failed for " + context.request.name() + " — queued for retry. " + reason);
    }

    private static void generate(MinecraftServer server, Jobs jobs, RequestContext context) {
        requireServerThread(server);
        if (!current(server, jobs, context.level) || !jobs.generating.add(context.request.uuid())) return;
        ProfileRequest r = context.request;
        CompletableFuture<String> future = r.isRegen()
                ? AIManager.regenerateNpcPersonality(r.name(), r.profession(), r.existingText(),
                        r.villagerNames(), r.villageName(), r.biomes(), r.structureCuriosity())
                : AIManager.generateNpcPersonality(r.name(), r.profession(),
                        r.villagerNames(), r.villageName(), r.biomes(), r.structureCuriosity());
        jobs.futures.add(future);
        future.whenComplete((personality, error) -> server.execute(() -> {
            jobs.futures.remove(future);
            if (!current(server, jobs, context.level)) {
                discard(jobs, context);
                return;
            }
            if (shouldRetry(personality, error)) {
                retry(jobs, context, error == null ? "Empty or invalid AI response." : error.getMessage());
                return;
            }
            String description = "A " + r.profession().replace("minecraft:", "") + " living in " + r.villageName() + ".";
            try {
                var db = ModEvents.getNpcDatabase(context.level);
                NpcData data = db.getData(r.uuid());
                String assigned = data == null ? "" : NpcDataLoader.normalizeProfileFileName(data.getAiProfileFile());
                boolean wrote = assigned.isBlank()
                        ? NpcDataLoader.writeProfile(r.name(), r.uuid(), description, personality, r.villageName())
                        : NpcDataLoader.writeProfileToFile(assigned, r.name(), r.uuid(), description, personality, r.villageName());
                if (!wrote) {
                    retry(jobs, context, "Could not write profile file.");
                    return;
                }
                db.putData(r.uuid(), applyPersonality(data, r, description, personality));
            } catch (IllegalArgumentException | IllegalStateException e) {
                LOGGER.error("Cannot persist NPC profile {}: {}", r.uuid(), e.getMessage(), e);
                retry(jobs, context, "Could not update NPC data: " + e.getMessage());
                return;
            }
            jobs.generating.remove(r.uuid());
            jobs.requests.remove(r.uuid());
            success(context.source, "[NPC] Profile " + (r.isRegen() ? "updated" : "created") + " for " + r.name() + ".");
        }));
    }

    static NpcData applyPersonality(@Nullable NpcData existing, ProfileRequest request,
                                    String description, String personality) {
        NpcData data = existing == null
                ? new NpcData(request.uuid(), request.name(), request.villageUUID(), description) : existing;
        // Mutate the existing entry: never drop behavior, assigned filename or forever-kid state.
        data.setName(request.name());
        data.setHomeVillageId(request.villageUUID());
        data.setDescription(description);
        data.setPersonality(personality);
        data.setVillageName(request.villageName());
        return data;
    }

    /** The scheduler calls this every five minutes: retry one failed request, not the whole queue. */
    public static void tickRetryQueue(MinecraftServer server) {
        Jobs jobs = jobs(server);
        ProfileRequest request = PENDING_PROFILES.poll();
        if (request == null) return;
        RequestContext context = jobs.requests.get(request.uuid());
        if (context == null) {
            LOGGER.error("Discarding orphan NPC retry request for {} (no originating world).", request.uuid());
            return;
        }
        if (!current(server, jobs, context.level)) {
            discard(jobs, context);
            LOGGER.warn("Discarding NPC retry for unloaded world: {}", request.uuid());
            return;
        }
        generate(server, jobs, context);
    }

    public static void autoReplenishNotesIfNeeded(MinecraftServer server) {
        Jobs jobs = jobs(server);
        if (!shouldReplenish(NpcDataLoader.getThreatNoteCacheSize(), jobs.replenishing)) return;
        jobs.replenishing = true;
        requestNotes(server.createCommandSourceStack(), jobs, 5, true);
    }

    static boolean shouldReplenish(int count, boolean inFlight) {
        return count < 3 && !inFlight;
    }

    public static int fillNotesCache(CommandSourceStack source, int count) {
        requireServerThread(source.getServer());
        if (count < 1 || count > 20) return fail(source, "Note count must be between 1 and 20.");
        success(source, "[NPC] Requesting " + count + " threat notes from AI...");
        requestNotes(source, jobs(source.getServer()), count, false);
        return 1;
    }

    private static void requestNotes(CommandSourceStack source, Jobs jobs, int count, boolean automatic) {
        MinecraftServer server = source.getServer();
        ServerLevel level = source.getLevel();
        String structure = NOTE_STRUCTURE_TYPES[level.getRandom().nextInt(NOTE_STRUCTURE_TYPES.length)];
        var future = AIManager.generateChestThreatNotesBatch(structure, count).orTimeout(90, TimeUnit.SECONDS);
        jobs.futures.add(future);
        future.whenComplete((notes, error) -> server.execute(() -> {
            jobs.futures.remove(future);
            if (automatic) jobs.replenishing = false;
            if (!current(server, jobs, level)) return;
            if (error != null || notes == null || notes.isEmpty()
                    || notes.stream().anyMatch(n -> shouldRetry(n, null))) {
                LOGGER.warn("Threat notes request failed: {}", error == null ? "empty/invalid response" : error.getMessage());
                fail(source, "[NPC] AI failed to return valid threat notes; will retry next cycle.");
                return;
            }
            int before = NpcDataLoader.getThreatNoteCacheSize();
            NpcDataLoader.addThreatNotes(notes);
            int total = NpcDataLoader.getThreatNoteCacheSize();
            if (total != before + notes.size()) {
                fail(source, "[NPC] Could not persist generated threat notes; see server log.");
                return;
            }
            success(source, "[NPC] Added " + notes.size() + " note(s) to cache. Cache total: " + total + ".");
        }));
    }

    /** Call on server stopping before OllamaService.shutdown(); invalidates delayed and in-flight callbacks. */
    public static void shutdown(MinecraftServer server) {
        requireServerThread(server);
        Jobs jobs = JOBS.remove(server);
        if (jobs == null) return;
        jobs.stopped = true;
        PENDING_PROFILES.removeIf(r -> {
            RequestContext context = jobs.requests.get(r.uuid());
            return context != null && context.request == r;
        });
        for (var future : jobs.futures) future.cancel(false);
        jobs.futures.clear();
        jobs.requests.clear();
        jobs.generating.clear();
    }

    private static void requireServerThread(MinecraftServer server) {
        if (!server.isSameThread()) throw new IllegalStateException("NPC mutation must run on the server thread");
    }

    private static void success(CommandSourceStack source, String message) {
        if (!canReceiveFeedback(source)) return;
        source.sendSuccess(() -> Component.literal(message).withStyle(ChatFormatting.GREEN), false);
    }

    private static int fail(CommandSourceStack source, String message) {
        if (canReceiveFeedback(source)) source.sendFailure(Component.literal(message));
        return 0;
    }

    private static boolean canReceiveFeedback(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) return true;
        return source.getServer().getPlayerList().getPlayer(player.getUUID()) == player
                && player.level() == source.getLevel();
    }
}
