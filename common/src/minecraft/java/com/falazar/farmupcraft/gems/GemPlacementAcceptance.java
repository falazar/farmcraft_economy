package com.falazar.farmupcraft.gems;

import com.falazar.farmupcraft.GemContent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import java.util.*;

/** Explicitly invoked only by disposable acceptance servers. */
public final class GemPlacementAcceptance {
    private GemPlacementAcceptance() {}

    public static void borderAndRetry(ServerLevel level, int cx, int cz) {
        for (int dx = -1; dx <= 2; dx++) for (int dz = -1; dz <= 1; dz++)
            level.getChunk(cx + dx, cz + dz);
        LevelChunk left = level.getChunk(cx, cz), right = level.getChunk(cx + 1, cz);
        String biome = GemPlatform.biomeId(level.getBiome(new BlockPos(cx * 16 + 15, -32, cz * 16 + 8)));
        var hintOrigin = new BlockPos(cx * 16 + 15, 80, cz * 16 + 8);
        require(GemWorld.direction(level, hintOrigin, biome, -32).isEmpty(),
                "Eligible biome directly beneath villager suppresses compass direction");
        var hintRules = new GemRules.Rules(1, 1, GemPlatform.minY(level), GemPlatform.maxY(level),
                1, 2, 6, 15, Map.of("opal", List.of(new GemRules.Band(biome, -32, -47, -17))),
                List.of(biome), Map.of(biome, 64));
        var hintKnowledge = new GemRules.Knowledge(GemRules.Kind.ORDINARY, "opal", biome, "", -32, true);
        require(GemRules.hint(hintRules, hintKnowledge, GemWorld.direction(level, hintOrigin, biome, -32), false)
                        .equals("You could search for opals below level 0."),
                "Same-biome direction-oriented knowledge uses only the loose height bound");
        System.out.println("GEM_SAME_BIOME_HINT_PASS");
        GemRules.Rules fixture = null;
        List<GemRules.Target> crossing = List.of();
        for (long seed = 1; seed <= 256 && crossing.isEmpty(); seed++) {
            Map<String, List<GemRules.Band>> bands = new LinkedHashMap<>();
            for (String gem : GemRules.GEMS) bands.put(gem, List.of(new GemRules.Band(biome, -32, -47, -17)));
            fixture = new GemRules.Rules(1, seed, GemPlatform.minY(level), GemPlatform.maxY(level),
                    1, 2, 6, 15, bands, List.of(biome), Map.of(biome, 64));
            var origin = GemRules.originCandidates(fixture, cx, cz);
            for (String gem : GemRules.GEMS) {
                Random attemptRoll = new Random(GemRules.mix(seed ^ ((long) cx << 32)
                        ^ Integer.toUnsignedLong(cz) ^ GemRules.mix(gem.hashCode()) ^ GemRules.mix(biome.hashCode())));
                if (1 + attemptRoll.nextInt(2) != 1) continue;
                var points = origin.stream().filter(p -> p.gem().equals(gem)
                        && (p.x() >> 4) >= cx && (p.x() >> 4) <= cx + 1 && (p.z() >> 4) == cz
                        && GemPlatform.biomeId(level.getBiome(new BlockPos(p.x(), p.y(), p.z()))).equals(biome)).toList();
                if (points.stream().anyMatch(p -> (p.x() >> 4) == cx)
                        && points.stream().anyMatch(p -> (p.x() >> 4) == cx + 1)) {
                    crossing = points;
                    break;
                }
            }
        }
        require(!crossing.isEmpty(), "Native fixture must contain eligible owned halves from a crossing origin");
        List<GemRules.Target> targets = new ArrayList<>(GemRules.candidates(fixture, cx, cz));
        targets.addAll(GemRules.candidates(fixture, cx + 1, cz));
        reset(level, targets);
        GemWorld.generateOwned((net.minecraft.world.level.WorldGenLevel) level, left, fixture, Heightmap.Types.OCEAN_FLOOR_WG);
        GemWorld.generateOwned(level, right, fixture, Heightmap.Types.OCEAN_FLOOR);
        var forward = snapshot(level, targets);
        require(crossing.stream().anyMatch(p -> (p.x() >> 4) == cx && forward.contains(key(p)))
                && crossing.stream().anyMatch(p -> (p.x() >> 4) == cx + 1 && forward.contains(key(p))),
                "Actual ore blocks must occupy both owned halves");
        reset(level, targets);
        GemWorld.generateOwned(level, right, fixture, Heightmap.Types.OCEAN_FLOOR);
        GemWorld.generateOwned((net.minecraft.world.level.WorldGenLevel) level, left, fixture, Heightmap.Types.OCEAN_FLOOR_WG);
        require(forward.equals(snapshot(level, targets)), "Actual old/new owned passes must match in both visit orders");
        require(GemWorld.generateOwned(level, left, fixture, Heightmap.Types.OCEAN_FLOOR) == 0
                && GemWorld.generateOwned(level, right, fixture, Heightmap.Types.OCEAN_FLOOR) == 0,
                "A lost completion-marker retry must not replace preexisting deterministic ore");
        require(forward.equals(snapshot(level, targets)), "Retry preserves every gem position and type");
        reset(level, targets);
        int retained = 0;
        Set<String> restored = new HashSet<>();
        for (var p : targets) if (forward.contains(key(p)) && restored.add(key(p)) && restored.size() % 2 == 0) {
            level.setBlock(new BlockPos(p.x(), p.y(), p.z()), GemContent.ore(p.gem(), true).defaultBlockState(), 2);
            retained++;
        }
        require(retained > 0 && retained < forward.size(), "Interrupted decoration fixture retains only part of its ores");
        GemWorld.generateOwned(level, left, fixture, Heightmap.Types.OCEAN_FLOOR);
        GemWorld.generateOwned(level, right, fixture, Heightmap.Types.OCEAN_FLOOR);
        require(forward.equals(snapshot(level, targets)),
                "Retrogen after partially saved decoration must reconstruct the identical full ore positions");
        System.out.println("GEM_BORDER_ACCEPTANCE_PASS blocks=" + forward.size() + " crossing_targets=" + crossing.size());
        System.out.println("GEM_PARTIAL_DECORATION_RETRY_PASS retained=" + retained);
        caveExposure(level, left);
    }

