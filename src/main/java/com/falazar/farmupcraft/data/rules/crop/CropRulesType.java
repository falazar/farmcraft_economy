package com.falazar.farmupcraft.data.rules.crop;

import com.mojang.serialization.Codec;

public interface CropRulesType <T extends CropRules> {
    Codec<T> codec();
}