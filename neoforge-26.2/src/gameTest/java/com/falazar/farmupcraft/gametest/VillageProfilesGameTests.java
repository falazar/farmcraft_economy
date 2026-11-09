package com.falazar.farmupcraft.gametest;

import com.falazar.farmupcraft.command.NpcCommand;
import com.falazar.farmupcraft.data.ChunkData;
import com.falazar.farmupcraft.data.PlayerData;
import com.falazar.farmupcraft.data.VillageData;
import com.falazar.farmupcraft.events.ModEvents;
import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import com.falazar.farmupcraft.util.NpcDataLoader;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@EventBusSubscriber(modid = FarmUpCraftNeoForge.MOD_ID)
public final class VillageProfilesGameTests {
    private VillageProfilesGameTests() {}

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, functions -> functions.register(
                FarmUpCraftNeoForge.prefix("village_profiles"), VillageProfilesGameTests::verify));
    }

    private static void verify(GameTestHelper helper) {
        try (var fixture = new ResourcePlotFixture(helper)) {
            var chunks = ModEvents.getChunkDataDatabase(fixture.level);
            var villages = ModEvents.getVillageDatabase(fixture.level);
            var players = ModEvents.getPlayerDatabase(fixture.level);
            UUID villageId = UUID.randomUUID();
            var village = new VillageData(villageId, "Profile Test", fixture.player.chunkPosition(),
                    1, List.of(fixture.player.chunkPosition()), true, 250, "", "cow", "",
                    fixture.player.getUUID().toString(), List.of(fixture.player.getUUID()));
            villages.putData(villageId, village);
            players.putData(fixture.player.getUUID(),
                    new PlayerData(fixture.player.getId(), fixture.player.getUUID(), villageId, 10000));
            chunks.putData(fixture.key, new ChunkData("village", -1, villageId));
            var outside = fixture.pos.offset(32, 0, 0);
            long outsideKey = ChunkPos.pack(outside);
            var previousOutside = chunks.getData(outsideKey);
            chunks.putData(outsideKey, new ChunkData("village", -1, UUID.randomUUID()));
            var entities = new ArrayList<Entity>();
            java.nio.file.Path savedProfile = null;
            try {
                for (int i = 0; i < 3; i++) {
                    var villager = EntityTypes.VILLAGER.create(fixture.level, EntitySpawnReason.TRIGGERED);
                    helper.assertTrue(villager != null, "Create profile fixture villager");
                    entities.add(villager);
                    var pos = i == 2 ? outside : fixture.pos;
                    villager.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
                    villager.setCustomName(Component.literal("ProfileTest" + i));
                    villager.setNoAi(true);
                    helper.assertTrue(fixture.level.addFreshEntity(villager), "Add profile fixture villager");
                    if (i == 1) {
                        savedProfile = net.neoforged.fml.loading.FMLPaths.GAMEDIR.get().resolve("npcData")
                                .resolve(villager.getName().getString() + "-" + villager.getUUID() + ".json");
                        helper.assertTrue(!Files.exists(savedProfile), "Test profile must be new");
                        helper.assertTrue(NpcDataLoader.writeProfile(villager.getName().getString(),
                                villager.getUUID(), "Existing description", "Existing personality", village.getName()),
                                "Write existing profile fixture");
                    }
                }
                var source = fixture.player.createCommandSourceStack();
                helper.assertValueEqual(NpcCommand.initVillageProfiles(source, village), 1,
                        "Only missing profiles in this village must queue");
                helper.assertValueEqual(NpcCommand.initVillageProfiles(source, village), 0,
                        "Repeated automatic scans must skip scheduled requests");
                helper.assertTrue(Files.readString(savedProfile).contains("Existing personality"),
                        "Automatic scan must preserve existing profile");
                chunks.putData(outsideKey, new ChunkData("village", -1, villageId));
                village.setLevel(2);
                helper.assertValueEqual(NpcCommand.initVillageProfiles(source, village), 1,
                        "Expanded village scan must pick up newly owned villagers without duplicating old jobs");
                helper.assertValueEqual(NpcCommand.initVillageProfiles(source, village), 0,
                        "Queued expansion profiles must also be deduplicated");
                helper.assertTrue(!NpcDataLoader.hasProfile(fixture.level, entities.getFirst().getName().getString(),
                        entities.getFirst().getUUID()), "Test must not call AI or generate files before its delay");
            } finally {
                NpcCommand.shutdown(fixture.level.getServer());
                entities.forEach(Entity::discard);
                if (savedProfile != null) Files.deleteIfExists(savedProfile);
                villages.removeDataBatch(List.of(villageId));
                if (previousOutside == null) chunks.removeDataBatch(List.of(outsideKey));
                else chunks.putData(outsideKey, previousOutside);
            }
        } catch (IOException error) {
            throw new IllegalStateException("Cannot verify profile preservation or clean up fixture", error);
        }
        helper.succeed();
    }
}
