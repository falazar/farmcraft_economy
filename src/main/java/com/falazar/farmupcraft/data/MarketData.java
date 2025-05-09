package com.falazar.farmupcraft.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

public class MarketData {

    public static final Codec<MarketData> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.list(GoodsData.CODEC).fieldOf("goods").forGetter(MarketData::getGoods)
                    // Add other market-specific fields here if needed
                    // TODO on changes here we need to save back to db.
            ).apply(instance, MarketData::new)
    );

    private final List<GoodsData> goods;

    public MarketData(List<GoodsData> goods) {
        this.goods = goods;
    }

    public List<GoodsData> getGoods() {
        return goods;
    }

    // TODO sell market items method needs to call here to
    // subtract 1 per 32 items sold.
    // and add 1 randomly to another in the list per 64 items sold.
    // and save new data to disk.

    // TODO runDailyMarket method should call over here to
    // Update each item in this market price by 1.
    // need to save last time runDailyMarket is called also. Global settings?

}
