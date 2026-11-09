package com.falazar.farmupcraft.gametest;

import com.falazar.farmupcraft.data.ChunkData;
import com.falazar.farmupcraft.data.VillageData;
import com.falazar.farmupcraft.data.PlayerData;
import com.falazar.farmupcraft.events.ModEvents;
import com.falazar.farmupcraft.events.VillageRouteSurveys;
import com.falazar.farmupcraft.events.VillageRoads;
import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import com.falazar.farmupcraft.util.RouteSurvey;
import com.falazar.farmupcraft.util.RouteSurveyWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.structures.BuriedTreasurePieces;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.*;
import static com.falazar.farmupcraft.util.RouteSurvey.*;

@EventBusSubscriber(modid = FarmUpCraftNeoForge.MOD_ID)
public final class RouteSurveyGameTests {
    private RouteSurveyGameTests() {}
    @SubscribeEvent public static void register(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, functions -> functions.register(
                FarmUpCraftNeoForge.prefix("route_surveys"), RouteSurveyGameTests::verify));
    }

    private static Result scan(GameTestHelper helper, RouteSurveyWorld source, Kind kind) {
        var scan = new RouteSurvey(source, kind, "fixture");
        int ticks = 0;
        while (!scan.advance(32)) helper.assertTrue(++ticks < 20000, "Survey must finish within its work budget");
        return scan.result();
    }

    private static void verify(GameTestHelper helper) {
        try (var fixture = new ResourcePlotFixture(helper)) {
            var level = fixture.level;
            var villages = ModEvents.getVillageDatabase(level);
            var plots = ModEvents.getChunkDataDatabase(level);
            var surveys = ModEvents.getRouteSurveyDatabase(level);
            UUID id = UUID.randomUUID(), otherId = UUID.randomUUID();
            UUID offlineId = UUID.randomUUID();
            var players = ModEvents.getPlayerDatabase(level);
            var discoveries = ModEvents.getRouteDiscoveryDatabase(level);
            int x0 = fixture.player.chunkPosition().getMinBlockX();
            int z0 = fixture.player.chunkPosition().getMinBlockZ() + 8;
            int y = fixture.pos.getY() + 8;
            ChunkPos own = new ChunkPos(x0 >> 4, z0 >> 4);
            ChunkPos destination = new ChunkPos((x0 + 80) >> 4, z0 >> 4);
            var village = new VillageData(id, "RoutesTest", own, 1, List.of(own), true,
                    0, "", "", "", "", List.of());
            var other = new VillageData(otherId, "RouteDestination", destination, 1,
                    List.of(destination, new ChunkPos((x0 + 96) >> 4, z0 >> 4)),
                    true, 0, "", "", "", "", List.of());
            Map<BlockPos, BlockState> originals = new LinkedHashMap<>();
            Map<Long, ChunkData> oldPlots = new HashMap<>();
            long ownKey = RouteSurveyWorld.chunkKey(x0, z0), otherKey = RouteSurveyWorld.chunkKey(x0 + 80, z0);
            oldPlots.put(ownKey, plots.getData(ownKey));
            oldPlots.put(otherKey, plots.getData(otherKey));
            long greenKey = RouteSurveyWorld.chunkKey(x0 + 104, z0);
            oldPlots.put(greenKey, plots.getData(greenKey));
            var structureRegistry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
            var surface = structureRegistry.getValue(Identifier.parse("minecraft:desert_pyramid"));
            var underground = structureRegistry.getValue(Identifier.parse("minecraft:mineshaft"));
            var ruinChunk = level.getChunk((x0 + 64) >> 4, z0 >> 4);
            var previousStarts = new HashMap<>(ruinChunk.getAllStarts());
            try {
                for (int dx = -16; dx <= 112; dx += 16) for (int dz : new int[]{-16, 0, 16})
                    level.getChunk((x0 + dx) >> 4, (z0 + dz) >> 4);
                villages.putData(id, village);
                villages.putData(otherId, other);
                players.putData(fixture.player.getUUID(), new PlayerData(fixture.player.getId(), fixture.player.getUUID(), id, 0));
                players.putData(offlineId, new PlayerData(-1, offlineId, id, 0));
                plots.putData(ownKey, new ChunkData("village", -1, id));
                plots.putData(otherKey, new ChunkData("house", -1, otherId));
                plots.putData(greenKey, new ChunkData("village", -1, otherId));
                var greenContacts = new RouteSurveyWorld(level, village, Kind.ROAD).claims(new Point(x0 + 104, y, z0));
                helper.assertTrue(greenContacts.contains("village:" + otherId)
                        && greenContacts.stream().noneMatch(c -> c.startsWith("plot:")),
                        "Green village territory connects villages but must not be mistaken for a blue purchased plot");
                for (int x = 0; x <= 100; x++) {
                    for (int dz = -1; dz <= 4; dz++) {
                        for (int dy = 0; dy <= 3; dy++) {
                            BlockPos pos = new BlockPos(x0 + x, y + dy, z0 + dz);
                            originals.put(pos, level.getBlockState(pos));
                            level.setBlock(pos, dy == 0 && dz <= 1
                                    ? Blocks.STONE_BRICKS.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
                        }
                    }
                }
                Result road = scan(helper, new RouteSurveyWorld(level, village, Kind.ROAD), Kind.ROAD);
                helper.assertTrue(Math.abs(road.length() - 100) < .01,
                        "A three-wide road is 100 blocks long, not 303 paving blocks: " + road.length());
                helper.assertTrue(road.segments().stream().flatMap(s -> s.claims().stream())
                        .anyMatch(c -> c.equals("village:" + otherId)), "Road must record another village claimed area");
                helper.assertTrue(road.segments().stream().flatMap(s -> s.claims().stream())
                        .anyMatch(c -> c.startsWith("plot:" + otherId + "/")), "Road must record claimed blue plot contact");
                for (int dz = -1; dz <= 1; dz++) level.setBlock(new BlockPos(x0 + 40, y, z0 + dz),
                        Blocks.AIR.defaultBlockState(), 2);
                helper.assertTrue(Math.abs(scan(helper, new RouteSurveyWorld(level, village, Kind.ROAD), Kind.ROAD)
                        .length() - 100) < .01, "One-block road gap must be crossed without losing width-independent length");
                for (int dz = -1; dz <= 1; dz++) level.setBlock(new BlockPos(x0 + 41, y, z0 + dz),
                        Blocks.AIR.defaultBlockState(), 2);
                helper.assertTrue(scan(helper, new RouteSurveyWorld(level, village, Kind.ROAD), Kind.ROAD).length() == 0,
                        "Two-block gap must stop this under-48-block village-side stub");
                for (int x : new int[]{40, 41}) for (int dz = -1; dz <= 1; dz++)
                    level.setBlock(new BlockPos(x0 + x, y, z0 + dz), Blocks.OAK_SLAB.defaultBlockState(), 2);
                helper.assertTrue(scan(helper, new RouteSurveyWorld(level, village, Kind.ROAD), Kind.ROAD).length() >= 99,
                        "Wood slabs must reconnect the road");
                int bridgeDecks = 0, steppingStones = 0;
                for (var entry : net.minecraft.core.registries.BuiltInRegistries.BLOCK) {
                    if (entry instanceof SlabBlock || entry instanceof StairBlock)
                        helper.assertTrue(RouteSurveyWorld.roadMaterial(entry.defaultBlockState()),
                                "Every registered slab/stair must qualify as a road material");
                    var blockId = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(entry);
                    if (blockId.getNamespace().equals("refurbished_furniture") && blockId.getPath().endsWith("_stepping_stones")
                            || blockId.getNamespace().equals("mcwbridges") && RouteSurveyWorld.roadMaterial(entry.defaultBlockState())) {
                        if (blockId.getNamespace().equals("mcwbridges")) bridgeDecks++;
                        else steppingStones++;
                        level.setBlock(new BlockPos(x0 + 30, y, z0), entry.defaultBlockState(), 2);
                        var deckSource = new RouteSurveyWorld(level, village, Kind.ROAD);
                        helper.assertTrue(deckSource.neighbors(new Point(x0 + 29, y, z0)).stream()
                                .anyMatch(link -> link.point().equals(new Point(x0 + 30, y, z0))),
                                "Actual optional-mod bridge/stepping-stone deck must be traversable: " + blockId);
                    }
                    if (blockId.getNamespace().equals("mcwbridges")
                            && (blockId.getPath().endsWith("_pier") || blockId.getPath().equals("bridge_torch")
                                || blockId.getPath().equals("bridge_lantern")))
                        helper.assertTrue(!RouteSurveyWorld.roadMaterial(entry.defaultBlockState()),
                                "Bridge supports and lights must not count as road decking");
                }
                com.mojang.logging.LogUtils.getLogger().info(
                        "Route materials verified: {} Macaw bridge decks, {} Refurbished stepping-stone variants",
                        bridgeDecks, steppingStones);
                level.setBlock(new BlockPos(x0 + 30, y, z0), Blocks.STONE_BRICKS.defaultBlockState(), 2);
                var piece = new BuriedTreasurePieces.BuriedTreasurePiece(new BlockPos(x0 + 64, y + 1, z0));
                ruinChunk.setStartForStructure(surface, new StructureStart(surface, ruinChunk.getPos(), 0,
                        new PiecesContainer(List.of(piece))));
                ruinChunk.setStartForStructure(underground, new StructureStart(underground, ruinChunk.getPos(), 0,
                        new PiecesContainer(List.of(piece))));
                var ruins = new RouteSurveyWorld(level, village, Kind.ROAD);
                Set<String> contacts = ruins.ruins(new Point(x0 + 64, y, z0));
                helper.assertTrue(contacts.size() == 1 && contacts.iterator().next().startsWith("minecraft:desert_pyramid@"),
                        "Surface structure pieces count; underground structures never count even with the same test bounds");
                helper.assertTrue(ruins.ruins(new Point(x0 + 64, y - 20, z0)).isEmpty(),
                        "Sharing X/Z with a ruin must not count without matching its entrance/piece height");
                for (int x = 0; x <= 100; x++) {
                    level.setBlock(new BlockPos(x0 + x, y, z0 + 3), Blocks.STONE.defaultBlockState(), 2);
                    level.setBlock(new BlockPos(x0 + x, y + 1, z0 + 3),
                            Blocks.RAIL.defaultBlockState().setValue(RailBlock.SHAPE, RailShape.EAST_WEST), 2);
                }
                helper.assertTrue(scan(helper, new RouteSurveyWorld(level, village, Kind.RAIL), Kind.RAIL).length() == 0,
                        "Rails must not be discovered without a trainstation plot");
                plots.putData(ownKey, new ChunkData("trainstation", -1, id));
                Result rails = scan(helper, new RouteSurveyWorld(level, village, Kind.RAIL), Kind.RAIL);
                helper.assertTrue(Math.abs(rails.length() - 100) < .01, "Trainstation must discover connected railway");
                level.setBlock(new BlockPos(x0 + 40, y + 1, z0 + 3), Blocks.AIR.defaultBlockState(), 2);
                helper.assertTrue(scan(helper, new RouteSurveyWorld(level, village, Kind.RAIL), Kind.RAIL).length() == 0,
                        "Railways cannot bridge a missing track");
                Saved saved = Saved.empty().begin(1000).finish(road, 1100);
                var encoded = VillageRouteSurveys.CODEC.encodeStart(NbtOps.INSTANCE, saved).getOrThrow();
                helper.assertValueEqual(VillageRouteSurveys.CODEC.parse(NbtOps.INSTANCE, encoded).getOrThrow(), saved,
                        "Actual NBT codec must preserve segments, endpoints, contacts and datetimes");
                var manyPoints = new ArrayList<Point>();
                for (int i = 0; i < 10000; i++) manyPoints.add(new Point(i, y, 0));
                Saved large = Saved.empty().begin(2000).finish(new Result(List.of(
                        new Segment("large", manyPoints, 0, 9999, Set.of(), Set.of())), Set.of()), 2100);
                var serialized = VillageRouteSurveys.CODEC.encodeStart(NbtOps.INSTANCE, large).getOrThrow();
                helper.assertTrue(serialized instanceof net.minecraft.nbt.ListTag tags && tags.size() > 1,
                        "Long saved routes must be split into NBT-safe strings");
                var nbt = new net.minecraft.nbt.CompoundTag();
                nbt.put("survey", serialized);
                try (var bytes = new java.io.ByteArrayOutputStream()) {
                    net.minecraft.nbt.NbtIo.writeCompressed(nbt, bytes);
                    helper.assertTrue(bytes.size() > 0, "Large route must actually write as binary NBT");
                } catch (java.io.IOException error) {
                    throw new IllegalStateException("Cannot save large route survey NBT", error);
                }
                surveys.putData(VillageRouteSurveys.key(id, Kind.ROAD), saved);
                helper.assertValueEqual(VillageRouteSurveys.show(fixture.player.createCommandSourceStack(), village.getName()), 1,
                        "Saved route report command must work");
                var messages = new ArrayList<String>();
                var recipient = new net.neoforged.neoforge.common.util.FakePlayer(level, fixture.player.getGameProfile()) {
                    @Override public void sendSystemMessage(Component message) { messages.add(message.getString()); }
                };
                var dispatcher = level.getServer().getCommands().getDispatcher();
                try {
                    helper.assertValueEqual(dispatcher.execute("village roads", recipient.createCommandSourceStack()), 1,
                            "Roads command must default to the player's village");
                    helper.assertTrue(messages.stream().anyMatch(m -> m.contains("Start ") && m.contains("blocks | dir ")
                            && m.contains("connections:")), "Road list must include start, measured length, direction and connections");
                    helper.assertTrue(messages.stream().anyMatch(m -> m.contains("RouteDestination")),
                            "Connections must resolve the destination village name");
                    messages.clear();
                    helper.assertValueEqual(dispatcher.execute("village roads \"RoutesTest\" 999", recipient.createCommandSourceStack()),
                            0, "Invalid page must be reported");
                    helper.assertTrue(messages.stream().anyMatch(m -> m.contains("Page must be")), "Invalid pages must explain the allowed range");
                    helper.assertValueEqual(dispatcher.execute("village railroads", recipient.createCommandSourceStack()), 1,
                            "Railroad display command must be registered");
                } catch (com.mojang.brigadier.exceptions.CommandSyntaxException error) {
                    throw new IllegalStateException("Road listing command failed", error);
                }
                String noticeKey = VillageRouteSurveys.key(id, Kind.ROAD);
                VillageRoads.discover(level, id, Kind.ROAD, saved);
                var ledger = discoveries.getData(noticeKey);
                helper.assertTrue(ledger.pending().get(fixture.player.getUUID().toString()).size() == 1
                        && ledger.pending().get(offlineId.toString()).size() == 1,
                        "First discovery must persist an inbox notice for every village member, including offline players");
                var encodedInbox = VillageRoads.CODEC.encodeStart(NbtOps.INSTANCE, ledger).getOrThrow();
                discoveries.putData(noticeKey, VillageRoads.CODEC.parse(NbtOps.INSTANCE, encodedInbox).getOrThrow());
                messages.clear();
                VillageRoads.deliver(recipient);
                helper.assertTrue(messages.size() == 1 && messages.getFirst().contains("New road discovery"),
                        "Login delivery must show saved road discovery");
                VillageRoads.deliver(recipient);
                helper.assertTrue(messages.size() == 1, "Delivered notice must not repeat on another login");
                VillageRoads.clear();
                VillageRoads.discover(level, id, Kind.ROAD, saved);
                helper.assertTrue(discoveries.getData(noticeKey).pending().get(offlineId.toString()).size() == 1
                        && !discoveries.getData(noticeKey).pending().containsKey(fixture.player.getUUID().toString()),
                        "Reload and unchanged daily scan must not announce the same road again");
                var offlineRecipient = new net.neoforged.neoforge.common.util.FakePlayer(level,
                        new com.mojang.authlib.GameProfile(offlineId, "offline-route-test")) {
                    @Override public void sendSystemMessage(Component message) { messages.add(message.getString()); }
                };
                VillageRoads.deliver(offlineRecipient);
                helper.assertTrue(messages.size() == 2 && discoveries.getData(noticeKey).pending().isEmpty(),
                        "Offline member must receive and consume the queued notice on next login");
                discoveries.removeDataBatch(List.of(noticeKey));
                var railNoticeKey = VillageRouteSurveys.key(id, Kind.RAIL);
                VillageRoads.discover(level, id, Kind.RAIL, Saved.empty().begin(1000).finish(rails, 1100));
                helper.assertTrue(discoveries.getData(railNoticeKey).pending().get(offlineId.toString()).getFirst().contains("railway"),
                        "Railways must announce independently from road discoveries");
                var unloaded = new Point(1_000_000, y, 1_000_000);
                var before = level.getChunkSource().getChunkNow(unloaded.x() >> 4, unloaded.z() >> 4);
                var source = new RouteSurveyWorld(level, village, Kind.ROAD);
                helper.assertTrue(before == null && source.neighbors(unloaded).isEmpty()
                        && level.getChunkSource().getChunkNow(unloaded.x() >> 4, unloaded.z() >> 4) == null
                        && source.issues().contains("unloaded terrain"), "Survey must report unloaded terrain without loading it");
                villages.removeDataBatch(List.of(otherId));
                surveys.removeDataBatch(List.of(VillageRouteSurveys.key(id, Kind.ROAD), VillageRouteSurveys.key(id, Kind.RAIL)));
                discoveries.removeDataBatch(List.of(VillageRouteSurveys.key(id, Kind.ROAD), VillageRouteSurveys.key(id, Kind.RAIL)));
                players.removeDataBatch(List.of(offlineId));
                VillageRouteSurveys.stop(level.getServer());
                for (int i = 0; i < 200; i++) VillageRouteSurveys.tick(level.getServer());
                String roadKey = VillageRouteSurveys.key(id, Kind.ROAD);
                var started = surveys.getData(roadKey);
                helper.assertTrue(started != null && started.lastChecked() > 0 && started.status().startsWith("In progress"),
                        "Server ticks must automatically start a due survey and persist its attempt before scanning");
                var savedGate = VillageRouteSurveys.CODEC.encodeStart(NbtOps.INSTANCE, started).getOrThrow();
                surveys.putData(roadKey, VillageRouteSurveys.CODEC.parse(NbtOps.INSTANCE, savedGate).getOrThrow());
                VillageRouteSurveys.stop(level.getServer());
                for (int i = 0; i < 200; i++) VillageRouteSurveys.tick(level.getServer());
                helper.assertTrue(surveys.getData(roadKey).lastChecked() == started.lastChecked(),
                        "Restart/reload must not reset the road survey's 24-hour gate");
                helper.assertTrue(surveys.getData(VillageRouteSurveys.key(id, Kind.RAIL)) != null,
                        "Rail survey has its own persistent daily gate");
                String railKey = VillageRouteSurveys.key(id, Kind.RAIL);
                for (int i = 0; i < 20000 && surveys.getData(railKey).lastSuccess() == 0; i++) {
                    VillageRouteSurveys.tick(level.getServer());
                    if (i % 64 == 0) java.util.concurrent.locks.LockSupport.parkNanos(1_000_000L);
                }
                helper.assertTrue(surveys.getData(railKey).lastSuccess() > 0
                        && surveys.getData(railKey).status().equals("Complete"),
                        "Automatic scheduler must finish background measurement and persist its result: "
                                + surveys.getData(railKey).status());
                VillageRouteSurveys.stop(level.getServer());
                for (int i = 0; i < 200; i++) VillageRouteSurveys.tick(level.getServer());
                helper.assertTrue(surveys.getData(roadKey).lastChecked() == started.lastChecked(),
                        "Repeated scheduler checks must not bypass either saved daily gate");
            } finally {
                VillageRouteSurveys.stop(level.getServer());
                originals.forEach((pos, state) -> level.setBlock(pos, state, 2));
                ruinChunk.setAllStarts(previousStarts);
                villages.removeDataBatch(List.of(id, otherId));
                discoveries.removeDataBatch(List.of(VillageRouteSurveys.key(id, Kind.ROAD), VillageRouteSurveys.key(id, Kind.RAIL)));
                players.removeDataBatch(List.of(offlineId));
                for (var old : oldPlots.entrySet()) {
                    if (old.getValue() == null) plots.removeDataBatch(List.of(old.getKey()));
                    else plots.putData(old.getKey(), old.getValue());
                }
                surveys.removeDataBatch(List.of(VillageRouteSurveys.key(id, Kind.ROAD), VillageRouteSurveys.key(id, Kind.RAIL)));
            }
        }
        helper.succeed();
    }
}
