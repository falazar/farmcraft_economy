package com.falazar.farmupcraft.gems;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public final class GemWorldState extends SavedData {
    private int version;
    public static GemWorldState get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(GemWorldState::load, GemWorldState::new,
                "farmupcraft_gem_world_state");
    }
    private static GemWorldState load(CompoundTag tag) {
        var data = new GemWorldState();
        data.version = tag.getInt("generation_version");
        return data;
    }
    @Override public CompoundTag save(CompoundTag tag) {
        tag.putInt("generation_version", version);
        return tag;
    }
    public int version() { return version; }
    public void initialized() { version = GemRules.VERSION; setDirty(); }
}
