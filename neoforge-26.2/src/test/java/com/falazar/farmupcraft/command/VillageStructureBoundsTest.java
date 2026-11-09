package com.falazar.farmupcraft.command;

import com.falazar.farmupcraft.data.ChunkData;
import com.falazar.farmupcraft.data.GameStructureData;
import com.falazar.farmupcraft.data.VillageData;
import com.falazar.farmupcraft.database.DataBase;
import com.falazar.farmupcraft.database.serializers.CodecDataSerializer;
import com.falazar.farmupcraft.database.serializers.LongDataSerializer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class VillageStructureBoundsTest {
    @Test
    void villageListIncludesUnvisitedSurfaceIglooButNotSecretOrForeignStructures(MinecraftServer server) {
        UUID villageId = UUID.randomUUID();
        ChunkPos chunk = new ChunkPos(39, -21);
        var village = new VillageData(villageId, "Home", chunk, 1, List.of(chunk), true,
                0, "2000-01-01", "", "2000-01-01", "Founder", new ArrayList<>());
        try (var chunks = new DataBase<Long, ChunkData>("structure-bounds-chunks", new LongDataSerializer(),
                    new CodecDataSerializer<>(ChunkData.CODEC), false, 0, false, server.registryAccess());
             var structures = new DataBase<Long, GameStructureData>("structure-bounds-records", new LongDataSerializer(),
                    new CodecDataSerializer<>(GameStructureData.CODEC), false, 0, false, server.registryAccess())) {
            chunks.putData(chunk.pack(), new ChunkData("village", 1, villageId));
            chunks.putData(ChunkPos.pack(40, -21), new ChunkData("village", 1, UUID.randomUUID()));
            var igloo = new GameStructureData(1L, "igloo grassy", new BlockPos(624, 57, -321), "igloo",
                    false, false, new BlockPos(621, 45, -326), new BlockPos(627, 69, -317));
            var secret = new GameStructureData(2L, "Secret", igloo.getCenterPos(), "dungeon",
                    false, false, new BlockPos(621, 45, -326), new BlockPos(627, 59, -317));
            var foreign = new GameStructureData(3L, "Foreign", new BlockPos(640, 70, -321), "igloo");
            for (var data : List.of(igloo, secret, foreign)) structures.putData(data.getId(), data);
            assertEquals(List.of(1L), VillageCommand.getVillageStructuresList(village, structures, chunks)
                    .stream().map(java.util.Map.Entry::getKey).toList());
            assertFalse(igloo.wasVisited());
            assertFalse(igloo.isOnClaimedPlot(), "Listing alone must not award unvisited structure income");
            secret.markAsVisited();
            assertEquals(2, VillageCommand.getVillageStructuresList(village, structures, chunks).size());
        }
    }
}
