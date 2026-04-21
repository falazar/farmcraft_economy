package com.falazar.farmupcraft.currency;

import com.mojang.serialization.Codec;

import static com.falazar.farmupcraft.FarmUpCraft.LOGGER;

import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;

public class Wallet {
    private final List<CoinStack> coins;

    public static final Codec<Wallet> CODEC = CoinStack.CODEC.listOf().fieldOf("coins").codec()
            .xmap(Wallet::new, Wallet::getAllStacks);

    public Wallet(List<CoinStack> stacks) {
        this.coins = new ArrayList<>(stacks);
        normalize();
    }

    /**
     * Remove any stacks with zero or negative amounts (can result from old buggy
     * saves).
     */
    private void normalize() {
        coins.removeIf(s -> s.getAmount() <= 0);
    }

    private static boolean sameCoin(Coin left, Coin right) {
        if (left == null || right == null) {
            return false;
        }
        return left.getId().equals(right.getId());
    }

    public void add(Coin coin, int amount) {
        CoinStack first = null;
        List<CoinStack> duplicates = new ArrayList<>();
        for (CoinStack stack : coins) {
            if (sameCoin(stack.getCoin(), coin)) {
                if (first == null) {
                    first = stack;
                } else {
                    // Merge duplicate stacks into the first one
                    first.increment(stack.getAmount());
                    duplicates.add(stack);
                }
            }
        }
        coins.removeAll(duplicates);
        if (first != null) {
            first.increment(amount);
        } else {
            coins.add(new CoinStack(coin, amount));
        }
    }

    public boolean remove(Coin coin, int amount) {
        int total = get(coin);
        if (total < amount) {
            LOGGER.info("DEBUG: Not enough coins to remove. Requested: " + amount + ", Available: " + total);
            return false;
        }
        int remaining = amount;
        normalize(); // purge any bad stacks before operating
        List<CoinStack> emptied = new ArrayList<>();
        for (CoinStack stack : coins) {
            if (remaining <= 0)
                break;
            if (sameCoin(stack.getCoin(), coin)) {
                int take = Math.min(stack.getAmount(), remaining);
                stack.increment(-take);
                remaining -= take;
                if (stack.isEmpty()) {
                    emptied.add(stack);
                }
            }
        }
        coins.removeAll(emptied);
        LOGGER.info("DEBUG: Removed " + amount + " coins. Remaining total: " + get(coin));
        return true;
    }

    public boolean hasEnough(Coin coin, int amount) {
        return get(coin) >= amount;
    }

    public int get(Coin coin) {
        return coins.stream().filter(c -> sameCoin(c.getCoin(), coin)).mapToInt(CoinStack::getAmount).sum();
    }

    public void set(Coin coin, int amount) {
        remove(coin, get(coin));
        add(coin, amount);
    }

    public List<CoinStack> getAllStacks() {
        return coins;
    }
}
