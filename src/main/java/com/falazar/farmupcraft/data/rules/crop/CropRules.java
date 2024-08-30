package com.falazar.farmupcraft.data.rules.crop;

import com.falazar.farmupcraft.registry.FUCRegistries;
import com.mojang.serialization.Codec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.Item;

import java.util.List;

public interface CropRules {
    Codec<CropRules> DIRECT_CODEC = ExtraCodecs.
            lazyInitializedCodec(() ->
                    FUCRegistries.CROP_RULES_TYPE_SERIALIZER_SUPP.get().getCodec()
                    .dispatch(CropRules::type, CropRulesType::codec));

    List<Item> getCropItems(ServerLevel level);

    CropRulesType<? extends CropRules> type();
}
