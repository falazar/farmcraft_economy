package com.falazar.farmupcraft.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

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

    @NonNull
    private final UUID uuid;
    @NonNull
    private String name;
    private UUID homeVillageUUID;
    @NonNull
    private String description = "";
    @NonNull
    private String personality = "";
    @NonNull
    private String villageName = "";
    private boolean foreverKid = false;
    @NonNull
    private String behavior = "nonhostile";
    @NonNull
    private String aiProfileFile = "";

    public NpcData(UUID uuid, String name, UUID homeVillageUUID, String description) {
        this(uuid, name, homeVillageUUID, description, "", "", false, "nonhostile", "");
    }

    public NpcData(UUID uuid, String name, UUID homeVillageUUID, String description, String personality) {
        this(uuid, name, homeVillageUUID, description, personality, "", false, "nonhostile", "");
    }

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

    public UUID getUUID() {
        return uuid;
    }

    @Nullable
    public UUID getHomeVillageUUID() {
        return homeVillageUUID;
    }

    public void setHomeVillageId(@Nullable UUID homeVillageId) {
        this.homeVillageUUID = homeVillageId;
    }

    @NonNull
    public String getName() {
        return name;
    }

    public void setName(@NonNull String name) {
        this.name = name;
    }

    @NonNull
    public String getDescription() {
        return description;
    }

    public void setDescription(@NonNull String description) {
        this.description = description;
    }

    @NonNull
    public String getPersonality() {
        return personality;
    }

    public void setPersonality(@NonNull String personality) {
        this.personality = personality;
    }

    @NonNull
    public String getVillageName() {
        return villageName;
    }

    public void setVillageName(@NonNull String villageName) {
        this.villageName = villageName;
    }

    public boolean isForeverKid() {
        return foreverKid;
    }

    public void setForeverKid(boolean foreverKid) {
        this.foreverKid = foreverKid;
    }

    @NonNull
    public String getBehavior() {
        return behavior;
    }

    public void setBehavior(@NonNull String behavior) {
        this.behavior = behavior;
    }

    @NonNull
    public String getAiProfileFile() {
        return aiProfileFile;
    }

    public void setAiProfileFile(@NonNull String aiProfileFile) {
        this.aiProfileFile = aiProfileFile;
    }
}
