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
                    CurrencyCost.CODEC.fieldOf("cost").forGetter(GoodsData::getCost)
                    // amountSold
                    // Dont need type right? just in list?
            ).apply(instance, GoodsData::new)
    );

    private final Item item;
    private final CurrencyCost cost;
    private int amountSold; // TODO USE
    private String rarity; // common, uncommon, rare.

    public GoodsData(Item item, CurrencyCost cost) {
        this.item = item;
        this.cost = cost;
    }

    public Item getItem() {
        return item;
    }

    public CurrencyCost getCost() {
        return cost;
    }
}
