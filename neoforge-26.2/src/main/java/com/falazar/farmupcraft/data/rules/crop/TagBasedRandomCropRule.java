package com.falazar.farmupcraft.data.rules.crop;

import com.falazar.farmupcraft.registry.CropRulesRegistry;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Selects crops deterministically from a lazily cached item tag.
 */
public class TagBasedRandomCropRule implements CropRules {
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final MapCodec<TagBasedRandomCropRule> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    TagKey.codec(Registries.ITEM).fieldOf("tag").forGetter(TagBasedRandomCropRule::getCropTag),
                    Codec.INT.fieldOf("random_crop_count").forGetter(TagBasedRandomCropRule::getRandomCropCount)
            ).apply(instance, TagBasedRandomCropRule::new));
    public static final CropRulesType<TagBasedRandomCropRule> TYPE = () -> CODEC;

    private final TagKey<Item> cropTag;
    private final int randomCropCount;
    private final List<Item> items = new ArrayList<>();

    public TagBasedRandomCropRule(TagKey<Item> cropTag, int randomCropCount) {
        this.cropTag = cropTag;
        this.randomCropCount = randomCropCount;
    }

    public TagKey<Item> getCropTag() {
        return cropTag;
    }

    public int getRandomCropCount() {
        return randomCropCount;
    }

    @Override
    public List<Item> getCropItems(ServerLevel level, int id) {
        if (items.isEmpty()) {
            level.registryAccess().lookup(Registries.ITEM).ifPresent(reg -> {
                for (Holder<Item> itemHolder : reg.getTagOrEmpty(getCropTag())) {
                    items.add(itemHolder.value());
                }
            });
        }
        List<Item> randomList = selectCrops(items, level.getSeed(), id, randomCropCount);

        if (ModList.get().isLoaded("pamhc2crops")) {
            List<Item> additionalItems = new ArrayList<>();
            for (Item item : randomList) {
                Identifier location = BuiltInRegistries.ITEM.getKey(item);
                if (location == null) continue;
                if (!location.toString().contains("pamhc2crops")) continue;
                addItemIfValid(additionalItems, pairedItemId(location.toString()));
            }
            randomList.addAll(additionalItems);
        }
        return randomList;
    }

    static <T> List<T> selectCrops(List<T> crops, long seed, int id, int count) {
        Random random = new Random(seed + id);
        List<T> shuffledCrops = new ArrayList<>(crops);
        Collections.shuffle(shuffledCrops, random);
        return shuffledCrops.subList(0, Math.min(count, shuffledCrops.size()));
    }

    // Keep the legacy seed-name rules, including the mustard and sesame exceptions.
    static String pairedItemId(String locationString) {
        String modifiedString;
        if (locationString.contains("seed")) {
            String[] parts = locationString.split("seed");
            if (parts.length == 3) {
                modifiedString = parts[0] + "seed" + parts[1] + parts[2];
            } else if (parts.length == 2) {
                modifiedString = parts[0] + parts[1];
            } else {
                modifiedString = locationString;
            }
        } else {
            modifiedString = locationString.replace("item", "seeditem");
        }
        if (locationString.equals("pamhc2crops:sesameseedsseeditem")) {
            modifiedString = "pamhc2crops:sesameseedsitem";
        }
        if (locationString.equals("pamhc2crops:mustardseedsitem")) {
            modifiedString = "pamhc2crops:mustardseedsseeditem";
        }
        if (locationString.equals("pamhc2crops:sesameseedsitem")) {
            modifiedString = "pamhc2crops:sesameseedsseeditem";
        }
        if (locationString.equals("pamhc2crops:onionitem")) {
            modifiedString = "pamhc2crops:onionseeditem";
        }
        return modifiedString;
    }

    private void addItemIfValid(List<Item> list, String locationString) {
        Identifier loc = Identifier.parse(locationString);
        Item itemExtra = BuiltInRegistries.ITEM.getValue(loc);
        if (itemExtra != null && !itemExtra.getDefaultInstance().is(Items.AIR)) {
            list.add(itemExtra);
        } else {
            LOGGER.warn("Tried to add a non existing item for {}", loc);
        }
    }

    @Override
    public CropRulesType<? extends CropRules> type() {
        return CropRulesRegistry.TAG_BASED_RANDOM_CROP_RULE.get();
    }
}
