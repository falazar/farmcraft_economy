package com.falazar.farmupcraft.data.rules.crop;

import com.mojang.serialization.MapCodec;

public interface CropRulesType<T extends CropRules> {
    MapCodec<T> codec();
}
