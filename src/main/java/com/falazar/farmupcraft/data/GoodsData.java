package com.falazar.farmupcraft.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;

public class GoodsData {

    public static final Codec<GoodsData> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    BuiltInRegistries.ITEM.byNameCodec().fieldOf("item").forGetter(GoodsData::getItem),
                    Codec.INT.fieldOf("cost").forGetter(GoodsData::getCost)
            ).apply(instance, GoodsData::new)
    );

    private final Item item;
    private final int cost;

    public GoodsData(Item item, int cost) {
        this.item = item;
        this.cost = cost;
    }

    public Item getItem() {
        return item;
    }

    public int getCost() {
        return cost;
    }
}
