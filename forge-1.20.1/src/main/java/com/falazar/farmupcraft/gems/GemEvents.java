package com.falazar.farmupcraft.gems;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.server.ServerLifecycleHooks;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import java.util.Map;
import java.util.WeakHashMap;

@Mod.EventBusSubscriber(modid = "farmupcraft")
public final class GemEvents {
    private static final String VERSION = "gems_generation_version";
    private static final String CHEST = "gems_ruin_note_version";
    private GemEvents() {}
    private static final Map<LevelChunk, Marker> MARKERS = java.util.Collections.synchronizedMap(new WeakHashMap<>());
    private static final class Marker implements ICapabilitySerializable<CompoundTag> {
        private int version;
        @Override public <T> LazyOptional<T> getCapability(Capability<T> capability, Direction side) { return LazyOptional.empty(); }
        @Override public CompoundTag serializeNBT() { var tag = new CompoundTag(); tag.putInt(VERSION, version); return tag; }
        @Override public void deserializeNBT(CompoundTag tag) { version = tag.getInt(VERSION); }
    }
    @SubscribeEvent public static void attach(AttachCapabilitiesEvent<LevelChunk> event) {
        var marker = new Marker();
        MARKERS.put(event.getObject(), marker);
        event.addCapability(new ResourceLocation("farmupcraft", VERSION), marker);
    }
    public static int version(LevelChunk chunk) { return marker(chunk).version; }
    public static void complete(LevelChunk chunk) { setVersion(chunk, GemRules.VERSION); }
    public static void setVersion(LevelChunk chunk, int version) { marker(chunk).version = version; }
    private static Marker marker(LevelChunk chunk) {
        var marker = MARKERS.get(chunk);
        if (marker == null) throw new IllegalStateException("Missing serializable gem chunk marker " + chunk.getPos());
        return marker;
    }
    public static boolean chestRolled(BlockEntity chest) { return chest.getPersistentData().getInt(CHEST) > 0; }
    public static void markChest(BlockEntity chest) { chest.getPersistentData().putInt(CHEST, 1); chest.setChanged(); }
    @SubscribeEvent public static void levelLoaded(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level) GemWorld.initialize(level);
    }
    @SubscribeEvent public static void started(ServerStartedEvent event) { GemWorld.initialize(event.getServer().overworld()); }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { GemWorld.stop(event.getServer()); }
    @SubscribeEvent public static void loaded(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && event.getChunk() instanceof LevelChunk chunk)
            level.getServer().execute(() -> GemWorld.enqueue(level, chunk, event.isNewChunk()));
    }
    @SubscribeEvent public static void unloaded(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level && event.getChunk() instanceof LevelChunk chunk)
            level.getServer().execute(() -> GemWorld.unload(level, chunk));
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (event.phase == TickEvent.Phase.END && server != null) GemWorld.tick(server.overworld());
    }
}
