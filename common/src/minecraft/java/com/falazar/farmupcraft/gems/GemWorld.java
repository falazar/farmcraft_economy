package com.falazar.farmupcraft.gems;

import com.falazar.farmupcraft.GemContent;
import com.falazar.farmupcraft.data.NpcData;
import com.falazar.farmupcraft.events.MineshaftOreEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.*;
import java.util.*;

/** Live access is server-thread; native decoration owns its destination chunk on the generation worker. */
public final class GemWorld {
    public static final int HINT_RADIUS = 128;
    public static final int SAMPLE_BUDGET = 256;
    public static final int PLACE_BUDGET = 64;
    public static final int CHUNK_BUDGET = 8;
    public static final int SURFACE_SCAN_LIMIT = 96;
    private static final Map<ServerLevel, GemWorld> WORLDS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Set<ServerLevel> DISABLED_WORLDS = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final ServerLevel level;
    private final GemRules.Rules rules;
    private final LinkedHashMap<Long, Work> queue = new LinkedHashMap<>();
    private final Set<Long> freshlyGenerated = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final java.util.concurrent.atomic.AtomicLong nativeGenerationPasses = new java.util.concurrent.atomic.AtomicLong();
    private int lastTickSamples, lastTickPlacements;
    private record Work(List<GemRules.Target> targets, int cursor, LevelChunk owner) {}
    private GemWorld(ServerLevel level, GemRules.Rules rules) { this.level = level; this.rules = rules; }

