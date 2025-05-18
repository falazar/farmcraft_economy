package com.falazar.farmupcraft.data;

import com.falazar.farmupcraft.currency.CoinStack;
import com.falazar.farmupcraft.currency.CurrencyCost;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;

public class GoodsData {
    public static final Codec<GoodsData> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    BuiltInRegistries.ITEM.byNameCodec().fieldOf("item").forGetter(GoodsData::getItem),
                    CurrencyCost.CODEC.fieldOf("cost").forGetter(GoodsData::getCost),
                    Codec.INT.optionalFieldOf("amountSold", 5).forGetter(GoodsData::getAmountSold),
                    Codec.STRING.optionalFieldOf("rarity", "common").forGetter(GoodsData::getRarity),
                    Codec.BOOL.optionalFieldOf("active", false).forGetter(GoodsData::isActive),
                    Codec.STRING.optionalFieldOf("marketType", "").forGetter(GoodsData::getMarketType)
            ).apply(instance, GoodsData::new)
    );

    private final Item item; // TODO item code?
    private final CurrencyCost cost; // current coins to sell item for.
    private int amountSold; // How many items sold so far.
    private String rarity; // common, uncommon, rare.
    private Boolean active;  // Is the item currently active in a market.
    private String marketType; // One of 4 or more types, "wood", "stone", "food", "general" etc.
    // TODO one more dateAddedToMarket, so we can sort it.

    public GoodsData(Item item, CurrencyCost cost, int amountSold, String rarity, boolean active, String marketType) {
        this.item = item;
        this.cost = cost;
        this.amountSold = amountSold;
        this.rarity = rarity;
        this.active = active;
        this.marketType = marketType;
    }

    public Item getItem() {
        return item;
    }

    public CurrencyCost getCost() {
        return cost;
    }

    public int getAmountSold() {
        return amountSold;
    }

    public void setAmountSold(int amountSold) {
        this.amountSold = amountSold;
    }

    public String getRarity() {
        return rarity;
    }

    public void setRarity(String rarity) {
        this.rarity = rarity;
    }

    public Boolean isActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public String getMarketType() {
        return marketType;
    }

    public void setMarketType(String marketType) {
        this.marketType = marketType;
    }
}
