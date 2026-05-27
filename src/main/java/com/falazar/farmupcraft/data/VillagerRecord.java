package com.falazar.farmupcraft.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;

import java.util.UUID;

/**
 * Stores a deceased villager's identity so the next new villager in the village
 * can be "reincarnated" with the same UUID and name, preserving the npcData
 * personality file link.
 */
public class VillagerRecord {

    public static final Codec<VillagerRecord> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.STRING_CODEC.fieldOf("uuid").forGetter(VillagerRecord::getUuid),
            Codec.STRING.fieldOf("name").forGetter(VillagerRecord::getName),
            BlockPos.CODEC.fieldOf("death_pos").forGetter(VillagerRecord::getDeathPos))
            .apply(instance, VillagerRecord::new));

    private final UUID uuid;
    private final String name;
    private final BlockPos deathPos;

    public VillagerRecord(UUID uuid, String name, BlockPos deathPos) {
        this.uuid = uuid;
        this.name = name;
        this.deathPos = deathPos;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public BlockPos getDeathPos() {
        return deathPos;
    }

    @Override
    public String toString() {
        return "VillagerRecord{name=" + name + ", uuid=" + uuid + ", deathPos=" + deathPos + "}";
    }
}
