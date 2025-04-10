package com.falazar.farmupcraft.data;

import com.falazar.farmupcraft.util.CodecUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;

import java.util.List;

public class VillageData {

    public static final Codec<VillageData> CODEC = RecordCodecBuilder.create(instance ->
                    instance.group(
                            Codec.STRING.fieldOf("id").forGetter(VillageData::getId),
                            Codec.STRING.fieldOf("name").forGetter(VillageData::getName),
                            Codec.INT.fieldOf("coins").forGetter(VillageData::getCoins),
                            Codec.INT.fieldOf("level").forGetter(VillageData::getLevel),
                            CodecUtils.CHUNK_POS_CODEC.listOf().fieldOf("claimed_chunks").forGetter(VillageData::getClaimedChunks)
                    ).apply(instance, VillageData::new)
    );

    private final String id;
    private final String name;
    private final int coins;
    private final int level;
    private final List<ChunkPos> claimedChunks;

    /**
     * Constructs a new ChunkData object.
     *
     * @param id       unique id of village
     * @param name     the mame of village
     * @param coins    the coins of village
     * @param level    the level of village
     * @param position the 3d position of village
     */
    public VillageData(String id, String name, int coins, int level, List<ChunkPos> claimedChunks) {
        this.id = id;
        this.name = name;
        this.coins = coins;
        this.level = level;
        this.claimedChunks = claimedChunks;
    }

    /**
     * Gets the village's unique id.
     * @return the village's unique id
     */
    public String getId() {
        return id;
    }

    /**
     * Gets the village's name.
     * @return the village's name
     */
    public String getName() {
        return name;
    }

    /**
     * Gets the village's coins.
     * @return the village's coins
     */
    public int getCoins() {
        return coins;
    }

    /**
     * Gets the village's level.
     * @return the village's level
     */
    public int getLevel() {
        return level;
    }

    public List<ChunkPos> getClaimedChunks() {
        return claimedChunks;
    }




    @Override
    public String toString() {
        return "Village ID: " + id + ", Owner: " + name + ", Chunks: " + claimedChunks;
    }

}