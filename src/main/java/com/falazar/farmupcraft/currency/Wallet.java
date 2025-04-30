package com.falazar.farmupcraft.currency;

import com.mojang.serialization.Codec;

import java.util.ArrayList;
import java.util.List;

public class Wallet {
    private final List<CoinStack> coins;

    public static final Codec<Wallet> CODEC = CoinStack.CODEC.listOf().fieldOf("coins").codec()
            .xmap(Wallet::new, Wallet::getAllStacks);

    public Wallet(List<CoinStack> stacks) {
        this.coins = new ArrayList<>(stacks);
    }

    public void add(Coin coin, int amount) {
        for (CoinStack stack : coins) {
            if (stack.getCoin() == coin) {
                stack.increment(amount);  // TODO scouter, this isnt working or?
                return;
            }
        }
        coins.add(new CoinStack(coin, amount));
    }

    public boolean remove(Coin coin, int amount) {
        for (CoinStack stack : coins) {
            if (stack.getCoin() == coin) {
                if (stack.getAmount() < amount) return false;
                stack.increment(-amount);
                if (stack.isEmpty()) coins.remove(stack);
                return true;
            }
        }
        return false;
    }

    public boolean hasEnough(Coin coin, int amount) {
        return coins.stream().anyMatch(c -> c.getCoin() == coin && c.getAmount() >= amount);
    }

    public int get(Coin coin) {
        return coins.stream().filter(c -> c.getCoin() == coin).mapToInt(CoinStack::getAmount).sum();
    }

    public void set(Coin coin, int amount) {
        remove(coin, get(coin));
        add(coin, amount);
    }

    public List<CoinStack> getAllStacks() {
        return coins;
    }
}
