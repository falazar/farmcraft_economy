package com.falazar.farmupcraft.gems;

import com.mojang.serialization.Codec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public final class GemWorldState extends SavedData {
    private static final SavedDataType<GemWorldState> TYPE = new SavedDataType<>(
            Identifier.parse("farmupcraft:gem_world_state"), () -> new GemWorldState(0),
            Codec.INT.xmap(GemWorldState::new, data -> data.version));
    private int version;
    private GemWorldState(int version) { this.version = version; }
    public static GemWorldState get(ServerLevel level) { return level.getDataStorage().computeIfAbsent(TYPE); }
    public int version() { return version; }
    public void initialized() { version = GemRules.VERSION; setDirty(); }
}
