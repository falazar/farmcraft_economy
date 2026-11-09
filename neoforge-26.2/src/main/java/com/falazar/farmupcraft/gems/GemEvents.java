package com.falazar.farmupcraft.gems;

import com.mojang.serialization.Codec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import java.util.function.Supplier;

@EventBusSubscriber(modid = "farmupcraft")
public final class GemEvents {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, "farmupcraft");
    private static final Supplier<AttachmentType<Integer>> VERSION = ATTACHMENTS.register("gems_generation_version",
            () -> AttachmentType.builder(() -> 0).serialize(Codec.INT.fieldOf("version")).build());
    private static final Supplier<AttachmentType<Integer>> CHEST = ATTACHMENTS.register("gems_ruin_note_version",
            () -> AttachmentType.builder(() -> 0).serialize(Codec.INT.fieldOf("version")).build());
    private GemEvents() {}
    public static void register(net.neoforged.bus.api.IEventBus bus) { ATTACHMENTS.register(bus); }
    public static int version(LevelChunk chunk) { return chunk.getData(VERSION); }
    public static void complete(LevelChunk chunk) { setVersion(chunk, GemRules.VERSION); }
    public static void setVersion(LevelChunk chunk, int version) { chunk.setData(VERSION, version); }
    public static boolean chestRolled(BlockEntity chest) { return chest.hasData(CHEST.get()) && chest.getData(CHEST) > 0; }
    public static void markChest(BlockEntity chest) { chest.setData(CHEST, 1); chest.setChanged(); }
    @SubscribeEvent public static void levelLoaded(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level) GemWorld.initialize(level);
    }
    @SubscribeEvent public static void started(ServerStartedEvent event) {
        if (event.getServer().overworld() != null) GemWorld.initialize(event.getServer().overworld());
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { GemWorld.stop(event.getServer()); }
    @SubscribeEvent public static void loaded(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level) {
            var chunk = event.getChunk();
            level.getServer().execute(() -> GemWorld.enqueue(level, chunk, event.isNewChunk()));
        }
    }
    @SubscribeEvent public static void unloaded(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level && event.getChunk() instanceof LevelChunk chunk)
            level.getServer().execute(() -> GemWorld.unload(level, chunk));
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if (event.getServer().overworld() != null) GemWorld.tick(event.getServer().overworld());
    }
}
