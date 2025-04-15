package com.falazar.farmupcraft.data;

import com.falazar.farmupcraft.util.CodecUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

public class ChunkData {

    public static final Codec<ChunkData> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.STRING.fieldOf("type").forGetter(ChunkData::getType),
                    Codec.INT.fieldOf("player_id").forGetter(ChunkData::getPlayerId),
                    UUIDUtil.STRING_CODEC.fieldOf("village_id").forGetter(ChunkData::getVillageId)
            ).apply(instance, ChunkData::new)
    );

    private final String type;
    private final int playerId;
    private final UUID villageId;

    /**
     * Constructs a new ChunkData object.
     *
     * @param type       the type of the chunk (could represent biome, purpose, etc.)
     * @param playerId   the integer ID representing the player entity, NOT the player's UUID
     * @param villageId  the integer ID representing the village associated with this chunk
     */
    public ChunkData(String type, int playerId, UUID villageId) {
        this.type = type;
        this.playerId = playerId;
        this.villageId = villageId;
    }

    /**
     * Gets the type of the chunk.
     *
     * <p>Note: This field could potentially be refactored into an enum for better type safety.</p>
     *
     * @return the chunk type as a string
     */
    public String getType() {
        return type;
    }

    /**
     * Gets the player's integer entity ID associated with this chunk.
     *
     * <p>This is the in-game integer ID used to identify entities, not the player's UUID.</p>
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

    /**
     * Retrieves the name of the player associated with this chunk from the server.
     *
     * @param level the server level where the player is located
     * @return the player's name, or an empty string if the entity is not a player or cannot be found
     */
    public String getNameForPlayer(ServerLevel level) {
        Entity entity = level.getEntity(playerId);
        if (entity instanceof Player player) {
            return player.getGameProfile().getName();
        }
        return "";
    }
}