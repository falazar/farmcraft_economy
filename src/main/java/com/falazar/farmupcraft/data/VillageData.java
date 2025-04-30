package com.falazar.farmupcraft.data;

import com.falazar.farmupcraft.util.CodecUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.level.ChunkPos;

import java.util.*;

public class VillageData {
    public static final Codec<VillageData> CODEC = RecordCodecBuilder.create(instance ->
                    instance.group(
                            UUIDUtil.STRING_CODEC.fieldOf("uuid").forGetter(VillageData::getUUID),
                            Codec.STRING.fieldOf("name").forGetter(VillageData::getName),
                            CodecUtils.CHUNK_POS_CODEC.fieldOf("position").forGetter(VillageData::getPosition),
                            Codec.INT.fieldOf("level").forGetter(VillageData::getLevel),
                            CodecUtils.CHUNK_POS_CODEC.listOf().fieldOf("claimed_chunks").forGetter(VillageData::getClaimedChunks),
                            Codec.BOOL.fieldOf("bought").forGetter(VillageData::isBought),
                            Codec.INT.optionalFieldOf("coins", 0).forGetter(VillageData::getCoins) // New field with default value
                    ).apply(instance, VillageData::new)
    );

    private final UUID uuid;
    private String name;
    private final ChunkPos position;
    private int level;
    private List<ChunkPos> claimedChunks;
    private Set<Long> claimedChunkSet = new HashSet<>();
    private final boolean bought;  // TODO what is this one? remove?
    // TODO add coins to see if we have any or are in debt.
    private int coins;

    // TODO add coins to see if we have any or are in debt.
    // TODO add player commands to add coins and subtract

    /**
     * Constructs a new VillageData object.
     * @param uuid     unique id of village
     * @param name     the mame of village
     * @param level    the level of village
     * @param position the 3d position of village
     */
    public VillageData(UUID uuid, String name, ChunkPos position, int level, List<ChunkPos> claimedChunks, boolean bought, int coins) {
        this.uuid = uuid;
        this.name = name;
        this.position = position;
        this.level = level;
        this.claimedChunks = new ArrayList<>(claimedChunks); // Convert to mutable list
        this.bought = bought;
        for (ChunkPos pos : claimedChunks) {
            claimedChunkSet.add(ChunkPos.asLong(pos.x, pos.z));
        }
        this.coins = coins;
    }

    /**
     * Gets the village's unique id.
     * @return the village's unique id
     */
    public UUID getUUID() {
        return uuid;
    }

    /**
     * Gets the village's name.
     * @return the village's name
     */
    public String getName() {
        return name;
    }

    // Set village name
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Gets the village's position.
     * @return the village's position
     */
    public ChunkPos getPosition() {
        return position;
    }

    /**
     * Gets the village's level.
     * @return the village's level
     */
    public int getLevel() {
        return level;
    }

    // Set level
    public void setLevel(int level) {
        this.level = level;
    }

    public List<ChunkPos> getClaimedChunks() {
        return claimedChunks;
    }

    public Set<Long> getClaimedChunkSet() {
        return claimedChunkSet;
    }

    public void addClaimedChunk(ChunkPos chunkPos) {
        claimedChunks.add(chunkPos);
        claimedChunkSet.add(ChunkPos.asLong(chunkPos.x, chunkPos.z));
    }

    public boolean isBought() {
        return bought;
    }

    @Override
    public String toString() {
        return "Village ID: " + uuid + ", Owner: " + name + ", Chunks: " + claimedChunks;
    }

    public int getCoins() {
        return coins;
    }

    public void setCoins(int coins) {
        this.coins = coins;
    }

    public void addCoins(int coins) {
        if (coins < 0) {
            throw new IllegalArgumentException("Cannot add negative coins");
        }
        this.coins += coins;
    }

    public void subtractCoins(int coins) {
        if (coins < 0) {
            throw new IllegalArgumentException("Cannot subtract negative coins");
        }
        this.coins -= coins;
    }

    public boolean hasEnoughCoins(int coins) {
        return this.coins >= coins;
    }
}