package com.falazar.farmupcraft.saveddata;

import com.falazar.farmupcraft.data.BiomeRulesData;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** A biome's rules and persisted, lazily selected crops. */
public class BiomeRulesInstance {
    public static final Codec<BiomeRulesInstance> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    BiomeRulesData.CODEC.fieldOf("rules_data").forGetter(BiomeRulesInstance::getRulesData),
                    BuiltInRegistries.ITEM.byNameCodec().listOf().fieldOf("random_crops")
                            .forGetter(BiomeRulesInstance::getRandomCrops)
            ).apply(instance, BiomeRulesInstance::new));

    public static final BiomeRulesInstance EMPTY = new BiomeRulesInstance(BiomeRulesData.EMPTY);

    private final BiomeRulesData rulesData;
    private List<Item> randomCrops;
    private boolean shouldSave = true;
    private Runnable onChanged = () -> {};

    public BiomeRulesInstance(BiomeRulesData rulesData) {
        this(rulesData, List.of());
    }

    public BiomeRulesInstance(BiomeRulesData rulesData, List<Item> randomCrops) {
        this.rulesData = rulesData;
        this.randomCrops = new ArrayList<>(randomCrops);
    }

    public BiomeRulesData getRulesData() {
        return rulesData;
    }

    private List<Item> getRandomCrops() {
        return randomCrops;
    }

    public List<Item> getCrops(ServerLevel level) {
        return getCrops(level, false);
    }

    public List<Item> getCrops(ServerLevel level, boolean newTry) {
        if (newTry) {
            return getCropsWithRandomId(level, 0);
        }
        return randomCrops;
    }

    public List<Item> getCropsWithRandomId(ServerLevel level, int id) {
        if (randomCrops.isEmpty()) {
            randomCrops = new ArrayList<>(rulesData.getCropRules().getCropItems(level, id));
            onChanged.run();
        }
        return randomCrops;
    }

    public boolean biomeHasCrops(ItemStack itemStack) {
        return randomCrops.contains(itemStack.getItem());
    }

    public void setShouldSave(boolean shouldSave) {
        this.shouldSave = shouldSave;
        onChanged.run();
    }

    public boolean shouldSave() {
        return shouldSave;
    }

    void onChanged(Runnable onChanged) {
        this.onChanged = onChanged;
    }
}