    public static void initialize(ServerLevel level) {
        if (!level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)) return;
        if (WORLDS.containsKey(level) || DISABLED_WORLDS.contains(level)) return;
        try {
            var policy = GemFiles.policy(GemPlatform.configDir().resolve("gem_biome_config.json"));
            var source = level.getChunkSource().getGenerator().getBiomeSource().possibleBiomes();
            List<GemRules.Biome> eligible = new ArrayList<>();
            List<String> overworld = new ArrayList<>();
            for (var holder : source) {
                String id = GemPlatform.biomeId(holder);
                boolean cave = tagged(holder, policy.cave_biome_tags())
                        || policy.excluded_biomes().contains(id);
                if (cave || id.contains("deferred_placeholder")) continue;
                overworld.add(id);
                boolean ocean = holder.is(GemPlatform.biomeTag("minecraft:is_ocean"))
                        || id.contains("ocean");
                // Reference floors are conservative anchors, not sea level. Actual burial remains a per-column check.
                int reference = ocean ? verifiedOceanFloor(level, holder, id.contains("deep") ? 16 : 32)
                        : GemRules.referenceElevation(id);
                var biome = new GemRules.Biome(id, cave, tagged(holder, policy.rare_biome_tags()), ocean, reference);
                if (policy.eligible(biome)) eligible.add(biome);
            }
            Path dir = level.getServer().getWorldPath(LevelResource.ROOT).resolve("farmupcraft").resolve("gems");
            Path sentinel = dir.resolve("initialized-v1");
            var worldState = GemWorldState.get(level);
            if ((eligible.size() < 3 || overworld.stream().distinct().count() < 4)
                    && !Files.exists(dir.resolve("gem_biomes.json"))
                    && !Files.exists(sentinel) && worldState.version() == 0) {
                System.getLogger("farmupcraft.gems").log(System.Logger.Level.WARNING,
                        "Gem gameplay disabled: this Overworld biome source cannot supply three eligible biomes and a rumor decoy. No rules, hints, or deposits are created.");
                DISABLED_WORLDS.add(level);
                return;
            }
            if (worldState.version() != 0 && worldState.version() != GemRules.VERSION)
                throw new IllegalStateException("Unsupported persisted gem world generation version");
            var rules = GemFiles.load(dir.resolve("gem_biomes.json"), level.getSeed(), eligible, overworld,
                    GemPlatform.minY(level), GemPlatform.maxY(level), Files.exists(sentinel) || worldState.version() > 0);
            GemFiles.write(sentinel, Map.of("generation_version", GemRules.VERSION));
            worldState.initialized();
            GemFiles.migrateProfiles(GemPlatform.gameDir().resolve("npcData"));
            WORLDS.put(level, new GemWorld(level, rules));
        } catch (Exception error) {
            throw new IllegalStateException("FarmUpCraft gems initialization failed; no rules were rerolled", error);
        }
    }

    private static boolean tagged(net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> holder,
                                  List<String> tags) {
        return tags.stream().anyMatch(id -> holder.is(GemPlatform.biomeTag(id))
                || id.startsWith("c:") && holder.is(GemPlatform.biomeTag("forge:" + id.substring(2))));
    }

    private static int verifiedOceanFloor(ServerLevel level,
            net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> biome, int proposed) {
        var generator = level.getChunkSource().getGenerator();
        var random = level.getChunkSource().randomState();
        Random samples = new Random(GemRules.mix(level.getSeed() ^ GemPlatform.biomeId(biome).hashCode()));
        int lowest = Integer.MAX_VALUE;
        for (int i = 0; i < 2048; i++) {
            int x = samples.nextInt(16384) - 8192, z = samples.nextInt(16384) - 8192;
            if (generator.getBiomeSource().getNoiseBiome(x >> 2, 64 >> 2, z >> 2, random.sampler()).equals(biome))
                lowest = Math.min(lowest, generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, random));
        }
        // Leave room for a full 31-level band and burial even at the world's lower bound.
        if (lowest == Integer.MAX_VALUE) {
            if (GemPlatform.biomeId(biome).startsWith("minecraft:")) {
                System.getLogger("farmupcraft.gems").log(System.Logger.Level.INFO,
                        "Using installed-generator verified vanilla ocean reference " + proposed + " for " + GemPlatform.biomeId(biome));
                return proposed;
            }
            int reference = GemPlatform.minY(level) + 35;
            System.getLogger("farmupcraft.gems").log(System.Logger.Level.WARNING,
                    "No ocean-floor sample for " + GemPlatform.biomeId(biome)
                            + "; using conservative bottom-world reference " + reference);
            return reference;
        }
        return Math.min(proposed, lowest - 1);
    }

    public static void enqueue(ServerLevel level, LevelChunk chunk, boolean fresh) {
        if (!level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)) return;
        initialize(level);
        GemWorld world = WORLDS.get(level);
        if (world == null) return;
        if (GemEvents.version(chunk) == GemRules.VERSION) return;
        if (GemEvents.version(chunk) != 0)
            throw new IllegalStateException("Unsupported gems_generation_version in " + chunk.getPos());
        long key = GemPlatform.chunkKey(chunk);
        if (world.freshlyGenerated.remove(key)) {
            GemEvents.complete(chunk);
            GemPlatform.unsaved(chunk);
            return;
        }
        world.queue.compute(key, (ignored, existing) -> existing != null && existing.owner == chunk
                ? existing : new Work(null, 0, chunk));
    }

    public static void unload(ServerLevel level, LevelChunk chunk) {
        var world = WORLDS.get(level);
        if (world != null) {
            long key = GemPlatform.chunkKey(chunk);
            var work = world.queue.get(key);
            if (work != null && work.owner == chunk) world.queue.remove(key);
        }
    }

    public static void stop(net.minecraft.server.MinecraftServer server) {
        WORLDS.keySet().removeIf(level -> level.getServer() == server);
        DISABLED_WORLDS.removeIf(level -> level.getServer() == server);
    }

    public static void tick(ServerLevel level) {
        tick(level, SAMPLE_BUDGET, PLACE_BUDGET);
    }

    public static void tick(ServerLevel level, int sampleBudget, int placementBudget) {
        if (sampleBudget < 1 || sampleBudget > SAMPLE_BUDGET || placementBudget < 1 || placementBudget > PLACE_BUDGET)
            throw new IllegalArgumentException("Gem work budgets must be positive and bounded");
        var world = WORLDS.get(level);
        if (world == null) return;
        int samples = 0, placed = 0, chunks = 0;
        Map<Long, Work> pending = new LinkedHashMap<>();
        var iterator = world.queue.entrySet().iterator();
        while (iterator.hasNext() && samples < sampleBudget && placed < placementBudget && chunks++ < CHUNK_BUDGET) {
            var entry = iterator.next();
            var chunk = level.getChunkSource().getChunkNow((int) (long) entry.getKey(), (int) (entry.getKey() >> 32));
            if (chunk == null) { iterator.remove(); continue; }
            if (GemEvents.version(chunk) == GemRules.VERSION) { iterator.remove(); continue; }
            Work work = entry.getValue();
            if (work.owner != chunk) work = new Work(null, 0, chunk);
            // Vanilla block-biome zoom can sample one quart across a boundary. Wait, never force that neighbor.
            if (!loadedNeighborhood(level, chunk)) {
                pending.put(entry.getKey(), work);
                iterator.remove();
                continue;
            }
            if (work.targets == null) work = new Work(placementTargets(world.rules,
                    GemPlatform.chunkX(chunk), GemPlatform.chunkZ(chunk)), 0, chunk);
            var originals = originalPositions(work.targets);
            int cursor = work.cursor;
            while (cursor < work.targets.size() && samples < sampleBudget && placed < placementBudget) {
                var target = work.targets.get(cursor++);
                samples++;
                BlockPos pos = placementPosition(chunk, world.rules, target, Heightmap.Types.OCEAN_FLOOR,
                        p -> GemPlatform.biomeId(level.getBiome(p)), level::getBlockState, originals);
                if (pos == null) continue;
                var state = chunk.getBlockState(pos);
                if (!replaceable(state)) continue;
                boolean deep = state.is(Blocks.DEEPSLATE) || pos.getY() < 0;
                if (level.setBlock(pos, GemContent.ore(target.gem(), deep).defaultBlockState(), 2 | 16)) placed++;
            }
            if (cursor == work.targets.size()) {
                GemEvents.complete(chunk);
                GemPlatform.unsaved(chunk);
                iterator.remove();
            } else {
                pending.put(entry.getKey(), new Work(work.targets, cursor, chunk));
                iterator.remove();
            }
        }
        world.queue.putAll(pending);
        world.lastTickSamples = samples;
        world.lastTickPlacements = placed;
    }

    public static boolean replaceable(net.minecraft.world.level.block.state.BlockState state) {
        if (state.isAir() || state.hasBlockEntity() || !state.getFluidState().isEmpty()
                || MineshaftOreEvents.isOre(state)) return false;
        String path = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        if (path.endsWith("_ore") || path.startsWith("ore_")
                || path.startsWith("polished_") || path.startsWith("chiseled_") || path.startsWith("cut_")
                || path.contains("brick") || path.contains("tiles") || path.contains("cobblestone")
                || state.is(net.minecraft.tags.BlockTags.SLABS) || state.is(net.minecraft.tags.BlockTags.STAIRS)
                || state.is(net.minecraft.tags.BlockTags.WALLS)) return false;
        return state.is(GemPlatform.blockTag("farmupcraft:gem_replaceable"));
    }

    /** Normal feature generation: complete owned pass after terrain decoration, without touching neighbor chunks. */
    public static void generateNew(net.minecraft.world.level.WorldGenLevel region,
                                   net.minecraft.world.level.chunk.ChunkAccess chunk) {
        ServerLevel level = region.getLevel();
        if (!level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)) return;
        GemWorld world = WORLDS.get(level);
        if (world == null) {
            if (DISABLED_WORLDS.contains(level)) return;
            throw new IllegalStateException("Gem rules must initialize before terrain feature generation");
        }
        int cx = chunk.getPos().getMinBlockX() >> 4, cz = chunk.getPos().getMinBlockZ() >> 4;
        generateOwned(region, chunk, world.rules, Heightmap.Types.OCEAN_FLOOR_WG);
        world.nativeGenerationPasses.incrementAndGet();
        world.freshlyGenerated.add(Integer.toUnsignedLong(cx) | ((long) cz << 32));
    }

    public static int generateOwned(ServerLevel level, net.minecraft.world.level.chunk.ChunkAccess chunk,
                                    GemRules.Rules rules, Heightmap.Types heightmap) {
        if (!(chunk instanceof LevelChunk full) || !loadedNeighborhood(level, full))
            throw new IllegalStateException("Owned live placement requires loaded biome-sampling neighbors");
        return generateOwned(chunk, rules, heightmap, pos -> GemPlatform.biomeId(level.getBiome(pos)), level::getBlockState);
    }

    public static int generateOwned(net.minecraft.world.level.WorldGenLevel region,
                                    net.minecraft.world.level.chunk.ChunkAccess chunk,
                                    GemRules.Rules rules, Heightmap.Types heightmap) {
        return generateOwned(chunk, rules, heightmap, pos -> GemPlatform.biomeId(region.getBiome(pos)), region::getBlockState);
    }

    private static int generateOwned(net.minecraft.world.level.chunk.ChunkAccess chunk,
                                     GemRules.Rules rules, Heightmap.Types heightmap,
                                     java.util.function.Function<BlockPos, String> biomeAt,
                                     java.util.function.Function<BlockPos, net.minecraft.world.level.block.state.BlockState> stateAt) {
        int cx = chunk.getPos().getMinBlockX() >> 4, cz = chunk.getPos().getMinBlockZ() >> 4;
        int placed = 0;
        var targets = placementTargets(rules, cx, cz);
        var originals = originalPositions(targets);
        for (var target : targets) {
            BlockPos pos = placementPosition(chunk, rules, target, heightmap, biomeAt, stateAt, originals);
            if (pos == null) continue;
            var host = chunk.getBlockState(pos);
            if (!replaceable(host)) continue;
            GemPlatform.place(chunk, pos, GemContent.ore(target.gem(),
                    host.is(Blocks.DEEPSLATE) || pos.getY() < 0).defaultBlockState());
            placed++;
        }
        return placed;
    }

    static BlockPos placementPosition(net.minecraft.world.level.chunk.ChunkAccess chunk,
                                              GemRules.Rules rules, GemRules.Target target, Heightmap.Types heightmap,
                                              java.util.function.Function<BlockPos, String> biomeAt,
                                              java.util.function.Function<BlockPos, net.minecraft.world.level.block.state.BlockState> stateAt,
                                              Set<BlockPos> originals) {
        BlockPos original = new BlockPos(target.x(), target.y(), target.z());
        if (!validPlacement(chunk, rules, target, original, heightmap, biomeAt)) return null;
        // Existing ores occupy their deterministic choice on retries; never relocate a second copy.
        if (!replaceable(chunk.getBlockState(original))) return original;
        if (!GemRules.hideExposedTarget(rules.seed(), target) || !touchesAir(original, stateAt)) return original;
        for (var alternative : GemRules.buriedAlternatives(target)) {
            var pos = new BlockPos(alternative.x(), alternative.y(), alternative.z());
            if (!originals.contains(pos) && validPlacement(chunk, rules, target, pos, heightmap, biomeAt)
                    && !touchesAir(pos, stateAt)) return pos;
        }
        return null;
    }

    private static List<GemRules.Target> placementTargets(GemRules.Rules rules, int cx, int cz) {
        Map<String, GemRules.Target> targets = new LinkedHashMap<>();
        for (var target : GemRules.candidates(rules, cx, cz))
            targets.putIfAbsent(target.x() + ":" + target.y() + ":" + target.z() + ":" + target.biome(), target);
        return List.copyOf(targets.values());
    }

    private static Set<BlockPos> originalPositions(List<GemRules.Target> targets) {
        // A relocation must not occupy a later target's origin and change partial-retry choices.
        Set<BlockPos> positions = new HashSet<>();
        for (var target : targets) positions.add(new BlockPos(target.x(), target.y(), target.z()));
        return positions;
    }

    private static boolean validPlacement(net.minecraft.world.level.chunk.ChunkAccess chunk, GemRules.Rules rules,
                                          GemRules.Target target, BlockPos pos, Heightmap.Types heightmap,
                                          java.util.function.Function<BlockPos, String> biomeAt) {
        var state = chunk.getBlockState(pos);
        if (!replaceable(state) && !isGemOre(state)) return false;
        if (rules.gems().get(target.gem()).stream().noneMatch(b -> b.id().equals(target.biome()) && b.contains(pos.getY()))
                || !biomeAt.apply(pos).equals(target.biome())) return false;
        int surface = terrainSurface(chunk, pos, heightmap);
        return surface != Integer.MIN_VALUE && pos.getY() <= surface - 5;
    }

    private static boolean isGemOre(net.minecraft.world.level.block.state.BlockState state) {
        var id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return id.getNamespace().equals("farmupcraft") && id.getPath().endsWith("_ore")
                && GemRules.GEMS.stream().anyMatch(gem -> id.getPath().equals(gem + "_ore")
                    || id.getPath().equals("deepslate_" + gem + "_ore"));
    }

    private static boolean touchesAir(BlockPos pos,
                                      java.util.function.Function<BlockPos, net.minecraft.world.level.block.state.BlockState> stateAt) {
        for (var direction : net.minecraft.core.Direction.values()) {
            if (stateAt.apply(pos.relative(direction)).isAir()) return true;
        }
        return false;
    }

    private static int terrainSurface(net.minecraft.world.level.chunk.ChunkAccess chunk, BlockPos pos, Heightmap.Types heightmap) {
        int top = chunk.getHeight(heightmap, pos.getX() & 15, pos.getZ() & 15);
        for (int steps = 0; steps < SURFACE_SCAN_LIMIT; steps++, top--) {
            var surface = chunk.getBlockState(new BlockPos(pos.getX(), top, pos.getZ()));
            if (!surface.hasBlockEntity() && surface.getFluidState().isEmpty()
                    && (replaceable(surface) || MineshaftOreEvents.isOre(surface) || surface.is(Blocks.BEDROCK)))
                return top;
        }
        return Integer.MIN_VALUE;
    }

    private static boolean loadedNeighborhood(ServerLevel level, LevelChunk chunk) {
        int cx = GemPlatform.chunkX(chunk), cz = GemPlatform.chunkZ(chunk);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
            if (level.getChunkSource().getChunkNow(cx + dx, cz + dz) == null) return false;
        return true;
    }

    private static String loadedBiome(ServerLevel level, LevelChunk anchor, BlockPos pos) {
        boolean[] available = {true};
        var manager = level.getBiomeManager().withDifferentSource((qx, qy, qz) -> {
            var chunk = level.getChunkSource().getChunkNow(qx >> 2, qz >> 2);
            if (chunk != null) return chunk.getNoiseBiome(qx, qy, qz);
            available[0] = false;
            return anchor.getNoiseBiome(GemPlatform.chunkX(anchor) << 2, qy, GemPlatform.chunkZ(anchor) << 2);
        });
        var biome = manager.getBiome(pos);
        return available[0] ? GemPlatform.biomeId(biome) : "";
    }

    public static GemRules.Rules rules(ServerLevel level) {
        initialize(level);
        var world = WORLDS.get(level.getServer().overworld());
        if (world == null) { initialize(level.getServer().overworld()); world = WORLDS.get(level.getServer().overworld()); }
        if (world == null) throw new IllegalStateException("Gem gameplay is unavailable for this world's biome source");
        return world.rules;
    }

    public static boolean available(ServerLevel level) {
        initialize(level.getServer().overworld());
        return WORLDS.containsKey(level.getServer().overworld());
    }

    public static long nativePasses(ServerLevel level) {
        var world = WORLDS.get(level);
        return world == null ? 0 : world.nativeGenerationPasses.get();
    }
    public static int lastSamples(ServerLevel level) { return WORLDS.get(level).lastTickSamples; }
    public static int lastPlacements(ServerLevel level) { return WORLDS.get(level).lastTickPlacements; }

    public static String direction(ServerLevel level, BlockPos origin, String biome, int y) {
        var current = level.getChunkSource().getChunkNow(origin.getX() >> 4, origin.getZ() >> 4);
        if (current != null && loadedBiome(level, current,
                new BlockPos(origin.getX(), y, origin.getZ())).equals(biome)) return "";
        int best = Integer.MAX_VALUE, dx = 0, dz = 0;
        for (int x = origin.getX() - HINT_RADIUS; x <= origin.getX() + HINT_RADIUS; x += 16)
            for (int z = origin.getZ() - HINT_RADIUS; z <= origin.getZ() + HINT_RADIUS; z += 16) {
                int distance = (x - origin.getX()) * (x - origin.getX()) + (z - origin.getZ()) * (z - origin.getZ());
                if (distance == 0 || distance > HINT_RADIUS * HINT_RADIUS || distance >= best) continue;
                var chunk = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
                if (chunk == null) continue;
                if (loadedBiome(level, chunk, new BlockPos(x, y, z)).equals(biome)) {
                    best = distance; dx = x - origin.getX(); dz = z - origin.getZ();
                }
            }
        if (best == Integer.MAX_VALUE) return "";
        String[] compass = {"east", "southeast", "south", "southwest", "west", "northwest", "north", "northeast"};
        return compass[Math.floorMod((int) Math.round(Math.atan2(dz, dx) / (Math.PI / 4)), 8)];
    }

    public static String hint(ServerLevel level, UUID identity, BlockPos origin, String question) {
        initialize(level.getServer().overworld());
        if (!available(level)) return "Gem information is unavailable in this world; I don't know where to find gems.";
        var rules = rules(level);
        var knowledge = GemRules.knowledge(rules, identity);
        String requested = GemRules.requestedGem(question);
        if (knowledge.kind() == GemRules.Kind.NONE) return requested.isEmpty() ? "I don't know where to find gems."
                : "I don't know where to find " + GemRules.plural(requested) + ".";
        if (!requested.isEmpty() && !requested.equals(knowledge.gem())) return "I don't know where to find " + GemRules.plural(requested) + ".";
        String direction = knowledge.kind() == GemRules.Kind.ORDINARY && knowledge.direction()
                && level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)
                ? direction(level, origin, knowledge.biome(), knowledge.y()) : "";
        String text = GemRules.hint(rules, knowledge, direction, level.getRandom().nextBoolean());
        return level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD) ? text : "In the Overworld: " + text;
    }

    public static boolean lacksRequestedKnowledge(ServerLevel level, UUID identity, String question) {
        initialize(level.getServer().overworld());
        if (!available(level)) return true;
        var knowledge = GemRules.knowledge(rules(level), identity);
        String requested = GemRules.requestedGem(question);
        return knowledge.kind() == GemRules.Kind.NONE
                || !requested.isEmpty() && !requested.equals(knowledge.gem());
    }

    public record ConversationGem(GemRules.Kind kind, String fact) {}

    public static ConversationGem conversationGem(ServerLevel level, UUID identity, BlockPos origin, String message) {
        if (!GemRules.gemQuestion(message) && (!available(level) || !GemRules.unsolicitedGemTopic(
                GemRules.knowledge(rules(level), identity).kind(), level.getRandom().nextInt(20)))) return null;
        var kind = lacksRequestedKnowledge(level, identity, message) ? GemRules.Kind.NONE
                : GemRules.knowledge(rules(level), identity).kind();
        return new ConversationGem(kind, hint(level, identity, origin, message));
    }

    public static String enforceGemReply(ServerLevel level, ConversationGem topic, String reply) {
        if (topic.kind() == GemRules.Kind.NONE) return reply;
        if (GemRules.validKnowledgeReply(topic.kind(), topic.fact(), reply, GemPlatform.biomeIds(level))) return reply;
        com.mojang.logging.LogUtils.getLogger().error("NPC gem reply failed fact validation: mode={}, fact={}, reply={}",
                topic.kind(), topic.fact(), reply);
        return "[NPC Error] The reply changed or added unverified gem information. Please try again.";
    }

    public static NpcData generic(String name, UUID uuid) {
        return new NpcData(uuid, name, new UUID(0, 0), "A local villager. Speak briefly and naturally; do not invent geology.",
                "friendly and practical", "");
    }

    /** Preserve late player changes in freshly created gem-note chests, without migrating legacy chests. */
    public static int threatNoteSlot(net.minecraft.world.level.block.entity.BlockEntity chest, Container container) {
        if (!GemEvents.chestRolled(chest) || container.getItem(0).isEmpty()) return 0;
        for (int slot = 1; slot < container.getContainerSize(); slot++) if (container.getItem(slot).isEmpty()) return slot;
        return -1;
    }

    /** Called only immediately after FarmUpCraft creates an empty special-note chest, never on scans. */
    public static void newRuinChest(ServerLevel level, BlockPos pos, long structureId) {
        var entity = level.getBlockEntity(pos);
        if (!(entity instanceof Container container) || GemEvents.chestRolled(entity)) return;
        GemEvents.markChest(entity);
        if (!available(level)) return;
        var identity = new UUID(structureId, pos.asLong() ^ 0x5275696e4e6f7465L);
        if (GemRules.knowledge(rules(level), identity).kind() == GemRules.Kind.NONE) return;
        String text = hint(level, identity, pos, "");
        for (int slot = 1; slot < container.getContainerSize(); slot++) if (container.getItem(slot).isEmpty()) {
            ItemStack note = new ItemStack(Items.PAPER);
            GemPlatform.nameNote(note, text);
            container.setItem(slot, note);
            entity.setChanged();
            break;
        }
    }
}
