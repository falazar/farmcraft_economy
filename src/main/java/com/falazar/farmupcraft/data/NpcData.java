package com.falazar.farmupcraft.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Optional;
import java.util.UUID;

public class NpcData {

    public static final Codec<NpcData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.STRING_CODEC.fieldOf("id").forGetter(NpcData::getUUID),
            Codec.STRING.fieldOf("name").forGetter(NpcData::getName),
            UUIDUtil.STRING_CODEC.optionalFieldOf("home_village_id")
                    .forGetter(npc -> Optional.ofNullable(npc.getHomeVillageUUID())),
            Codec.STRING.optionalFieldOf("description", "").forGetter(NpcData::getDescription),
            Codec.STRING.optionalFieldOf("personality", "").forGetter(NpcData::getPersonality),
            Codec.STRING.optionalFieldOf("village_name", "").forGetter(NpcData::getVillageName),
            Codec.BOOL.optionalFieldOf("forever_kid", false).forGetter(NpcData::isForeverKid),
            Codec.STRING.optionalFieldOf("behavior", "nonhostile").forGetter(NpcData::getBehavior),
            Codec.STRING.optionalFieldOf("ai_profile_file", "").forGetter(NpcData::getAiProfileFile))
            .apply(instance, (id, name, homeVillageOpt, description, personality, villageName, foreverKid, behavior,
                    aiProfileFile) -> new NpcData(id, name, homeVillageOpt.orElse(null), description, personality,
                            villageName, foreverKid, behavior, aiProfileFile)));

    @Nonnull
    private final UUID uuid;
    @Nonnull
    private String name;
    private UUID homeVillageUUID;
    @Nonnull
    private String description = "";
    @Nonnull
    private String personality = "";
    @Nonnull
    private String villageName = "";
    private boolean foreverKid = false;
    @Nonnull
    private String behavior = "nonhostile";
    @Nonnull
    private String aiProfileFile = "";
    // todo add lastPos

    /** Legacy constructor — no personality. */
    public NpcData(UUID uuid, String name, UUID homeVillageUUID, String description) {
        this(uuid, name, homeVillageUUID, description, "", "", false, "nonhostile", "");
    }

    /** Constructor — no village_name. */
    public NpcData(UUID uuid, String name, UUID homeVillageUUID, String description, String personality) {
        this(uuid, name, homeVillageUUID, description, personality, "", false, "nonhostile", "");
    }

    /** Constructor — no foreverKid. */
    public NpcData(UUID uuid, String name, UUID homeVillageUUID, String description, String personality,
            String villageName) {
        this(uuid, name, homeVillageUUID, description, personality, villageName, false, "nonhostile", "");
    }

    public NpcData(UUID uuid, String name, UUID homeVillageUUID, String description, String personality,
            String villageName, boolean foreverKid) {
        this(uuid, name, homeVillageUUID, description, personality, villageName, foreverKid, "nonhostile", "");
    }

    public NpcData(UUID uuid, String name, UUID homeVillageUUID, String description, String personality,
            String villageName, boolean foreverKid, String behavior, String aiProfileFile) {
        this.uuid = uuid;
        this.name = name;
        this.homeVillageUUID = homeVillageUUID;
        this.description = description;
        this.personality = personality;
        this.villageName = villageName == null ? "" : villageName;
        this.foreverKid = foreverKid;
        this.behavior = behavior == null || behavior.isBlank() ? "nonhostile" : behavior;
        this.aiProfileFile = aiProfileFile == null ? "" : aiProfileFile;
    }

    /**
     * Gets the UUID.
     * <p>
     * This is the in-game UUID used to identify entities.
     * </p>
     * 
     * @return the npcs entity UUID
     */
    public UUID getUUID() {
        return uuid;
    }

    /**
     * Gets the home village id of the NPC.
     * 
     * @return the NPC's home village id
     */
    @Nullable
    public UUID getHomeVillageUUID() {
        return homeVillageUUID;
    }

    public void setHomeVillageId(@Nullable UUID homeVillageId) {
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

    @Nonnull
    public String getPersonality() {
        return personality;
    }

    public void setPersonality(@Nonnull String personality) {
        this.personality = personality;
    }

    @Nonnull
    public String getVillageName() {
        return villageName;
    }

    public void setVillageName(@Nonnull String villageName) {
        this.villageName = villageName;
    }

    public boolean isForeverKid() {
        return foreverKid;
    }

    public void setForeverKid(boolean foreverKid) {
        this.foreverKid = foreverKid;
    }

    @Nonnull
    public String getBehavior() {
        return behavior;
    }

    public void setBehavior(@Nonnull String behavior) {
        this.behavior = behavior;
    }

    @Nonnull
    public String getAiProfileFile() {
        return aiProfileFile;
    }

    public void setAiProfileFile(@Nonnull String aiProfileFile) {
        this.aiProfileFile = aiProfileFile;
    }

}
