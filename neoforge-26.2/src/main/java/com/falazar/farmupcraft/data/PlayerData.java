package com.falazar.farmupcraft.data;

import com.falazar.farmupcraft.events.ModEvents;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

public class PlayerData {
    private static final UUID NO_VILLAGE = new UUID(0L, 0L);

    public static final Codec<PlayerData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("id").forGetter(PlayerData::getId),
            UUIDUtil.STRING_CODEC.optionalFieldOf("player_uuid", NO_VILLAGE).forGetter(PlayerData::getPlayerUUID),
            UUIDUtil.STRING_CODEC.optionalFieldOf("home_village_id", NO_VILLAGE)
                    .forGetter(pd -> pd.getHomeVillageUUID() != null ? pd.getHomeVillageUUID() : NO_VILLAGE),
            Codec.INT.optionalFieldOf("coins", 0).forGetter(PlayerData::getCoins),
            Codec.BOOL.optionalFieldOf("border_show", false).forGetter(PlayerData::isBorderShow),
            Codec.STRING.optionalFieldOf("border_color", "blue").forGetter(PlayerData::getBorderColor),
            Codec.LONG.optionalFieldOf("first_welcome_day", com.falazar.farmupcraft.util.PlayerOnboarding.UNSET_DAY)
                    .forGetter(PlayerData::getFirstWelcomeDay),
            Codec.BOOL.optionalFieldOf("village_map_show", false).forGetter(PlayerData::isVillageMapShow),
            Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("market_minimum_price", 10)
                    .forGetter(PlayerData::getMarketMinimumPrice),
            Codec.STRING.listOf().optionalFieldOf("market_favorites", java.util.List.of())
                    .forGetter(PlayerData::getMarketFavorites))
            .apply(instance, PlayerData::new));

    private final int id;
    private UUID playerUUID;
    private UUID homeVillageUUID;
    private int coins;
    private boolean borderShow;
    private String borderColor;
    private long firstWelcomeDay;
    private boolean villageMapShow;
    private int marketMinimumPrice;
    private final java.util.Set<String> marketFavorites = new java.util.LinkedHashSet<>();

    public java.util.List<String> getMarketFavorites() { return java.util.List.copyOf(marketFavorites); }

    public boolean isMarketFavorite(String itemId) { return marketFavorites.contains(itemId); }

    public void setMarketFavorite(String itemId, boolean favorite) {
        if (favorite) marketFavorites.add(itemId);
        else marketFavorites.remove(itemId);
    }

    public int getMarketMinimumPrice() { return marketMinimumPrice; }

    public void setMarketMinimumPrice(int price) {
        if (price < 1) throw new IllegalArgumentException("Market minimum price must be positive");
        marketMinimumPrice = price;
    }

    public boolean isVillageMapShow() { return villageMapShow; }

    public void setVillageMapShow(boolean show) { villageMapShow = show; }

    public long getFirstWelcomeDay() { return firstWelcomeDay; }

    public void setFirstWelcomeDay(long day) { firstWelcomeDay = day; }

    public PlayerData(int id, UUID playerUUID, UUID homeVillageUUID, int coins, boolean borderShow, String borderColor) {
        this(id, playerUUID, homeVillageUUID, coins, borderShow, borderColor,
                com.falazar.farmupcraft.util.PlayerOnboarding.UNSET_DAY);
    }

    public PlayerData(int id, UUID playerUUID, UUID homeVillageUUID, int coins, boolean borderShow,
                      String borderColor, long firstWelcomeDay) {
        this(id, playerUUID, homeVillageUUID, coins, borderShow, borderColor, firstWelcomeDay, false);
    }

    public PlayerData(int id, UUID playerUUID, UUID homeVillageUUID, int coins, boolean borderShow,
                      String borderColor, long firstWelcomeDay, boolean villageMapShow) {
        this(id, playerUUID, homeVillageUUID, coins, borderShow, borderColor, firstWelcomeDay, villageMapShow, 10);
    }

    public PlayerData(int id, UUID playerUUID, UUID homeVillageUUID, int coins, boolean borderShow,
                      String borderColor, long firstWelcomeDay, boolean villageMapShow, int marketMinimumPrice) {
        this(id, playerUUID, homeVillageUUID, coins, borderShow, borderColor, firstWelcomeDay,
                villageMapShow, marketMinimumPrice, java.util.List.of());
    }

    public PlayerData(int id, UUID playerUUID, UUID homeVillageUUID, int coins, boolean borderShow,
                      String borderColor, long firstWelcomeDay, boolean villageMapShow, int marketMinimumPrice,
                      java.util.List<String> marketFavorites) {
        this.id = id;
        this.playerUUID = playerUUID;
        this.homeVillageUUID = homeVillageUUID;
        this.coins = Math.max(0, coins);
        this.borderShow = borderShow;
        this.borderColor = borderColor;
        this.firstWelcomeDay = firstWelcomeDay;
        this.villageMapShow = villageMapShow;
        setMarketMinimumPrice(marketMinimumPrice);
        this.marketFavorites.addAll(marketFavorites);
    }

    public PlayerData(int id, UUID playerUUID, UUID homeVillageUUID, int coins) {
        this(id, playerUUID, homeVillageUUID, coins, false, "blue");
    }

    public String getNameForPlayer(ServerLevel level, UUID playerUUID) {
        Entity entity = level.getEntity(playerUUID);
        return entity instanceof Player player ? player.getGameProfile().name() : "";
    }

    public int getId() {
        return id;
    }

    public UUID getPlayerUUID() {
        return playerUUID;
    }

    public void setPlayerUUID(UUID playerUUID) {
        this.playerUUID = playerUUID;
    }

    public UUID getHomeVillageUUID() {
        return homeVillageUUID == null || NO_VILLAGE.equals(homeVillageUUID) ? null : homeVillageUUID;
    }

    public VillageData getHomeVillage() {
        return ModEvents.getVillageDatabase().getData(homeVillageUUID);
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
        if (amount > 0) this.coins += amount;
    }

    public boolean removeCoins(int amount) {
        if (amount < 0 || coins < amount) return false;
        coins -= amount;
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
