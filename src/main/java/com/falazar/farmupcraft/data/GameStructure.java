package com.falazar.farmupcraft.data;

import net.minecraft.core.BlockPos;

public class GameStructure {
    private final String id;
    private final String name;
    private final BlockPos position;
    private final String type;
    private final boolean onClaimedPlot;

    public GameStructure(String id, String name, BlockPos position, String type, boolean onClaimedPlot) {
        this.id = id;
        this.name = name;
        this.position = position;
        this.type = type;
        this.onClaimedPlot = onClaimedPlot;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public BlockPos getPosition() { return position; }
    public String getType() { return type; }
    public boolean isOnClaimedPlot() { return onClaimedPlot; }
}