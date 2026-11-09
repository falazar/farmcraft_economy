package com.falazar.farmupcraft;

import com.falazar.farmupcraft.events.ModEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.DyeColor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public final class VillagerHomeSigns {
    private static final String KEY = "farmupcraft_home_sign";
    private static final String RESIDENTS = "residents";
    private static final String NEXT_CHECK = "farmupcraft_home_sign_next_check";
    private static final long CHECK_INTERVAL = 2L * 60 * 60 * 20;
    private static final int SEARCH_RADIUS = 24;
    private static final int SEARCH_HEIGHT = 12;
    private static final int MAX_VISITS = 4096;
    private static final Direction[] HORIZONTAL = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};

    private VillagerHomeSigns() {}

    public static void tick(Villager villager) {
        if (Math.floorMod(villager.tickCount + villager.getUUID().hashCode(), 200) != 0
                || !(villager.level() instanceof ServerLevel level)) return;
        var persistent = villager.getPersistentData();
        long now = level.getGameTime();
        if (persistent.contains(NEXT_CHECK) && now < persistent.getLong(NEXT_CHECK)) return;
        persistent.putLong(NEXT_CHECK, now + CHECK_INTERVAL);
        update(villager);
    }

    public static void update(Villager villager) {
        if (!(villager.level() instanceof ServerLevel level) || !villager.isAlive()) return;
        var home = villager.getBrain().getMemory(MemoryModuleType.HOME).orElse(null);
        if (home == null || !home.dimension().equals(level.dimension()) || !level.hasChunkAt(home.pos())) return;
        var bed = home.pos();
        var bedState = level.getBlockState(bed);
        if (!(bedState.getBlock() instanceof BedBlock)) return;
        if (bedState.getValue(BedBlock.PART) == BedPart.FOOT) bed = bed.relative(bedState.getValue(BedBlock.FACING));
        var data = villager.getPersistentData().getCompound(KEY);
        String dimension = level.dimension().location().toString();
        if (data.contains("bed") && (data.getLong("bed") != bed.asLong()
                || (data.contains("dimension") && !dimension.equals(data.getString("dimension"))))) {
            if (data.contains("sign")) {
                var previous = BlockPos.of(data.getLong("sign"));
                String previousDimension = data.contains("dimension") ? data.getString("dimension") : dimension;
                var previousLevel = level.getServer().getLevel(ResourceKey.create(Registries.DIMENSION,
                        new ResourceLocation(previousDimension)));
                if (previousLevel == null || !previousLevel.hasChunkAt(previous)) return;
                if (!removeResident(previousLevel, previous, villager.getUUID())) return;
            }
            data = new CompoundTag();
        }
        data.putLong("bed", bed.asLong());
        data.putString("dimension", dimension);
        villager.getPersistentData().put(KEY, data);
        UUID village = boughtVillage(level, bed);
        if (village == null) return;
        if (data.getBoolean("placed")) {
            if (data.contains("skipped")) {
                var search = findEntrance(level, bed);
                logSearch(villager, bed, search, "Previously skipped; diagnostic only, no retry");
                return;
            }
            if (!repairDoorSign(level, data, villager)) {
                if (data.contains("sign")) {
                    var signPos = BlockPos.of(data.getLong("sign"));
                    VillagerManager.LOGGER.info("Household sign check for {}: villager={}, bed={}, sign={}, status={}",
                            villager.getName().getString(), villager.blockPosition(), bed, signPos,
                            !level.hasChunkAt(signPos) ? "unloaded" :
                                    level.getBlockEntity(signPos) instanceof SignBlockEntity ? "present" : "missing; removal respected");
                    refreshResident(level, signPos, villager);
                }
                return;
            }
        }

        var search = findEntrance(level, bed);
        var entrance = search.entrance();
        if (entrance == null) {
            logSearch(villager, bed, search, "No entrance");
            skipped(villager, data, "No reachable doorway with a walkable outside approach near the assigned bed");
            return;
        }
        for (var pos : signPositions(level, entrance)) {
            if (!level.hasChunkAt(pos) || !village.equals(boughtVillage(level, pos))) continue;
            if (level.getBlockEntity(pos) instanceof SignBlockEntity sign) {
                var info = sign.getPersistentData().getCompound(KEY);
                if (!info.getString("village").equals(village.toString())
                        || !info.contains("door") || info.getLong("door") != entrance.door().asLong()
                        || !unchanged(sign)) continue;
                var residents = info.getCompound(RESIDENTS);
                if (residents.getAllKeys().size() >= 2 && !residents.contains(villager.getUUID().toString())) continue;
                residents.put(villager.getUUID().toString(), resident(villager));
                info.put(RESIDENTS, residents);
                sign.getPersistentData().put(KEY, info);
                writeSign(level, sign, residents);
                placed(data, pos);
                logPlacement(villager, bed, entrance, pos);
                return;
            }
        }
        for (var pos : signPositions(level, entrance)) {
            if (!level.hasChunkAt(pos) || !village.equals(boughtVillage(level, pos))
                    || !level.getBlockState(pos).isAir()) continue;
            var state = Blocks.OAK_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING, entrance.outside());
            if (!wallSupport(level, pos, entrance.outside()) || !state.canSurvive(level, pos)) continue;
            if (!level.setBlock(pos, state, 3)) {
                VillagerManager.LOGGER.error("Could not place household sign at {}", pos);
                continue;
            }
            if (!(level.getBlockEntity(pos) instanceof SignBlockEntity sign)) {
                throw new IllegalStateException("Household sign has no sign block entity at " + pos);
            }
            var info = new CompoundTag();
            var residents = new CompoundTag();
            residents.put(villager.getUUID().toString(), resident(villager));
            info.put(RESIDENTS, residents);
            info.putString("village", village.toString());
            info.putLong("door", entrance.door().asLong());
            info.putString("facing", entrance.outside().getName());
            sign.getPersistentData().put(KEY, info);
            writeSign(level, sign, residents);
            placed(data, pos);
            logPlacement(villager, bed, entrance, pos);
            return;
        }
        logSearch(villager, bed, search, "No sign space; candidates=" + signPositions(level, entrance).stream()
                .map(pos -> !level.hasChunkAt(pos)
                        || !level.hasChunkAt(pos.relative(entrance.outside().getOpposite())) ? pos + ": unloaded" : pos + ": block=" + level.getBlockState(pos)
                        + ", support=" + level.getBlockState(pos.relative(entrance.outside().getOpposite()))
                        + ", sameVillage=" + village.equals(boughtVillage(level, pos))).toList());
        skipped(villager, data, "No free supported sign space beside the home's outside doorway");
    }

    private static UUID boughtVillage(ServerLevel level, BlockPos pos) {
        var chunk = ModEvents.getChunkDataDatabase(level).getData(new ChunkPos(pos).toLong());
        if (chunk == null || chunk.getVillageId() == null) return null;
        var village = ModEvents.getVillageDatabase(level).getData(chunk.getVillageId());
        return village != null && village.isBought() ? village.getUUID() : null;
    }

    private static boolean repairDoorSign(ServerLevel level, CompoundTag data, Villager villager) {
        if (!data.contains("sign") || data.getBoolean("door_repair_done")) return false;
        var pos = BlockPos.of(data.getLong("sign"));
        if (!level.hasChunkAt(pos) || !(level.getBlockEntity(pos) instanceof SignBlockEntity sign)
                || !unchanged(sign)) return false;
        var facing = sign.getBlockState().getValue(WallSignBlock.FACING);
        var support = pos.relative(facing.getOpposite());
        if (!level.hasChunkAt(support) || !level.hasChunkAt(support.below())) return false;
        if (!(level.getBlockState(support).getBlock() instanceof DoorBlock)
                && !(level.getBlockState(support.below()).getBlock() instanceof DoorBlock)) return false;
        var residents = sign.getPersistentData().getCompound(KEY).getCompound(RESIDENTS);
        if (!residents.contains(villager.getUUID().toString())
                || !removeResident(level, pos, villager.getUUID())) return false;
        data.putBoolean("door_repair_done", true);
        data.putBoolean("placed", false);
        data.remove("sign");
        return true;
    }

    private static void skipped(Villager villager, CompoundTag data, String reason) {
        data.putBoolean("placed", true);
        data.putString("skipped", reason);
        data.remove("pending");
        VillagerManager.LOGGER.info("Household sign skipped permanently for {}: villager={}, bed={}, reason={}",
                villager.getName().getString(), villager.blockPosition(), BlockPos.of(data.getLong("bed")), reason);
    }

    private static void placed(CompoundTag data, BlockPos pos) {
        data.putBoolean("placed", true);
        data.putLong("sign", pos.asLong());
        data.remove("skipped");
        data.remove("pending");
    }

    private static CompoundTag resident(Villager villager) {
        var result = new CompoundTag();
        result.putString("name", villager.getName().getString());
        String profession = BuiltInRegistries.VILLAGER_PROFESSION.getKey(villager.getVillagerData().getProfession()).getPath();
        String label = villager.isBaby() ? "Child" : profession.equals("none") ? "Unemployed" : title(profession);
        result.putString("profession", label);
        return result;
    }

    private static String title(String value) {
        String words = value.replace('_', ' ');
        return words.isEmpty() ? words : Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }

    private static Component[] messages(CompoundTag residents) {
        Component[] result = {Component.empty(), Component.empty(), Component.empty(), Component.empty()};
        var ids = residents.getAllKeys().stream().sorted().toList();
        for (int i = 0; i < Math.min(2, ids.size()); i++) {
            var entry = residents.getCompound(ids.get(i));
            result[i * 2] = Component.literal(entry.getString("name"));
            result[i * 2 + 1] = Component.literal(entry.getString("profession"));
        }
        return result;
    }

    private static boolean unchanged(SignBlockEntity sign) {
        var info = sign.getPersistentData().getCompound(KEY);
        if (!info.contains(RESIDENTS) || info.getBoolean("edited")) return false;
        boolean same = sign.getBlockState().is(Blocks.OAK_WALL_SIGN)
                && sign.getBlockState().getValue(WallSignBlock.FACING).getName().equals(info.getString("facing"))
                && !sign.isWaxed();
        var expected = messages(info.getCompound(RESIDENTS));
        for (boolean front : new boolean[]{true, false}) {
            SignText text = sign.getText(front);
            same &= text.getColor() == DyeColor.BLACK && !text.hasGlowingText();
            for (int i = 0; i < 4; i++) {
                var message = front ? expected[i] : Component.empty();
                same &= text.getMessage(i, false).equals(message) && text.getMessage(i, true).equals(message);
            }
        }
        if (!same) {
            info.putBoolean("edited", true);
            sign.getPersistentData().put(KEY, info);
            sign.setChanged();
        }
        return same;
    }

    private static void writeSign(ServerLevel level, SignBlockEntity sign, CompoundTag residents) {
        var text = messages(residents);
        sign.setText(new SignText(text, text, DyeColor.BLACK, false), true);
        sign.setChanged();
        var state = level.getBlockState(sign.getBlockPos());
        level.sendBlockUpdated(sign.getBlockPos(), state, state, 3);
    }

    private static void refreshResident(ServerLevel level, BlockPos pos, Villager villager) {
        if (!level.hasChunkAt(pos) || !(level.getBlockEntity(pos) instanceof SignBlockEntity sign)
                || !unchanged(sign)) return;
        var info = sign.getPersistentData().getCompound(KEY);
        var residents = info.getCompound(RESIDENTS);
        String id = villager.getUUID().toString();
        if (!residents.contains(id)) return;
        var current = resident(villager);
        if (current.equals(residents.getCompound(id))) return;
        residents.put(id, current);
        info.put(RESIDENTS, residents);
        sign.getPersistentData().put(KEY, info);
        writeSign(level, sign, residents);
    }

    private static boolean removeResident(ServerLevel level, BlockPos pos, UUID id) {
        if (!(level.getBlockEntity(pos) instanceof SignBlockEntity sign) || !unchanged(sign)) return true;
        var info = sign.getPersistentData().getCompound(KEY);
        var residents = info.getCompound(RESIDENTS).copy();
        if (!residents.contains(id.toString())) return true;
        residents.remove(id.toString());
        if (residents.isEmpty()) {
            if (!level.removeBlock(pos, false)) {
                VillagerManager.LOGGER.error("Could not remove vacated household sign at {}", pos);
                return false;
            }
        } else {
            info.put(RESIDENTS, residents);
            sign.getPersistentData().put(KEY, info);
            writeSign(level, sign, residents);
        }
        return true;
    }

    private record Entrance(BlockPos door, Direction outside, int distance) {}
    private record Step(BlockPos pos, int distance) {}
    private record EntranceSearch(Entrance entrance, int seeds, int visited, int doors,
                                  int blockedApproaches, int coveredApproaches, int unloaded,
                                  int heightLimited, boolean budgetLimited, List<String> rejectedDoors) {}

    private static EntranceSearch findEntrance(ServerLevel level, BlockPos bed) {
        var queue = new ArrayDeque<Step>();
        var seen = new HashSet<BlockPos>();
        var entrances = new ArrayList<Entrance>();
        var skyChecks = new HashMap<Approach, Boolean>();
        var rejectedDoors = new ArrayList<String>();
        int doors = 0, blocked = 0, covered = 0, unloaded = 0, heightLimited = 0;
        for (var direction : HORIZONTAL) {
            for (int dy : new int[]{0, 1, -1}) {
                var pos = bed.relative(direction).offset(0, dy, 0);
                if (walkable(level, pos) && seen.add(pos)) {
                    queue.add(new Step(pos, 1));
                }
            }
        }
        int seeds = queue.size();
        while (!queue.isEmpty() && seen.size() <= MAX_VISITS) {
            var step = queue.removeFirst();
            for (var direction : HORIZONTAL) {
                for (int dy : new int[]{0, 1, -1}) {
                    var pos = step.pos().relative(direction).offset(0, dy, 0);
                    if (Math.abs(pos.getX() - bed.getX()) > SEARCH_RADIUS
                            || Math.abs(pos.getZ() - bed.getZ()) > SEARCH_RADIUS) continue;
                    if (Math.abs(pos.getY() - bed.getY()) > SEARCH_HEIGHT) {
                        heightLimited++;
                        continue;
                    }
                    if (!level.hasChunkAt(pos)) {
                        unloaded++;
                        continue;
                    }
                    var state = level.getBlockState(pos);
                    if (state.getBlock() instanceof DoorBlock doorBlock
                            && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER) {
                        doors++;
                        if (!doorBlock.type().canOpenByHand() || !level.hasChunkAt(pos.above())
                                || !level.getBlockState(pos.above()).is(state.getBlock())
                                || level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty()) continue;
                        var axis = state.getValue(DoorBlock.FACING);
                        if (direction.getAxis() != axis.getAxis()) continue;
                        var outside = pos.relative(direction);
                        if (!walkable(level, outside)) {
                            blocked++;
                            if (rejectedDoors.size() < 4) rejectedDoors.add(pos + ": door=" + state
                                    + ", outside=" + outside + ", blocks=" + approachBlocks(level, outside));
                            continue;
                        }
                        boolean sky = skyChecks.computeIfAbsent(new Approach(pos.immutable(), direction),
                                approach -> reachesSky(level, outside, approach.door()));
                        if (sky) {
                            entrances.add(new Entrance(pos.immutable(), direction, step.distance() + 1));
                        } else {
                            covered++;
                            if (rejectedDoors.size() < 4) rejectedDoors.add(pos + ": door=" + state
                                    + ", no loaded walkable route to sky from " + outside);
                        }
                        // Interior doors can lead to another room; do not enter the exterior.
                        if (!sky && seen.add(outside)) {
                            queue.addLast(new Step(outside, step.distance() + 2));
                        }
                        continue;
                    }
                    if (walkable(level, pos) && seen.add(pos)) queue.addLast(new Step(pos, step.distance() + 1));
                }
            }
        }
        var entrance = entrances.stream().min(Comparator.comparingInt(Entrance::distance)
                .thenComparingLong(entry -> entry.door().asLong())).orElse(null);
        return new EntranceSearch(entrance, seeds, seen.size(), doors, blocked, covered, unloaded,
                heightLimited, !queue.isEmpty(), List.copyOf(rejectedDoors));
    }

    private static String approachBlocks(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) return "unloaded";
        return "feet=" + level.getBlockState(pos) + ", head=" + level.getBlockState(pos.above())
                + ", floor=" + level.getBlockState(pos.below());
    }

    private static void logSearch(Villager villager, BlockPos bed, EntranceSearch search, String status) {
        var level = (ServerLevel) villager.level();
        var neighbors = new ArrayList<String>();
        for (var direction : HORIZONTAL) {
            var pos = bed.relative(direction);
            neighbors.add(direction + ": " + approachBlocks(level, pos));
        }
        VillagerManager.LOGGER.info("Household entrance diagnostic for {}: villager={}, bed={}, status={}, search={}, bedNeighbors={}",
                villager.getName().getString(), villager.blockPosition(), bed, status, search, neighbors);
    }

    private static void logPlacement(Villager villager, BlockPos bed, Entrance entrance, BlockPos sign) {
        VillagerManager.LOGGER.info("Household sign placed/shared for {}: bed={}, door={}, outside={}, sign={}, pathDistance={}",
                villager.getName().getString(), bed, entrance.door(), entrance.outside(), sign, entrance.distance());
    }

    private record Approach(BlockPos door, Direction outside) {}

    private static boolean reachesSky(ServerLevel level, BlockPos start, BlockPos door) {
        var queue = new ArrayDeque<BlockPos>();
        var seen = new HashSet<BlockPos>();
        queue.add(start);
        seen.add(start);
        while (!queue.isEmpty() && seen.size() <= 256) {
            var pos = queue.removeFirst();
            // Skylight updates lag behind building edits; the heightmap reflects roofs immediately.
            if (level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ()) <= pos.getY() + 1) return true;
            for (var direction : HORIZONTAL) {
                for (int dy : new int[]{0, 1, -1}) {
                    var next = pos.relative(direction).offset(0, dy, 0);
                    if (next.equals(door) || Math.abs(next.getX() - start.getX()) > 8
                            || Math.abs(next.getZ() - start.getZ()) > 8
                            || Math.abs(next.getY() - start.getY()) > 2 || !walkable(level, next)
                            || !seen.add(next)) continue;
                    queue.addLast(next);
                }
            }
        }
        return false;
    }

    private static boolean walkable(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos) || !level.hasChunkAt(pos.above()) || !level.hasChunkAt(pos.below())) return false;
        var feet = level.getBlockState(pos);
        var head = level.getBlockState(pos.above());
        var floor = level.getBlockState(pos.below());
        var floorShape = floor.getCollisionShape(level, pos.below());
        return feet.getFluidState().isEmpty() && head.getFluidState().isEmpty()
                && floor.getFluidState().isEmpty()
                && feet.getCollisionShape(level, pos).isEmpty()
                && head.getCollisionShape(level, pos.above()).isEmpty()
                && !floorShape.isEmpty() && floorShape.max(Direction.Axis.Y) <= 1.0;
    }

    private static boolean wallSupport(ServerLevel level, BlockPos pos, Direction facing) {
        var support = pos.relative(facing.getOpposite());
        if (!level.hasChunkAt(support)) return false;
        var state = level.getBlockState(support);
        return !(state.getBlock() instanceof DoorBlock) && !(state.getBlock() instanceof TrapDoorBlock)
                && state.isFaceSturdy(level, support, facing);
    }

    private static List<BlockPos> signPositions(ServerLevel level, Entrance entrance) {
        var result = new ArrayList<BlockPos>();
        // Right from the perspective of someone outside looking toward the door.
        var side = entrance.outside().getCounterClockWise();
        var edge = entrance.door();
        for (int i = 0; i < 3; i++) {
            var next = edge.relative(side);
            if (!level.hasChunkAt(next)) return result;
            var state = level.getBlockState(next);
            if (!(state.getBlock() instanceof DoorBlock)
                    || state.getValue(DoorBlock.FACING).getAxis() != entrance.outside().getAxis()) break;
            edge = next;
        }
        for (int offset : new int[]{1, 2, 3}) {
            var pos = edge.relative(entrance.outside()).relative(side, offset);
            result.add(pos.above());
            result.add(pos);
        }
        return result;
    }
}
