package com.falazar.farmupcraft.currency;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

public class CoinStack {
    public static final Codec<CoinStack> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Coin.DIRECT_CODEC.fieldOf("coin").forGetter(CoinStack::getCoin),
            Codec.INT.fieldOf("amount").forGetter(CoinStack::getAmount)
    ).apply(instance, CoinStack::new));
    private final Coin coin;
    private int amount;

    public CoinStack(Coin coin, int amount) {
        this.coin = coin;
        this.amount = amount;
    }

    public Coin getCoin() {
        return coin;
    }

    public int getAmount() {
        return amount;
    }

    public void setAmount(int amt) {
        this.amount = amt;
    }

    public void increment(int delta) {
        this.amount += delta;
    }

    public boolean isEmpty() {
        return amount <= 0;
    }

    public ResourceLocation getId() {
        return coin.getId();
    }
}
