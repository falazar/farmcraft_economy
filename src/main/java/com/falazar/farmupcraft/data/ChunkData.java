package com.falazar.farmupcraft.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class ChunkData {
    public static final Codec<ChunkData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("type").forGetter(ChunkData::getType),
            Codec.INT.fieldOf("player_id").forGetter(ChunkData::getPlayerId),
            UUIDUtil.STRING_CODEC.fieldOf("village_id").forGetter(ChunkData::getVillageId),
            UUIDUtil.STRING_CODEC.optionalFieldOf("owner_uuid").forGetter(c -> Optional.ofNullable(c.getOwnerUUID())),
            Codec.STRING.listOf().optionalFieldOf("visitors", List.of()).forGetter(ChunkData::getVisitors),
            Codec.INT.optionalFieldOf("bought_y", 60).forGetter(ChunkData::getBoughtY))
            .apply(instance, (type, playerId, villageId, ownerUUID, visitors, boughtY) -> new ChunkData(type, playerId,
                    villageId, ownerUUID.orElse(null), visitors, boughtY)));
    // TODO remove playerId from chunkdata, not needed only village, should be
    // nullable tho
    private String type;
    private int playerId;
    private UUID villageId;
    private UUID ownerUUID; // The player UUID who owns this plot (set on plot purchase).
    private List<String> visitors; // Player names allowed to use doors/chests on this plot.
    private int boughtY; // Y level where the plot was purchased.

    /**
     * Constructs a new ChunkData object.
     *
     * @param type      the type of the chunk (could represent biome, purpose, etc.)
     * @param playerId  the integer ID representing the player entity, NOT the
     *                  player's UUID
     * @param villageId the integer ID representing the village associated with this
     *                  chunk
     */
    public ChunkData(String type, int playerId, UUID villageId) {
        this.type = type;
        this.playerId = playerId;
        this.villageId = villageId;
        this.ownerUUID = null;
        this.visitors = new ArrayList<>();
        this.boughtY = 60;
    }

    public ChunkData(String type, int playerId, UUID villageId, UUID ownerUUID) {
        this.type = type;
        this.playerId = playerId;
        this.villageId = villageId;
        this.ownerUUID = ownerUUID;
        this.visitors = new ArrayList<>();
        this.boughtY = 60;
    }

    public ChunkData(String type, int playerId, UUID villageId, UUID ownerUUID, List<String> visitors) {
        this(type, playerId, villageId, ownerUUID, visitors, 60);
    }

    public ChunkData(String type, int playerId, UUID villageId, UUID ownerUUID, List<String> visitors, int boughtY) {
        this.type = type;
        this.playerId = playerId;
        this.villageId = villageId;
        this.ownerUUID = ownerUUID;
        this.visitors = visitors != null ? new ArrayList<>(visitors) : new ArrayList<>();
        this.boughtY = boughtY;
    }

    /**
     * Gets the type of the chunk.
     *
     * <p>
     * Note: This field could potentially be refactored into an enum for better type
     * safety.
     * </p>
     *
     * @return the chunk type as a string
     */
    public String getType() {
        return type;
    }

    // Set type now.
    public void setType(String type) {
        this.type = type;
    }

    /**
     * Gets the player's integer entity ID associated with this chunk.
     *
     * <p>
     * This is the in-game integer ID used to identify entities, not the player's
     * UUID.
     * </p>
     *
     * @return the player's integer entity ID
     */
    public int getPlayerId() {
        return playerId;
    }

    /**
     * Gets the village's integer ID associated with this chunk.
     * 
     * @return the village's integer ID
     */
    public UUID getVillageId() {
        return villageId;
    }

    // setVillageId
    public void setVillageId(UUID villageId) {
        this.villageId = villageId;
    }

    public UUID getOwnerUUID() {
        return ownerUUID;
    }

    public void setOwnerUUID(UUID ownerUUID) {
        this.ownerUUID = ownerUUID;
    }

    public List<String> getVisitors() {
        return visitors != null ? visitors : new ArrayList<>();
    }

    public void addVisitor(String playerName) {
        if (visitors == null)
            visitors = new ArrayList<>();
        String normalized = playerName == null ? "" : playerName.trim().toLowerCase();
        if (!normalized.isEmpty() && !visitors.contains(normalized))
            visitors.add(normalized);
    }

    public void removeVisitor(String playerName) {
        if (visitors == null)
            return;
        String normalized = playerName == null ? "" : playerName.trim().toLowerCase();
        visitors.remove(normalized);
    }

    public boolean isVisitor(String playerName) {
        if (visitors == null)
            return false;
        String normalized = playerName == null ? "" : playerName.trim().toLowerCase();
        return visitors.contains(normalized);
    }

    public int getBoughtY() {
        return boughtY;
    }

    public void setBoughtY(int boughtY) {
        this.boughtY = boughtY;
    }

    /**
     * Retrieves the name of the player associated with this chunk from the server.
     *
     * @param level the server level where the player is located
     * @return the player's name, or an empty string if the entity is not a player
     *         or cannot be found
     */
    public String getNameForPlayer(ServerLevel level) {
        Entity entity = level.getEntity(playerId);
        if (entity instanceof Player player) {
            return player.getGameProfile().getName();
        }
        return "";
    }
}