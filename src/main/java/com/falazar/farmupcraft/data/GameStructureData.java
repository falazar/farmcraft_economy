package com.falazar.farmupcraft.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;

public class GameStructureData {
    public static final Codec<BlockPos> BLOCK_POS_CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.INT.fieldOf("x").forGetter(BlockPos::getX),
                    Codec.INT.fieldOf("y").forGetter(BlockPos::getY),
                    Codec.INT.fieldOf("z").forGetter(BlockPos::getZ)
            ).apply(instance, BlockPos::new)
    );

    public static final Codec<GameStructureData> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.LONG.fieldOf("id").forGetter(GameStructureData::getId),
                    Codec.STRING.fieldOf("name").forGetter(GameStructureData::getName),
                    BLOCK_POS_CODEC.fieldOf("center_pos").forGetter(GameStructureData::getCenterPos),
                    Codec.STRING.fieldOf("type").forGetter(GameStructureData::getType),
                    Codec.BOOL.optionalFieldOf("on_claimed_plot", false).forGetter(GameStructureData::isOnClaimedPlot),
                    Codec.BOOL.optionalFieldOf("was_visited", false).forGetter(GameStructureData::wasVisited)
            ).apply(instance, GameStructureData::new)
    );

    private final Long id;
    private String name;
    private final BlockPos centerPos;
    private final String type;
    private boolean onClaimedPlot;
    private boolean wasVisited;

    /**
     * Constructs a new GameStructureData object with default values (not claimed, not visited).
     * @param id the unique identifier of the structure (Long ID from Minecraft)
     * @param name the display name of the structure
     * @param centerPos the center position of the structure
     * @param type the type/category of the structure
     */
    public GameStructureData(Long id, String name, BlockPos centerPos, String type) {
        this(id, name, centerPos, type, false, false);
    }

    /**
     * Constructs a new GameStructureData object.
     * @param id the unique identifier of the structure (Long ID from Minecraft)
     * @param name the display name of the structure
     * @param centerPos the center position of the structure
     * @param type the type/category of the structure
     * @param onClaimedPlot whether the structure is on a claimed plot
     * @param wasVisited whether a player has visited this structure
     */
    public GameStructureData(Long id, String name, BlockPos centerPos, String type, boolean onClaimedPlot, boolean wasVisited) {
        this.id = id;
        this.name = name;
        this.centerPos = centerPos;
        this.type = type;
        this.onClaimedPlot = onClaimedPlot;
        this.wasVisited = wasVisited;
    }

    /**
     * Gets the structure's unique identifier.
     * @return the structure's unique identifier (Long ID from Minecraft)
     */
    public Long getId() {
        return id;
    }

    /**
     * Gets the structure's display name.
     * @return the structure's display name
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the structure's display name.
     * @param name the new display name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Gets the structure's center position.
     * @return the structure's center position
     */
    public BlockPos getCenterPos() {
        return centerPos;
    }

    /**
     * Gets the structure's type/category.
     * @return the structure's type
     */
    public String getType() {
        return type;
    }

    /**
     * Checks if the structure is on a claimed plot.
     * @return true if on a claimed plot, false otherwise
     */
    public boolean isOnClaimedPlot() {
        return onClaimedPlot;
    }

    /**
     * Sets whether the structure is on a claimed plot.
     * @param onClaimedPlot the new claimed plot status
     */
    public void setOnClaimedPlot(boolean onClaimedPlot) {
        this.onClaimedPlot = onClaimedPlot;
    }

    /**
     * Checks if a player has visited this structure.
     * @return true if visited, false otherwise
     */
    public boolean wasVisited() {
        return wasVisited;
    }

    /**
     * Sets the visited status of this structure.
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

    @Override
    public String toString() {
        return "GameStructure ID: " + id + ", Name: " + name + ", Position: " + centerPos + ", Type: " + type + ", Claimed: " + onClaimedPlot + ", Visited: " + wasVisited;
    }
} 