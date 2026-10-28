package com.falazar.farmupcraft.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.List;

public class GameStructureData {
    public static final Codec<BlockPos> BLOCK_POS_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("x").forGetter(BlockPos::getX),
            Codec.INT.fieldOf("y").forGetter(BlockPos::getY),
            Codec.INT.fieldOf("z").forGetter(BlockPos::getZ)).apply(instance, BlockPos::new));

    public static final Codec<GameStructureData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.fieldOf("id").forGetter(GameStructureData::getId),
            Codec.STRING.fieldOf("name").forGetter(GameStructureData::getName),
            BLOCK_POS_CODEC.fieldOf("center_pos").forGetter(GameStructureData::getCenterPos),
            Codec.STRING.fieldOf("type").forGetter(GameStructureData::getType),
            Codec.BOOL.optionalFieldOf("on_claimed_plot", false).forGetter(GameStructureData::isOnClaimedPlot),
            Codec.BOOL.optionalFieldOf("was_visited", false).forGetter(GameStructureData::wasVisited),
            BLOCK_POS_CODEC.optionalFieldOf("min_pos", BlockPos.ZERO).forGetter(GameStructureData::getMinPos),
            BLOCK_POS_CODEC.optionalFieldOf("max_pos", BlockPos.ZERO).forGetter(GameStructureData::getMaxPos),
            Codec.LONG.listOf().optionalFieldOf("chunk_positions", new ArrayList<>())
                    .forGetter(GameStructureData::getChunkPositions),
            BLOCK_POS_CODEC.optionalFieldOf("special_chest_pos", BlockPos.ZERO)
                    .forGetter(GameStructureData::getSpecialChestPos),
            Codec.BOOL.optionalFieldOf("special_chest_opened", false)
                    .forGetter(GameStructureData::isSpecialChestOpened),
            Codec.INT.optionalFieldOf("total_chest_count", 0)
                    .forGetter(GameStructureData::getTotalChestCount),
            BLOCK_POS_CODEC.listOf().optionalFieldOf("opened_chest_positions", new ArrayList<>())
                    .forGetter(GameStructureData::getOpenedChestPositions))
            .apply(instance, GameStructureData::new));

    private final Long id;
    private String name;
    private final BlockPos centerPos;
    private final String type;
    private boolean onClaimedPlot;
    private boolean wasVisited;
    private BlockPos minPos;
    private BlockPos maxPos;
    private List<Long> chunkPositions;
    private BlockPos specialChestPos;
    private boolean specialChestOpened;
    private int totalChestCount;
    private List<BlockPos> openedChestPositions;

    /**
     * Constructs a new GameStructureData object with default values (not claimed,
     * not visited).
     * 
     * @param id        the unique identifier of the structure (Long ID from
     *                  Minecraft)
     * @param name      the display name of the structure
     * @param centerPos the center position of the structure
     * @param type      the type/category of the structure
     */
    public GameStructureData(Long id, String name, BlockPos centerPos, String type) {
        this(id, name, centerPos, type, false, false, BlockPos.ZERO, BlockPos.ZERO, new ArrayList<>());
    }

    public GameStructureData(Long id, String name, BlockPos centerPos, String type, boolean onClaimedPlot,
            boolean wasVisited) {
        this(id, name, centerPos, type, onClaimedPlot, wasVisited, BlockPos.ZERO, BlockPos.ZERO, new ArrayList<>());
    }

    public GameStructureData(Long id, String name, BlockPos centerPos, String type, boolean onClaimedPlot,
            boolean wasVisited, BlockPos minPos, BlockPos maxPos) {
        this(id, name, centerPos, type, onClaimedPlot, wasVisited, minPos, maxPos, new ArrayList<>());
    }

    public GameStructureData(Long id, String name, BlockPos centerPos, String type, boolean onClaimedPlot,
            boolean wasVisited, BlockPos minPos, BlockPos maxPos, List<Long> chunkPositions) {
        this(id, name, centerPos, type, onClaimedPlot, wasVisited, minPos, maxPos, chunkPositions,
                BlockPos.ZERO, false);
    }

    public GameStructureData(Long id, String name, BlockPos centerPos, String type, boolean onClaimedPlot,
            boolean wasVisited, BlockPos minPos, BlockPos maxPos, List<Long> chunkPositions,
            BlockPos specialChestPos, boolean specialChestOpened) {
        this(id, name, centerPos, type, onClaimedPlot, wasVisited, minPos, maxPos, chunkPositions,
                specialChestPos, specialChestOpened, 0, new ArrayList<>());
    }

    public GameStructureData(Long id, String name, BlockPos centerPos, String type, boolean onClaimedPlot,
            boolean wasVisited, BlockPos minPos, BlockPos maxPos, List<Long> chunkPositions,
            BlockPos specialChestPos, boolean specialChestOpened, int totalChestCount,
            List<BlockPos> openedChestPositions) {
        this.id = id;
        this.name = name;
        this.centerPos = centerPos;
        this.type = type;
        this.onClaimedPlot = onClaimedPlot;
        this.wasVisited = wasVisited;
        this.minPos = minPos;
        this.maxPos = maxPos;
        this.chunkPositions = chunkPositions != null ? new ArrayList<>(chunkPositions) : new ArrayList<>();
        this.specialChestPos = specialChestPos != null ? specialChestPos : BlockPos.ZERO;
        this.specialChestOpened = specialChestOpened;
        this.totalChestCount = totalChestCount;
        this.openedChestPositions = openedChestPositions != null ? new ArrayList<>(openedChestPositions) : new ArrayList<>();
    }

    /**
     * Gets the structure's unique identifier.
     * 
     * @return the structure's unique identifier (Long ID from Minecraft)
     */
    public Long getId() {
        return id;
    }

    /**
     * Gets the structure's display name.
     * 
     * @return the structure's display name
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the structure's display name.
     * 
     * @param name the new display name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Gets the structure's center position.
     * 
     * @return the structure's center position
     */
    public BlockPos getCenterPos() {
        return centerPos;
    }

    /**
     * Gets the structure's type/category.
     * 
     * @return the structure's type
     */
    public String getType() {
        return type;
    }

    /**
     * Checks if the structure is on a claimed plot.
     * 
     * @return true if on a claimed plot, false otherwise
     */
    public boolean isOnClaimedPlot() {
        return onClaimedPlot;
    }

    /**
     * Sets whether the structure is on a claimed plot.
     * 
     * @param onClaimedPlot the new claimed plot status
     */
    public void setOnClaimedPlot(boolean onClaimedPlot) {
        this.onClaimedPlot = onClaimedPlot;
    }

    /**
     * Checks if a player has visited this structure.
     * 
     * @return true if visited, false otherwise
     */
    public boolean wasVisited() {
        return wasVisited;
    }

    /**
     * Sets the visited status of this structure.
     * 
     * @param wasVisited the new visited status
     */
    public void setWasVisited(boolean wasVisited) {
        this.wasVisited = wasVisited;
    }

    /**
     * Marks this structure as visited.
     */
    public void markAsVisited() {
        this.wasVisited = true;
    }

    public BlockPos getMinPos() {
        return minPos;
    }

    public BlockPos getMaxPos() {
        return maxPos;
    }

    public void setMinPos(BlockPos minPos) {
        this.minPos = minPos;
    }

    public void setMaxPos(BlockPos maxPos) {
        this.maxPos = maxPos;
    }

    public boolean hasBoundingBox() {
        return !BlockPos.ZERO.equals(minPos) || !BlockPos.ZERO.equals(maxPos);
    }

    public List<Long> getChunkPositions() {
        return chunkPositions;
    }

    public void setChunkPositions(List<Long> chunkPositions) {
        this.chunkPositions = chunkPositions != null ? new ArrayList<>(chunkPositions) : new ArrayList<>();
    }

    public boolean hasChunkPositions() {
        return chunkPositions != null && !chunkPositions.isEmpty();
    }

    public BlockPos getSpecialChestPos() {
        return specialChestPos != null ? specialChestPos : BlockPos.ZERO;
    }

    public void setSpecialChestPos(BlockPos pos) {
        this.specialChestPos = pos != null ? pos : BlockPos.ZERO;
    }

    public boolean hasSpecialChest() {
        return !BlockPos.ZERO.equals(getSpecialChestPos());
    }

    public boolean isSpecialChestOpened() {
        return specialChestOpened;
    }

    public void setSpecialChestOpened(boolean opened) {
        this.specialChestOpened = opened;
    }

    public int getTotalChestCount() {
        return totalChestCount;
    }

    public void setTotalChestCount(int count) {
        this.totalChestCount = count;
    }

    public List<BlockPos> getOpenedChestPositions() {
        return openedChestPositions != null ? openedChestPositions : new ArrayList<>();
    }

    public boolean isChestOpened(BlockPos pos) {
        if (openedChestPositions == null) return false;
        for (BlockPos p : openedChestPositions) {
            if (p.equals(pos)) return true;
        }
        return false;
    }

    public void markChestOpened(BlockPos pos) {
        if (openedChestPositions == null) openedChestPositions = new ArrayList<>();
        if (!isChestOpened(pos)) openedChestPositions.add(pos.immutable());
    }

    @Override
    public String toString() {
        return "GameStructure ID: " + id + ", Name: " + name + ", Position: " + centerPos + ", Type: " + type
                + ", Claimed: " + onClaimedPlot + ", Visited: " + wasVisited;
    }
}