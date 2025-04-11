package com.falazar.farmupcraft.currency;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;

public enum Rarity implements StringRepresentable {
    COMMON(1, "Common"),
    UNCOMMON(2, "Uncommon"),
    RARE(3, "Rare"),
    EPIC(4, "Epic"),
    LEGENDARY(5, "Legendary");

    private final int tier;
    private final String displayName;

    Rarity(int tier, String displayName) {
        this.tier = tier;
        this.displayName = displayName;
    }

    public int getTier() {
        return tier;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static Rarity fromTier(int tier) {
        for (Rarity r : values()) {
            if (r.tier == tier) return r;
        }
        return COMMON;
    }
    public static final Codec<Rarity> CODEC = StringRepresentable.fromEnum(Rarity::values);

    @Override
    public String getSerializedName() {
        return this.name().toLowerCase(Locale.ROOT);
    }


    public static Rarity byName(String name) {
        return Rarity.valueOf(name.toUpperCase(Locale.ROOT));
    }
}
