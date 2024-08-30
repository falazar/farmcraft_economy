package com.falazar.farmupcraft.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

public class MarketData {

    public static final Codec<MarketData> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.list(GoodsData.CODEC).fieldOf("goods").forGetter(MarketData::getGoods)
                    // Add other market-specific fields here if needed
            ).apply(instance, MarketData::new)
    );

    private final List<GoodsData> goods;

    public MarketData(List<GoodsData> goods) {
        this.goods = goods;
    }

    public List<GoodsData> getGoods() {
        return goods;
    }
}
