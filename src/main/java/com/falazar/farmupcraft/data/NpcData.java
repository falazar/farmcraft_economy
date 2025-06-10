package com.falazar.farmupcraft.data;

import com.falazar.farmupcraft.currency.Wallet;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nonnull;
import java.util.UUID;

public class NpcData {

    public static final Codec<NpcData> CODEC = RecordCodecBuilder.create(instance ->
                    instance.group(
                            UUIDUtil.STRING_CODEC.fieldOf("id").forGetter(NpcData::getUUID),
                            Codec.STRING.fieldOf("name").forGetter(NpcData::getName),
                            UUIDUtil.STRING_CODEC.optionalFieldOf("home_village_id", UUID.randomUUID()).forGetter(NpcData::getHomeVillageUUID),
                            Codec.STRING.optionalFieldOf("description", "").forGetter(NpcData::getDescription)
                    ).apply(instance, NpcData::new)
    );

    @Nonnull
    private final UUID uuid;
    @Nonnull
    private String name;
    @Nonnull
    private UUID homeVillageUUID;
    @Nonnull
    private String description = "";
    // todo add lastPos

    /**
     * Constructs a new NpdData object.
     *
     * @param uuid              the UUID representing the npc entity.
     * @param homeVillageUUID uuid value for village id.
     */
    public NpcData(UUID uuid, String name, UUID homeVillageUUID, String description) {
        this.uuid = uuid;
        this.name = name;
        this.homeVillageUUID = homeVillageUUID;
        this.description = description;
    }

    /**
     * Gets the UUID.
     * <p>This is the in-game UUID used to identify entities.</p>
     * @return the npcs entity UUID
     */
    public UUID getUUID() {
        return uuid;
    }

    /**
     * Gets the home village id of the NPC.
     * @return the NPC's home village id
     */
    public UUID getHomeVillageUUID() {
        return homeVillageUUID;
    }

    @Nonnull
    public void setHomeVillageId(UUID homeVillageId) {
        this.homeVillageUUID = homeVillageId;
    }

    @Nonnull
    public String getName() {
        return name;
    }

    public void setName(@Nonnull String name) {
        this.name = name;
    }

    @Nonnull
    public String getDescription() {
        return description;
    }
    public void setDescription(@Nonnull String description) {
        this.description = description;
    }

}
