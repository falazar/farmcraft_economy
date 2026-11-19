package com.falazar.farmupcraft.command;

import com.falazar.farmupcraft.GemContent;
import com.falazar.farmupcraft.data.PlayerData;
import com.falazar.farmupcraft.data.VillageData;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

final class VillageUpgradeItems {
    private VillageUpgradeItems() {}

    record Requirement(Item item, int count) {
        int available(Inventory inventory) {
            return MarketCommand.countSaleItems(inventory, item);
        }

        String description(Inventory inventory) {
            return item.getDefaultInstance().getHoverName().getString() + ": " + available(inventory)
                    + " / " + count + " (consumed)";
        }
    }

    static boolean ensureChoices(VillageData village) {
        if (village.getUpgradeItems().size() == 4) return false;
        return ensureChoices(village, highNutritionFoods(BuiltInRegistries.ITEM.stream().toList()),
                GemContent.TYPES.stream().map(GemContent::gem).toList(), ThreadLocalRandom.current());
    }

    static boolean ensureChoices(VillageData village, List<Item> foods, List<Item> gems, RandomGenerator random) {
        if (foods.isEmpty() || gems.isEmpty())
            throw new IllegalStateException("Village upgrade food/gem candidates are missing");
        boolean changed = false;
        for (int target = 7; target <= 10; target++) {
            if (village.getUpgradeItems().containsKey(Integer.toString(target))) continue;
            List<Item> pool = target % 2 == 1 ? foods : gems;
            village.setUpgradeItem(target, BuiltInRegistries.ITEM.getKey(pool.get(random.nextInt(pool.size()))).toString());
            changed = true;
        }
        return changed;
    }

    static List<Item> highNutritionFoods(List<Item> items) {
        List<Item> ranked = items.stream().filter(item -> nutrition(item) > 0)
                .sorted(Comparator.comparingInt(VillageUpgradeItems::nutrition).reversed()
                        .thenComparing(item -> BuiltInRegistries.ITEM.getKey(item).toString()))
                .toList();
        if (ranked.isEmpty()) throw new IllegalStateException("No foods are registered for village upgrades");
        int cutoff = nutrition(ranked.get((ranked.size() - 1) / 10));
        return ranked.stream().filter(item -> nutrition(item) >= cutoff).toList();
    }

    private static int nutrition(Item item) {
        var food = item.getDefaultInstance().get(DataComponents.FOOD);
        return food == null ? 0 : food.nutrition();
    }

    static Requirement requirement(VillageData village, int targetLevel) {
        if (targetLevel < 7 || targetLevel > 10) return null;
        String id = village.getUpgradeItems().get(Integer.toString(targetLevel));
        if (id == null) throw new IllegalStateException("Village upgrade item has not been assigned");
        Item item = BuiltInRegistries.ITEM.getOptional(Identifier.parse(id))
                .orElseThrow(() -> new IllegalStateException("Required village upgrade item is missing: " + id));
        return new Requirement(item, targetLevel < 9 ? 64 : 128);
    }

    static boolean pay(VillageData village, PlayerData player, Inventory inventory, int coins, Requirement requirement) {
        if (requirement != null && requirement.available(inventory) < requirement.count()) return false;
        if (!VillageCommand.payLevelUpCost(village, player, coins)) return false;
        if (requirement != null)
            MarketCommand.removeItem(inventory, requirement.item().getDefaultInstance(), requirement.count());
        return true;
    }
}
