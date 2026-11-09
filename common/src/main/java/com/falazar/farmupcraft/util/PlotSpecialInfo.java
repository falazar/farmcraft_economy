package com.falazar.farmupcraft.util;

import java.util.List;
import java.util.ArrayList;
import java.util.Set;

public final class PlotSpecialInfo {
    private PlotSpecialInfo() {}

    public static List<VillageView.Entry> nursery(NurseryTreeCounter.Counts trees) {
        String breakdown = trees.byType().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey())
                .map(e -> e.getKey() + ": " + e.getValue()).collect(java.util.stream.Collectors.joining(", "));
        return List.of(
                entry("Fruit / nut trees", Integer.toString(trees.total())),
                entry("Tree breakdown", breakdown.isEmpty() ? "None found" : breakdown),
                entry("Tree scan", "All logs in this chunk, full world height; fruit within one block identifies the tree. "
                        + "Connected logs count once; leaves do not join trees. No attached fruit means not counted."));
    }

    public static List<VillageView.Entry> farm(int level, int bonus, boolean biomeRules, String crops, int y) {
        return List.of(
                entry("Crops planted", crops.isBlank() ? "None found" : crops),
                entry("Crop scan height", "Y " + (y - 1) + " through " + (y + 2) + " in this chunk"),
                entry("Harvest bonus", bonus == 0 ? "None" : bonus + "% chance of +1 crop (Pam right-click, Survival)"),
                entry("Biome planting", !biomeRules ? "Biome rules disabled or bypassed"
                        : level >= 3 ? "Tagged crops may use any biome in this chunk at planting height"
                        : "Tagged crops must match the biome at the planting location"),
                entry("Trample protection", "Protection at level 2"),
                entry("Growth speed", "No bonus"),
                entry("Crop help", "/show biomecrops; /show cropbiomes <crop>; middle-click farmland for eligible inventory crops"));
    }

    public static List<VillageView.Entry> pasture(int count, int cap, String animals, int extra,
                                                 int dye, String species, String grains) {
        return pasture(count, cap, animals, extra, dye, species, grains, Set.of());
    }

    public static List<VillageView.Entry> pasture(int count, int cap, String animals, int extra,
                                                 int dye, String species, String grains, Set<String> animalTypes) {
        boolean sheep = species.equals("sheep") || animalTypes.contains("sheep");
        boolean milk = species.equals("cow") || species.equals("mooshroom")
                || animalTypes.contains("cow") || animalTypes.contains("mooshroom");
        boolean eggs = species.equals("chicken") || species.equals("duck")
                || animalTypes.contains("chicken") || animalTypes.contains("duck");
        var entries = new ArrayList<>(List.of(
                new VillageView.Entry("Animals", count + " / " + cap + (count >= cap ? " - FULL" : " - " + (cap - count) + " spaces"),
                        count >= cap ? VillageView.NEGATIVE : VillageView.POSITIVE),
                entry("Animal breakdown", animals.isBlank() ? "None found" : animals),
                entry("Breeding species", species.isBlank() ? "No village species restriction" : species),
                entry("Breeding feed", grains),
                entry("Breeding rules", "Own village pasture; below animal cap; once per real day per animal")));
        if (milk) entries.add(entry("Milk rules", "Pasture only, once per real day"));
        if (extra > 0) entries.add(entry("Pasture drop bonus",
                (sheep ? "+" + extra + " wool when shearing; " : "")
                        + "+" + extra + " per loot stack from eligible pasture animals"));
        if (sheep) entries.add(entry("Sheep dye success", dye + "% (failed attempts consume dye)"));
        if (eggs) entries.add(entry("Egg hatching", "Pasture required; blocked at animal cap"));
        return List.copyOf(entries);
    }

    private static VillageView.Entry entry(String label, String value) {
        return new VillageView.Entry(label, value, VillageView.NORMAL);
    }
}
