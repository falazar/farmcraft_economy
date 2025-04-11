package com.falazar.farmupcraft.data;

import com.falazar.farmupcraft.currency.CurrencyCost;
import com.falazar.farmupcraft.util.CodecUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class VillageData {

    public static final Codec<VillageData> CODEC = RecordCodecBuilder.create(instance ->
                    instance.group(
                            Codec.STRING.fieldOf("id").forGetter(VillageData::getId),
                            Codec.STRING.fieldOf("name").forGetter(VillageData::getName),
                            Codec.INT.fieldOf("level").forGetter(VillageData::getLevel),
                            CodecUtils.CHUNK_POS_CODEC.listOf().fieldOf("claimed_chunks").forGetter(VillageData::getClaimedChunks),
                            Codec.BOOL.fieldOf("bought").forGetter(VillageData::isBought)
                    ).apply(instance, VillageData::new)
    );

    private final String id;
    private final String name;
    private final int level;
    private final List<ChunkPos> claimedChunks;
    private final Set<Long> claimedChunkSet = new HashSet<>();
    private final boolean bought;
    /**
     * Constructs a new ChunkData object.
     *
     * @param id       unique id of village
     * @param name     the mame of village
     * @param coins    the coins of village
     * @param level    the level of village
     * @param position the 3d position of village
     */
    public VillageData(String id, String name, int level, List<ChunkPos> claimedChunks, boolean bought) {
        this.id = id;
        this.name = name;
        this.level = level;
        this.claimedChunks = claimedChunks;
        this.bought = bought;
        for (ChunkPos pos : claimedChunks) {
            claimedChunkSet.add(ChunkPos.asLong(pos.x, pos.z));
        }
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
     * Gets the village's level.
     * @return the village's level
     */
    public int getLevel() {
        return level;
    }

    public List<ChunkPos> getClaimedChunks() {
        return claimedChunks;
    }

    public Set<Long> getClaimedChunkSet() {
        return claimedChunkSet;
    }

    public boolean isBought() {
        return bought;
    }

    @Override
    public String toString() {
        return "Village ID: " + id + ", Owner: " + name + ", Chunks: " + claimedChunks;
    }

}