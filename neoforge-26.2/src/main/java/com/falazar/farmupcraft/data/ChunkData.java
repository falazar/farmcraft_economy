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
            Codec.INT.optionalFieldOf("bought_y", 60).forGetter(ChunkData::getBoughtY),
            Codec.INT.optionalFieldOf("plot_level", 1).forGetter(ChunkData::getPlotLevel))
            .apply(instance, (type, playerId, villageId, ownerUUID, visitors, boughtY, plotLevel) -> {
                ChunkData cd = new ChunkData(type, playerId, villageId, ownerUUID.orElse(null), visitors, boughtY);
                cd.setPlotLevel(plotLevel);
                return cd;
            }));

    private String type;
    private int playerId;
    private UUID villageId;
    private UUID ownerUUID;
    private List<String> visitors;
    private int boughtY;
    private int plotLevel = 1;

    public ChunkData(String type, int playerId, UUID villageId) {
        this(type, playerId, villageId, null);
    }

    public ChunkData(String type, int playerId, UUID villageId, UUID ownerUUID) {
        this(type, playerId, villageId, ownerUUID, null, 60);
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

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    /** The player's integer entity ID, not their UUID. */
    public int getPlayerId() {
        return playerId;
    }

    public UUID getVillageId() {
        return villageId;
    }

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

    public int getPlotLevel() {
        return plotLevel;
    }

    public void setPlotLevel(int plotLevel) {
        this.plotLevel = plotLevel;
    }

    public String getNameForPlayer(ServerLevel level) {
        Entity entity = level.getEntity(playerId);
        if (entity instanceof Player player) {
            return player.getGameProfile().name();
        }
        return "";
    }
}
