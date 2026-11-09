package com.falazar.farmupcraft.gems;

import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.server.ServerStartedEvent;

@Mod.EventBusSubscriber(modid = "farmupcraft")
public final class GemAcceptance {
    @SubscribeEvent public static void started(ServerStartedEvent event) {
        if (!Boolean.getBoolean("farmupcraft.gems.acceptance")) return;
        try {
            var level = event.getServer().overworld();
            var rules = GemWorld.rules(level);
            if (rules.gems().size() != 5) throw new AssertionError("Five persisted gem types required");
            var chunk = level.getChunk(0, 0);
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
                level.getChunk(GemPlatform.chunkX(chunk) + dx, GemPlatform.chunkZ(chunk) + dz);
            var marker = event.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                    .resolve("farmupcraft").resolve("gems").resolve("acceptance-marker.json");
            if (java.nio.file.Files.exists(marker) && GemEvents.version(chunk) != 1)
                throw new AssertionError("Forge capability version must survive native restart");
            GemWorld.enqueue(level, chunk, false);
            for (int i = 0; i < 100 && GemEvents.version(chunk) == 0; i++) GemWorld.tick(level);
            if (GemEvents.version(chunk) != 1) throw new AssertionError("Serializable chunk capability must complete");
            GemEvents.setVersion(chunk, 0);
            GemWorld.enqueue(level, chunk, false);
            GemWorld.tick(level, 1, 1);
            if (GemEvents.version(chunk) != 0) throw new AssertionError("Interrupted pass marked prematurely");
            GemWorld.unload(level, chunk);
            GemWorld.enqueue(level, chunk, false);
            for (int i = 0; i < 100 && GemEvents.version(chunk) == 0; i++) GemWorld.tick(level);
            if (GemEvents.version(chunk) != 1) throw new AssertionError("Chunk retry must complete");
            var saved = net.minecraft.world.level.chunk.storage.ChunkSerializer.write(level, chunk);
            if (saved.getCompound("ForgeCaps").getCompound("farmupcraft:gems_generation_version")
                    .getInt("gems_generation_version") != 1)
                throw new AssertionError("Actual serialized chunk NBT must contain its completion version");
            var newTerrain = level.getChunk(1000, 1000);
            GemWorld.enqueue(level, newTerrain, true);
            if (GemEvents.version(newTerrain) != 1)
                throw new AssertionError("Native new terrain must complete its owned decoration pass");
            GemFiles.write(marker, java.util.Map.of("version", 1));
            GemPlacementAcceptance.borderAndRetry(level, 0, 0);
            System.out.println("GEM_FORGE_ACCEPTANCE_PASS native_passes=" + GemWorld.nativePasses(level));
        } catch (Throwable error) {
            error.printStackTrace();
            System.out.println("GEM_FORGE_ACCEPTANCE_FAIL");
        } finally { event.getServer().halt(false); }
    }
}
