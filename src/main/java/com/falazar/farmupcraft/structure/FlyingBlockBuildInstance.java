package com.falazar.farmupcraft.structure;

import com.falazar.farmupcraft.entity.FlyingBlockChunkEntity;
import com.falazar.farmupcraft.entity.curves.MotionCurve;
import com.falazar.farmupcraft.entity.curves.*;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;
import java.util.function.Supplier;

public class FlyingBlockBuildInstance {

    public enum BuildStyle {
        STRAIGHT,
        CHUNKY,
        RANDOM,
        BOTTOM_UP
    }

    private final BuildableStructure structure;
    private final Supplier<Vec3> launchOriginSupplier;
    private final BlockPos targetOrigin;
    private final Rotation rotation;

    private List<Pair<BlockPos, BlockState>> buildQueue;
    private Map<Integer, List<Pair<BlockPos, BlockState>>> chunkGroups = new HashMap<>();
    private Map<Integer, Vec3> chunkLaunchOrigins = new HashMap<>();
    private List<ChunkGroup> chunkGroupsList = new ArrayList<>();
    private final Map<BlockPos, BlockPos> relativeOffsetMap = new HashMap<>();

    private int tickCounter = 0;
    private final int delayTicks = 5;
    private int progress = 0;
    private final Map<BlockPos, BlockState> blockStateMap = new HashMap<>();

    private BuildStyle currentStyle = BuildStyle.STRAIGHT;
    private final Vec3i CHUNK_SIZE = new Vec3i(10, 10, 10); // width, height, depth


    public FlyingBlockBuildInstance(BuildableStructure structure, BlockPos targetOrigin, Rotation rotation, Supplier<Vec3> launchOriginSupplier) {
        this.structure = structure;
        this.targetOrigin = targetOrigin;
        this.rotation = rotation;
        this.launchOriginSupplier = launchOriginSupplier;
        this.buildQueue = createBuildQueue(currentStyle);
    }

    public void tick(ServerLevel level, int blocksPerTick) {
        tick(level, blocksPerTick, BuildStyle.STRAIGHT);
    }

    public void tick(ServerLevel level, int blocksPerTick, BuildStyle style) {
        if (style != currentStyle) {
            this.buildQueue = createBuildQueue(style);
            this.progress = 0;
            this.currentStyle = style;
        }

        tickCounter++;
        if (tickCounter < delayTicks) return;
        tickCounter = 0;

        RandomSource random = level.getRandom();
        int launched = 0;


        if (style == BuildStyle.CHUNKY && progress < chunkGroupsList.size()) {
            ChunkGroup chunkGroup = chunkGroupsList.get(progress);

            Vec3 chunkCenter = chunkGroup.center;

            for (BlockPos pos : chunkGroup.blocks) {


                BlockState state = blockStateMap.get(pos);
                if (state == null) continue;




                // This is the block's position relative to the chunk center
                Vec3 relativeOffset = Vec3.atBottomCenterOf(pos).subtract(chunkCenter);

                Vec3 start = launchOriginSupplier.get().add(relativeOffset);
                Vec3 end = Vec3.atBottomCenterOf(pos);



                Vec3 up = start.add(0, 7 , 0);
                Vec3 down = end.add(0, 7 , 0);

                SerializableMotionCurve curve = new ChainedMotionCurve()
                        .addSegment(new EasedLinearMotionCurve(start, up, Easing.SINE_IN), 0.2)
                        .addSegment(new EasedMotionCurve(new VerticalHopCurve(up, down, 3), Easing.CUBIC_OUT), 0.6)
                        .addSegment(new EasedLinearMotionCurve(down, end, Easing.SINE_OUT), 0.2);

                FlyingBlockChunkEntity entity = new FlyingBlockChunkEntity(
                        level,
                        curve,
                        state,
                        30,
                        true
                );
                entity.setShowTrail(true);
                level.addFreshEntity(entity);

            }

            progress++;

            return;
        }



        while (progress < buildQueue.size() && launched < blocksPerTick) {
            Pair<BlockPos, BlockState> pair = buildQueue.get(progress);
            BlockPos target = pair.getLeft();
            BlockState state = pair.getRight();
            Vec3 end = Vec3.atBottomCenterOf(target);

            Vec3 start = switch (style) {
                case RANDOM -> {
                    Vec3 base = launchOriginSupplier.get();
                    yield base.add(
                            (random.nextDouble() - 0.5) * 10,
                            random.nextDouble() * 6,
                            (random.nextDouble() - 0.5) * 10
                    );
                }
                case BOTTOM_UP, STRAIGHT -> launchOriginSupplier.get().add(0, 10, 0); // consistently from above
                default -> launchOriginSupplier.get(); // fallback
            };

            Vec3 up = start.add(0, 7 + random.nextDouble() * 3, 0);
            Vec3 down = end.add(0, 7 + random.nextDouble() * 2, 0);

            SerializableMotionCurve curve = new ChainedMotionCurve()
                    .addSegment(new EasedLinearMotionCurve(start, up, Easing.SINE_IN), 0.2)
                    .addSegment(new EasedMotionCurve(new VerticalHopCurve(up, down, 3), Easing.CUBIC_OUT), 0.6)
                    .addSegment(new EasedLinearMotionCurve(down, end, Easing.SINE_OUT), 0.2);

            FlyingBlockChunkEntity entity = new FlyingBlockChunkEntity(level, curve, state, 30, true);
            entity.setShowTrail(true);
            level.addFreshEntity(entity);

            progress++;
            launched++;
        }
    }

