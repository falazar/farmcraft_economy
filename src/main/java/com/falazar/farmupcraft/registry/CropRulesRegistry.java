package com.falazar.farmupcraft.registry;

import com.falazar.farmupcraft.FarmUpCraft;
import com.falazar.farmupcraft.data.rules.crop.CropRulesType;
import com.falazar.farmupcraft.data.rules.crop.ItemListCropRule;
import com.falazar.farmupcraft.data.rules.crop.TagBasedRandomCropRule;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class CropRulesRegistry {
    public static final DeferredRegister<CropRulesType<?>> CROP_RULE = DeferredRegister.create(FUCRegistries.Keys.CROP_RULES_SERIALIZER, FarmUpCraft.MODID);
    public static final RegistryObject<CropRulesType<ItemListCropRule>> ITEM_LIST_CROP_RULE = CROP_RULE.register("item_list_crop_rule", () -> ItemListCropRule.TYPE);
    public static final RegistryObject<CropRulesType<TagBasedRandomCropRule>> TAG_BASED_RANDOM_CROP_RULE = CROP_RULE.register("tag_based_random_crop_rule", () -> TagBasedRandomCropRule.TYPE);

}
