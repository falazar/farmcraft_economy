package com.falazar.farmupcraft.util;

import com.falazar.farmupcraft.data.VillageData;
import com.falazar.farmupcraft.events.ModEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.AABB;
import java.util.*;
import static com.falazar.farmupcraft.util.RouteSurvey.*;

public final class RouteSurveyWorld extends RouteSurveyTerrain {
    private static final TagKey<Block> SURFACES = TagKey.create(Registries.BLOCK,
            new ResourceLocation("farmupcraft", "road_surfaces"));
    private final ServerLevel level;
    private final UUID village;
    private final Set<Long> own = new HashSet<>();
    private final Map<Long, String> owners = new HashMap<>();
    private final Map<Long, String> plots = new HashMap<>();
    private final Map<Long, List<StructureStart>> structures = new HashMap<>();
    private final Map<Point, Boolean> roadCache = new HashMap<>();

    public RouteSurveyWorld(ServerLevel level, VillageData village, Kind kind) {
        super(kind, seedColumns(level, village, kind), level.getMinBuildHeight(), level.getMaxBuildHeight() - 1);
        this.level = level;
        this.village = village.getUUID();
        for (var v : ModEvents.getVillageDatabase(level).getValues()) {
            long center = chunkKey(v.getPosition().getMinBlockX(), v.getPosition().getMinBlockZ());
            plots.put(center, v.getUUID() + "/" + center + "/city_center");
            for (var chunk : v.getClaimedChunks()) {
                long key = chunkKey(chunk.getMinBlockX(), chunk.getMinBlockZ());
                owners.put(key, v.getUUID().toString());
                if (v.getUUID().equals(this.village)) own.add(key);
            }
        }
        var db = ModEvents.getChunkDataDatabase(level);
        for (Long key : db.getKeys()) {
            var plot = db.getData(key);
            if (plot != null && plot.getVillageId() != null && !"village".equalsIgnoreCase(plot.getType()))
                plots.put(key, plot.getVillageId() + "/" + key + "/" + plot.getType());
        }
    }

    public static long chunkKey(int x, int z) {
        return (x >> 4 & 0xFFFFFFFFL) | (z >> 4 & 0xFFFFFFFFL) << 32;
    }

    private static Collection<Column> seedColumns(ServerLevel level, VillageData v, Kind kind) {
        Set<Column> columns = new TreeSet<>();
        Set<Long> owned = new HashSet<>();
        for (var c : v.getClaimedChunks()) owned.add(chunkKey(c.getMinBlockX(), c.getMinBlockZ()));
        if (kind == Kind.RAIL) {
            var db = ModEvents.getChunkDataDatabase(level);
            for (Long key : db.getKeys()) {
                var p = db.getData(key);
                if (p == null || !v.getUUID().equals(p.getVillageId()) || !"trainstation".equalsIgnoreCase(p.getType())) continue;
                int x0 = (int) (long) key * 16, z0 = (int) (key >> 32) * 16;
                for (int x = x0; x < x0 + 16; x++) for (int z = z0; z < z0 + 16; z++) columns.add(new Column(x, z));
            }
        } else {
            for (var c : v.getClaimedChunks()) {
                int x0 = c.getMinBlockX(), z0 = c.getMinBlockZ();
                for (int i = 0; i < 16; i++) {
                    if (!owned.contains(chunkKey(x0 - 1, z0))) columns.add(new Column(x0, z0 + i));
                    if (!owned.contains(chunkKey(x0 + 16, z0))) columns.add(new Column(x0 + 15, z0 + i));
                    if (!owned.contains(chunkKey(x0, z0 - 1))) columns.add(new Column(x0 + i, z0));
                    if (!owned.contains(chunkKey(x0, z0 + 16))) columns.add(new Column(x0 + i, z0 + 15));
                }
            }
        }
        return columns;
    }

