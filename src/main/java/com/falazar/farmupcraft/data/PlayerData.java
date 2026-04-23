package com.falazar.farmupcraft.data;

import com.falazar.farmupcraft.currency.Wallet;
import com.falazar.farmupcraft.database.DataBase;
import com.falazar.farmupcraft.events.ModEvents;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nonnull;
import java.util.UUID;

public class PlayerData {

    public static final Codec<PlayerData> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.INT.fieldOf("id").forGetter(PlayerData::getId),
                    UUIDUtil.STRING_CODEC.optionalFieldOf("home_village_id", UUID.randomUUID()).forGetter(PlayerData::getHomeVillageUUID),
                    Wallet.CODEC.fieldOf("wallet").forGetter(PlayerData::getWallet),
                    Codec.BOOL.optionalFieldOf("border_show", false).forGetter(PlayerData::isBorderShow),
                    Codec.STRING.optionalFieldOf("border_color", "blue").forGetter(PlayerData::getBorderColor)
            ).apply(instance, PlayerData::new)
    );

    // TODO change to UUID
    @Nonnull
    private final int id;
    @Nonnull
    private UUID homeVillageUUID;
    @Nonnull
    private final Wallet wallet;
    private boolean borderShow;
    private String borderColor;
    // todo add level
    // todo add experience
    // todo add lastPos

    /** Full constructor used by CODEC. */
    public PlayerData(int id, UUID homeVillageUUID, Wallet wallet, boolean borderShow, String borderColor) {
        this.id = id;
        this.homeVillageUUID = homeVillageUUID;
        this.wallet = wallet;
        this.borderShow = borderShow;
        this.borderColor = borderColor;
    }

    /**
     * Constructs a new PlayerData object.
     * @param id            the integer ID representing the player entity, NOT the player's UUID
     * @param homeVillageUUID uuid value for village id.
     * @param wallet         the wallet with coins the player has
     */
    public PlayerData(int id, UUID homeVillageUUID, Wallet wallet) {
        this(id, homeVillageUUID, wallet, false, "blue");
    }

    /**
     * Retrieves the name of the player associated with this data from the server.
     *
     * @param level the server level where the player is located
     * @return the player's name, or an empty string if the entity is not a player or cannot be found
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
     * <p>This is the in-game integer ID used to identify entities, not the player's UUID.</p>
     * @return the player's integer entity ID
     */
    public int getId() {
        return id;
    }

    /**
     * Gets the home village id of the player.
     * @return the player's home village id
     */
    public UUID getHomeVillageUUID() {
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

    /**
     * Gets the number of coins the player has.
     * @return the player's coins
     */
    public Wallet getWallet() {
        return wallet;
    }
}
