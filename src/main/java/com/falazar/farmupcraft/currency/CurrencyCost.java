package com.falazar.farmupcraft.currency;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record CurrencyCost(Coin coin, int amount) {
    public boolean canAfford(Wallet wallet) {
        return wallet.hasEnough(coin, amount);
    }

    public boolean pay(Wallet wallet) {
        return wallet.remove(coin, amount);
    }

    public static final Codec<CurrencyCost> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Coin.DIRECT_CODEC.fieldOf("coin").forGetter(CurrencyCost::coin),
            Codec.INT.fieldOf("amount").forGetter(CurrencyCost::amount)
    ).apply(instance, CurrencyCost::new));
}

