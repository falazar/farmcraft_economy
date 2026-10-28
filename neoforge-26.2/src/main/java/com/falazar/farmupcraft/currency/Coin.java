package com.falazar.farmupcraft.currency;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

public class Coin {
    public static final Codec<Coin> DIRECT_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Identifier.CODEC.fieldOf("id").forGetter(Coin::getId),
            Codec.STRING.fieldOf("translation_key").forGetter(Coin::getDisplayName),
            Codec.INT.fieldOf("value").forGetter(Coin::getBaseValue),
            Rarity.CODEC.fieldOf("rarity").forGetter(Coin::getRarity)
    ).apply(instance, Coin::new));

    private final Identifier id;
    private final String displayName;
    private final int baseValue;
    private final Rarity rarity;

    public Coin(Identifier id, String displayName, int baseValue, Rarity rarity) {
        this.id = id;
        this.displayName = displayName;
        this.baseValue = baseValue;
        this.rarity = rarity;
    }

    public Identifier getId() { return id; }
    public String getDisplayName() { return displayName; }
    public int getBaseValue() { return baseValue; }
    public Rarity getRarity() { return rarity; }
}