    private List<Pair<BlockPos, BlockState>> createBuildQueue(BuildStyle style) {
        List<Pair<BlockPos, BlockState>> all = new ArrayList<>();
        structure.ensurePopulated(null);


        for (BuildableStructure.StructureBuildTask task : structure.blockTasks) {
            BlockPos rotatedRel = rotate(task.relativePos);  // <- rotated relative position
            BlockPos placeAt = targetOrigin.offset(rotatedRel);
            BlockState rotatedState = task.state.rotate(rotation);

            all.add(Pair.of(placeAt, rotatedState));
            blockStateMap.put(placeAt, rotatedState);
            relativeOffsetMap.put(placeAt, task.relativePos);
        }


        switch (style) {
            case CHUNKY -> {
                // Group blocks into chunks
                List<BlockPos> allPos = all.stream().map(Pair::getLeft).toList();
                List<ChunkGroup> groups = groupIntoChunks(allPos, CHUNK_SIZE);
                groups.sort(Comparator.comparingInt(group -> group.origin.getY()));

                this.chunkGroupsList.addAll(groups);
                chunkGroups.clear();
                chunkLaunchOrigins.clear();

                int index = 0;
                for (ChunkGroup group : groups) {
                    List<Pair<BlockPos, BlockState>> blockPairs = new ArrayList<>();
                    for (BlockPos blockPos : group.blocks) {
                        // Match position back to the state
                        all.stream()
                                .filter(p -> p.getLeft().equals(blockPos))
                                .findFirst()
                                .ifPresent(blockPairs::add);
                    }

                    chunkGroups.put(index, blockPairs);

                    index++;
                }

                // Flatten into build queue
                all = chunkGroups.values().stream().flatMap(List::stream).toList();
            }

            case RANDOM -> Collections.shuffle(all);
            case BOTTOM_UP -> all.sort(Comparator.comparingInt(p -> p.getLeft().getY()));
            default -> {} // STRAIGHT — no sorting
        }

        blockStateMap.clear();
        for (Pair<BlockPos, BlockState> pair : all) {
            blockStateMap.put(pair.getLeft(), pair.getRight());
        }


        return all;
    }



    private BlockPos rotate(BlockPos pos) {
        return switch (rotation) {
            case CLOCKWISE_90 -> new BlockPos(structure.structureSize.getZ() - 1 - pos.getZ(), pos.getY(), pos.getX());
            case CLOCKWISE_180 -> new BlockPos(structure.structureSize.getX() - 1 - pos.getX(), pos.getY(), structure.structureSize.getZ() - 1 - pos.getZ());
            case COUNTERCLOCKWISE_90 -> new BlockPos(pos.getZ(), pos.getY(), structure.structureSize.getX() - 1 - pos.getX());
            default -> pos;
        };
    }

    public boolean isFinished() {
        return currentStyle == BuildStyle.CHUNKY ? progress >= chunkGroupsList.size() : progress >= buildQueue.size();
    }


    public static class ChunkGroup {
        public final BlockPos origin;
        public final List<BlockPos> blocks = new ArrayList<>();
        public Vec3 center = Vec3.ZERO;

        public ChunkGroup(BlockPos origin) {
            this.origin = origin;
        }

        public void computeCenter() {
            if (blocks.isEmpty()) return;
            Vec3 sum = Vec3.ZERO;
            for (BlockPos p : blocks) sum = sum.add(Vec3.atCenterOf(p));
            this.center = sum.scale(1.0 / blocks.size());
        }
    }

    public static List<ChunkGroup> groupIntoChunks(List<BlockPos> allPositions,  Vec3i chunkSize) {
        Map<BlockPos, ChunkGroup> chunkMap = new HashMap<>();

        for (BlockPos pos : allPositions) {
            int chunkX = Math.floorDiv(pos.getX(), chunkSize.getX());
            int chunkY = Math.floorDiv(pos.getY(), chunkSize.getY());
            int chunkZ = Math.floorDiv(pos.getZ(), chunkSize.getZ());
            BlockPos chunkKey = new BlockPos(chunkX, chunkY, chunkZ);

            ChunkGroup group = chunkMap.computeIfAbsent(chunkKey, k -> new ChunkGroup(new BlockPos(
                    chunkX * chunkSize.getX(),
                    chunkY * chunkSize.getY(),
                    chunkZ * chunkSize.getZ()
            )));
            group.blocks.add(pos);
        }
        for (ChunkGroup group : chunkMap.values()) {
            group.blocks.sort(Comparator.comparingInt(BlockPos::getY)); // sort by Y
            group.computeCenter(); // compute the center for flying offsets
        }
        return new ArrayList<>(chunkMap.values());
    }


}