    private static void caveExposure(ServerLevel level, LevelChunk chunk) {
        var origin = new BlockPos(GemPlatform.chunkX(chunk) * 16 + 8, -32, GemPlatform.chunkZ(chunk) * 16 + 8);
        String biome = GemPlatform.biomeId(level.getBiome(origin));
        for (var pos : BlockPos.betweenClosed(origin.offset(-3, -3, -3), origin.offset(3, 3, 3)))
            level.setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++)
            level.setBlock(new BlockPos(origin.getX() + x, 0, origin.getZ() + z), Blocks.STONE.defaultBlockState(), 2);
        level.setBlock(origin.east(), Blocks.AIR.defaultBlockState(), 2);
        int hidden = 0, exposed = 0, preserved = 0;
        for (String gem : GemRules.GEMS) for (long seed = 0; seed < 100; seed++) {
            var rules = new GemRules.Rules(1, seed, GemPlatform.minY(level), GemPlatform.maxY(level),
                    1, 2, 6, 15, Map.of(gem, List.of(new GemRules.Band(biome, -32, -47, -17))),
                    List.of(biome), Map.of(biome, 64));
            var target = new GemRules.Target(gem, biome, origin.getX(), origin.getY(), origin.getZ());
            level.setBlock(origin.east(), Blocks.STONE.defaultBlockState(), 2);
            require(origin.equals(GemWorld.placementPosition(chunk, rules, target, Heightmap.Types.OCEAN_FLOOR,
                    p -> GemPlatform.biomeId(level.getBiome(p)), level::getBlockState, Set.of(origin))),
                    "Already buried targets stay unchanged even on a hide roll");
            level.setBlock(origin.east(), Blocks.AIR.defaultBlockState(), 2);
            var chosen = GemWorld.placementPosition(chunk, rules, target, Heightmap.Types.OCEAN_FLOOR,
                    p -> GemPlatform.biomeId(level.getBiome(p)), level::getBlockState, Set.of(origin));
            require(chosen != null, "A solid nearby cave wall preserves the target count");
            if (GemRules.hideExposedTarget(seed, target)) {
                hidden++;
                require(!chosen.equals(origin), "Hidden half moves away from cave exposure");
                for (var direction : net.minecraft.core.Direction.values())
                    require(!level.getBlockState(chosen.relative(direction)).isAir(), "Relocated ore is fully buried");
            } else {
                exposed++;
                require(chosen.equals(origin), "Allowed half remains at its original exposed position");
            }
            require((chosen.getX() >> 4) == GemPlatform.chunkX(chunk)
                    && (chosen.getZ() >> 4) == GemPlatform.chunkZ(chunk), "Relocation stays in the owning chunk");
            level.setBlock(chosen, GemContent.ore(gem, true).defaultBlockState(), 2);
            require(chosen.equals(GemWorld.placementPosition(chunk, rules, target, Heightmap.Types.OCEAN_FLOOR,
                    p -> GemPlatform.biomeId(level.getBiome(p)), level::getBlockState, Set.of(origin))),
                    "A retry selects the existing ore, not another buried copy");
            preserved++;
            level.setBlock(chosen, Blocks.STONE.defaultBlockState(), 2);
            level.setBlock(origin, GemContent.ore(gem, true).defaultBlockState(), 2);
            require(origin.equals(GemWorld.placementPosition(chunk, rules, target, Heightmap.Types.OCEAN_FLOOR,
                    p -> GemPlatform.biomeId(level.getBiome(p)), level::getBlockState, Set.of(origin))),
                    "Preexisting exposed ore is never relocated");
            level.setBlock(origin, Blocks.STONE.defaultBlockState(), 2);
            if (GemRules.hideExposedTarget(seed, target)) {
                var alternatives = GemRules.buriedAlternatives(target).stream()
                        .map(p -> new BlockPos(p.x(), p.y(), p.z())).filter(p -> !p.equals(origin.east())).toList();
                for (var pos : alternatives) level.setBlock(pos, Blocks.BEDROCK.defaultBlockState(), 2);
                require(GemWorld.placementPosition(chunk, rules, target, Heightmap.Types.OCEAN_FLOOR,
                        p -> GemPlatform.biomeId(level.getBiome(p)), level::getBlockState, Set.of(origin)) == null,
                        "Without a buried host the rejected exposed target is omitted");
                for (var pos : alternatives) level.setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
            }
        }
        require(hidden > 200 && hidden < 300 && exposed + hidden == 500, "Deterministic exposure roll is balanced");
        System.out.println("GEM_CAVE_EXPOSURE_PASS hidden=" + hidden + " exposed=" + exposed + " preserved=" + preserved);
        cavePartialRetry(level, chunk, biome);
    }

    private static void cavePartialRetry(ServerLevel level, LevelChunk chunk, String biome) {
        var min = new BlockPos(GemPlatform.chunkX(chunk) * 16, -48, GemPlatform.chunkZ(chunk) * 16);
        var max = min.offset(15, 32, 15);
        Map<String, List<GemRules.Band>> bands = new LinkedHashMap<>();
        for (String gem : GemRules.GEMS) bands.put(gem, List.of(new GemRules.Band(biome, -32, -47, -17)));
        int baselineTotal = 0, placedTotal = 0;
        for (long seed = 1; seed <= 10; seed++) {
            var rules = new GemRules.Rules(1, seed, GemPlatform.minY(level), GemPlatform.maxY(level),
                    1, 2, 6, 15, bands, List.of(biome), Map.of(biome, 64));
            resetCave(level, min, max);
            Set<BlockPos> baseline = new HashSet<>();
            for (var target : GemRules.candidates(rules, GemPlatform.chunkX(chunk), GemPlatform.chunkZ(chunk))) {
                var pos = new BlockPos(target.x(), target.y(), target.z());
                if (target.y() >= -47 && target.y() <= -17 && GemWorld.replaceable(level.getBlockState(pos))
                        && GemPlatform.biomeId(level.getBiome(pos)).equals(biome)) baseline.add(pos);
            }
            baselineTotal += baseline.size();
            GemWorld.generateOwned(level, chunk, rules, Heightmap.Types.OCEAN_FLOOR);
            Map<BlockPos, net.minecraft.world.level.block.state.BlockState> complete = new LinkedHashMap<>();
            for (var pos : BlockPos.betweenClosed(min, max)) {
                var state = level.getBlockState(pos);
                if (state.getBlock() != Blocks.STONE && !state.isAir()) complete.put(pos.immutable(), state);
            }
            require(!complete.isEmpty(), "Cave fixture generates actual ores");
            placedTotal += complete.size();
            require(GemWorld.generateOwned(level, chunk, rules, Heightmap.Types.OCEAN_FLOOR) == 0,
                    "Complete cave placement retry creates no additional ore");
            resetCave(level, min, max);
            int retained = 0;
            for (var entry : complete.entrySet()) if (retained++ % 2 == 0)
                level.setBlock(entry.getKey(), entry.getValue(), 2);
            GemWorld.generateOwned(level, chunk, rules, Heightmap.Types.OCEAN_FLOOR);
            for (var pos : BlockPos.betweenClosed(min, max))
                require(level.getBlockState(pos).equals(complete.getOrDefault(pos,
                                caveAir(pos, min) ? Blocks.AIR.defaultBlockState() : Blocks.STONE.defaultBlockState())),
                        "Partially saved cave placement reconstructs identical positions/types, seed=" + seed + " pos=" + pos);
        }
        System.out.println("GEM_CAVE_PARTIAL_RETRY_PASS seeds=10 baseline=" + baselineTotal + " placed=" + placedTotal);
    }

    private static void resetCave(ServerLevel level, BlockPos min, BlockPos max) {
        for (var pos : BlockPos.betweenClosed(min, max))
            level.setBlock(pos, caveAir(pos, min) ? Blocks.AIR.defaultBlockState() : Blocks.STONE.defaultBlockState(), 2);
    }

    private static boolean caveAir(BlockPos pos, BlockPos min) {
        return (pos.getX() - min.getX()) % 4 == 2 && pos.getY() >= -45 && pos.getY() <= -20
                && pos.getZ() > min.getZ() && pos.getZ() < min.getZ() + 15;
    }

    private static void reset(ServerLevel level, List<GemRules.Target> targets) {
        for (var p : targets) {
            level.setBlock(new BlockPos(p.x(), p.y(), p.z()), Blocks.STONE.defaultBlockState(), 2);
            level.setBlock(new BlockPos(p.x(), 0, p.z()), Blocks.STONE.defaultBlockState(), 2);
        }
    }
    private static String key(GemRules.Target p) { return p.x() + ":" + p.y() + ":" + p.z() + ":" + p.gem(); }
    private static Set<String> snapshot(ServerLevel level, List<GemRules.Target> targets) {
        Set<String> positions = new TreeSet<>();
        for (var p : targets) if (level.getBlockState(new BlockPos(p.x(), p.y(), p.z())).is(GemContent.ore(p.gem(), true)))
            positions.add(key(p));
        return positions;
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