    @Override protected boolean loaded(int x, int z) {
        return level.getChunkSource().getChunkNow(x >> 4, z >> 4) != null;
    }
    @Override protected int surfaceY(int x, int z) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
        if (chunk == null) throw new IllegalStateException("Survey attempted an unloaded column");
        return chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15);
    }
    private BlockState state(Point p) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(p.x() >> 4, p.z() >> 4);
        if (chunk == null) throw new IllegalStateException("Survey attempted an unloaded block");
        return chunk.getBlockState(new BlockPos(p.x(), p.y(), p.z()));
    }

    public static boolean roadMaterial(BlockState state) {
        if (state.is(SURFACES) || state.getBlock() instanceof SlabBlock || state.getBlock() instanceof StairBlock) return true;
        var id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return id.getNamespace().equals("refurbished_furniture") && id.getPath().endsWith("_stepping_stones")
                || id.getNamespace().equals("mcwbridges") && (id.getPath().endsWith("_bridge")
                        || id.getPath().endsWith("_bridge_stair") || id.getPath().endsWith("_bridge_middle"));
    }

    public boolean isValidRoadPoint(Point p) {
        return road(p);
    }

    @Override protected boolean road(Point p) {
        return roadCache.computeIfAbsent(p, point -> {
            if (point.y() < level.getMinBuildHeight() || point.y() >= level.getMaxBuildHeight()
                    || !loaded(point.x(), point.z()) || surfaceY(point.x(), point.z()) - point.y() > 8) return false;
            BlockState s = state(point);
            if (!roadMaterial(s) || !s.getFluidState().isEmpty() || isElevatedWall(point, s)) return false;
            BlockPos pos = new BlockPos(point.x(), point.y(), point.z());
            var collision = s.getCollisionShape(level, pos);
            boolean deck = collision.toAabbs().stream().anyMatch(b ->
                    b.intersects(new AABB(.35, 0, .35, .65, 1.01, .65)) && b.maxY <= 1.01 && b.maxY > 0);
            return deck && gapClear(point);
        });
    }

    private boolean isBridgeDeck(BlockState state) {
        var id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return id.getNamespace().equals("mcwbridges") && (id.getPath().endsWith("_bridge")
                || id.getPath().endsWith("_bridge_stair") || id.getPath().endsWith("_bridge_middle"));
    }

    private boolean isElevatedWall(Point point, BlockState state) {
        if (isBridgeDeck(state)) return false;
        if (roadMaterial(state(point.offset(0, -1, 0))) && roadMaterial(state(point.offset(0, -2, 0)))) {
            return true;
        }
        for (int[] dir : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            for (int step = 1; step <= 4; step++) {
                int nx = point.x() + dir[0] * step;
                int nz = point.z() + dir[1] * step;
                if (!loaded(nx, nz)) break;
                int neighborTopY = surfaceY(nx, nz) - 1;
                if (point.y() - neighborTopY >= 3) {
                    BlockState ground = state(new Point(nx, Math.max(level.getMinBuildHeight(), neighborTopY), nz));
                    if (ground.getFluidState().isEmpty()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override protected boolean gapClear(Point p) {
        if (!loaded(p.x(), p.z())) return false;
        for (int dy = 1; dy <= 2; dy++) {
            Point q = p.offset(0, dy, 0);
            if (!state(q).getCollisionShape(level, new BlockPos(q.x(), q.y(), q.z())).isEmpty()) return false;
        }
        return true;
    }
    @Override protected String railShape(Point p) {
        if (p.y() < level.getMinBuildHeight() || p.y() >= level.getMaxBuildHeight()) return "";
        BlockState s = state(p);
        return s.getBlock() instanceof BaseRailBlock rail ? s.getValue(rail.getShapeProperty()).getSerializedName() : "";
    }
    @Override public boolean inside(Point p) { return own.contains(chunkKey(p.x(), p.z())); }
    @Override public Set<String> claims(Point p) {
        Set<String> contacts = new TreeSet<>();
        for (int[] d : new int[][]{{0,0},{1,0},{-1,0},{0,1},{0,-1}}) {
            long key = chunkKey(p.x() + d[0], p.z() + d[1]);
            String owner = owners.get(key), plot = plots.get(key);
            if (owner != null && !owner.equals(village.toString())) contacts.add("village:" + owner);
            if (plot != null) contacts.add("plot:" + plot);
        }
        return contacts;
    }

    private List<StructureStart> surfaceStructures(Point p) {
        return structures.computeIfAbsent(chunkKey(p.x(), p.z()), key -> {
            LevelChunk chunk = level.getChunkSource().getChunkNow(p.x() >> 4, p.z() >> 4);
            if (chunk == null) { problems.add("unloaded structure bounds"); return List.of(); }
            Set<StructureStart> found = new HashSet<>(chunk.getAllStarts().values());
            for (var reference : chunk.getAllReferences().entrySet()) {
                if (reference.getKey().step() != GenerationStep.Decoration.SURFACE_STRUCTURES) continue;
                for (long start : reference.getValue()) {
                    LevelChunk origin = level.getChunkSource().getChunkNow((int) start, (int) (start >> 32));
                    if (origin == null) { problems.add("unloaded structure bounds"); continue; }
                    var structure = origin.getStartForStructure(reference.getKey());
                    if (structure != null) found.add(structure);
                }
            }
            return found.stream().filter(s -> s.isValid()
                    && s.getStructure().step() == GenerationStep.Decoration.SURFACE_STRUCTURES
                    && !level.registryAccess().registryOrThrow(Registries.STRUCTURE).getKey(s.getStructure())
                        .getPath().startsWith("village")).toList();
        });
    }

    @Override public Set<String> ruins(Point p) {
        Set<String> result = new TreeSet<>();
        for (StructureStart start : surfaceStructures(p)) {
            boolean touching = start.getPieces().stream().anyMatch(piece -> {
                var b = piece.getBoundingBox();
                return p.x() >= b.minX() - 1 && p.x() <= b.maxX() + 1
                        && p.z() >= b.minZ() - 1 && p.z() <= b.maxZ() + 1
                        && p.y() + 1 >= b.minY() - 1 && p.y() + 1 <= b.maxY() + 1;
            });
            if (touching) {
                var id = level.registryAccess().registryOrThrow(Registries.STRUCTURE).getKey(start.getStructure());
                var pos = start.getBoundingBox();
                result.add(id + "@" + pos.minX() + "," + pos.minY() + "," + pos.minZ());
            }
        }
        return result;
    }
}
