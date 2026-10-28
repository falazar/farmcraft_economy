package com.falazar.farmupcraft.data;

import com.falazar.farmupcraft.database.DataBase;
import com.falazar.farmupcraft.events.ModEvents;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

public class PlayerData {

    public static final Codec<PlayerData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("id").forGetter(PlayerData::getId),
            UUIDUtil.STRING_CODEC.optionalFieldOf("player_uuid", new UUID(0L, 0L)).forGetter(PlayerData::getPlayerUUID),
            UUIDUtil.STRING_CODEC.optionalFieldOf("home_village_id", new UUID(0L, 0L))
                    .forGetter(pd -> pd.getHomeVillageUUID() != null ? pd.getHomeVillageUUID() : new UUID(0L, 0L)),
            Codec.INT.optionalFieldOf("coins", 0).forGetter(PlayerData::getCoins),
            Codec.BOOL.optionalFieldOf("border_show", false).forGetter(PlayerData::isBorderShow),
            Codec.STRING.optionalFieldOf("border_color", "blue").forGetter(PlayerData::getBorderColor))
            .apply(instance, PlayerData::new));

    private final int id;
    private UUID playerUUID;
    private UUID homeVillageUUID;
    private int coins;
    private boolean borderShow;
    private String borderColor;
    // todo add level
    // todo add experience
    // todo add lastPos

    /** Full constructor used by CODEC. */
    public PlayerData(int id, UUID playerUUID, UUID homeVillageUUID, int coins, boolean borderShow,
            String borderColor) {
        this.id = id;
        this.playerUUID = playerUUID;
        this.homeVillageUUID = homeVillageUUID;
        this.coins = Math.max(0, coins);
        this.borderShow = borderShow;
        this.borderColor = borderColor;
    }

    /**
     * Constructs a new PlayerData object.
     * 
     * @param id              the integer ID representing the player entity, NOT the
     *                        player's UUID
     * @param playerUUID      the stable UUID of the player
     * @param homeVillageUUID uuid value for village id.
     * @param coins           the player's coin balance
     */
    public PlayerData(int id, UUID playerUUID, UUID homeVillageUUID, int coins) {
        this(id, playerUUID, homeVillageUUID, coins, false, "blue");
    }

    /**
     * Retrieves the name of the player associated with this data from the server.
     *
     * @param level the server level where the player is located
     * @return the player's name, or an empty string if the entity is not a player
     *         or cannot be found
     */
    public String getNameForPlayer(ServerLevel level, UUID playerUUID) {
        Entity entity = level.getEntity(playerUUID);
        if (entity instanceof Player player) {
            return player.getGameProfile().getName();
        }
        return "";
    }

    /**
     * Gets the player's integer entity ID.
     * <p>
     * This is the in-game integer ID used to identify entities, not the player's
     * UUID.
     * </p>
     * 
     * @return the player's integer entity ID
     */
    public int getId() {
        return id;
    }

    /**
     * Gets the stable UUID of the player.
     * 
     * @return the player's UUID
     */
    public UUID getPlayerUUID() {
        return playerUUID;
    }

    public void setPlayerUUID(UUID playerUUID) {
        this.playerUUID = playerUUID;
    }

    /**
     * Gets the home village id of the player.
     * 
     * @return the player's home village id
     */
    public UUID getHomeVillageUUID() {
        // Return null for the zero-UUID sentinel (means "no village"), so callers
        // can still do == null checks. The Codec getter uses a separate null-safe
        // lambda.
        if (homeVillageUUID == null || homeVillageUUID.equals(new UUID(0L, 0L)))
            return null;
        return homeVillageUUID;
    }

    // Get player home village.
    public VillageData getHomeVillage() {
        DataBase<UUID, VillageData> villageDataDB = ModEvents.getVillageDatabase();
        VillageData village = villageDataDB.getData(homeVillageUUID);
        return village;
    }

    public void setHomeVillageId(UUID homeVillageId) {
        this.homeVillageUUID = homeVillageId;
    }

    public int getCoins() {
        return coins;
    }

    public void setCoins(int coins) {
        this.coins = Math.max(0, coins);
    }

    public void addCoins(int amount) {
        if (amount <= 0) {
            return;
        }
        this.coins += amount;
    }

    public boolean removeCoins(int amount) {
        if (amount < 0) {
            return false;
        }
        if (this.coins < amount) {
            return false;
        }
        this.coins -= amount;
        return true;
    }

    public boolean isBorderShow() {
        return borderShow;
    }

    public void setBorderShow(boolean borderShow) {
        this.borderShow = borderShow;
    }

    public String getBorderColor() {
        return borderColor;
    }

    public void setBorderColor(String borderColor) {
        this.borderColor = borderColor;
    }

}
