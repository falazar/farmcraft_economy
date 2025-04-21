package com.falazar.farmupcraft.currency;

import com.falazar.farmupcraft.registry.FUCRegistries;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceKey;

public record CurrencyCost(Coin coin, int amount) {
    public boolean canAfford(Wallet wallet) {
        return wallet.hasEnough(coin, amount);
    }

    public boolean pay(Wallet wallet) {
        return wallet.remove(coin, amount);
    }

    //todo change this to resourcekey
    public static final Codec<CurrencyCost> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            //ResourceKey.codec(FUCRegistries.Keys.COIN).fieldOf("coin").forGetter(CurrencyCost::coin),
            Coin.DIRECT_CODEC.fieldOf("coin").forGetter(CurrencyCost::coin),
            Codec.INT.fieldOf("amount").forGetter(CurrencyCost::amount)
    ).apply(instance, CurrencyCost::new));
}

