package com.falazar.farmupcraft.gametest;

import com.falazar.farmupcraft.data.ChunkData;
import com.falazar.farmupcraft.data.GameStructureData;
import com.falazar.farmupcraft.data.PlayerData;
import com.falazar.farmupcraft.data.VillageData;
import com.falazar.farmupcraft.data.WorldData;
import com.falazar.farmupcraft.database.DataBase;
import com.falazar.farmupcraft.events.ModEvents;
import com.falazar.farmupcraft.events.WorldScheduler;
import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = FarmUpCraftNeoForge.MOD_ID)
public final class CommandUpkeepRegressionGameTests {
    private static final String OLD_DATE = "2000-01-01";

    private CommandUpkeepRegressionGameTests() {
    }

    @SubscribeEvent
    public static void registerFunctions(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, functions -> {
            functions.register(id("regression_nonop_admin_rejection"),
                    CommandUpkeepRegressionGameTests::nonopAdminRejection);
            functions.register(id("regression_nonop_treasury_commands"),
                    CommandUpkeepRegressionGameTests::nonopTreasuryCommands);
            functions.register(id("regression_upkeep_tick_boundary"),
                    CommandUpkeepRegressionGameTests::upkeepTickBoundary);
            functions.register(id("regression_upkeep_structure_income"),
                    CommandUpkeepRegressionGameTests::upkeepStructureIncome);
        });
    }

    private static void nonopAdminRejection(GameTestHelper helper) {
        try (var fixture = new Fixture(helper)) {
            var village = fixture.village(2, 1_000);
            var player = fixture.onlineMember(village, 125);
            var source = player.createCommandSourceStack();
            assertNonop(helper, source);
            int playerRows = ModEvents.getPlayerDatabase(fixture.level).getSize();
            int villageRows = ModEvents.getVillageDatabase(fixture.level).getSize();
            for (String command : List.of(
                    "player admin givecoins 500",
                    "player givecoins " + player.getGameProfile().name() + " 500",
                    "village setlevel 10",
                    "village rundailyupkeep",
                    "village delete " + village.getName(),
                    "database query farmupcraft:player_database",
                    "farmupcraft-legacy apply unused-regression-copy")) {
                boolean rejected = false;
                try {
                    fixture.execute(player, command);
                } catch (CommandSyntaxException expected) {
                    rejected = true;
                }
                helper.assertTrue(rejected, "Non-op dispatcher must reject /" + command + " before its handler");
                helper.assertTrue(ModEvents.getVillageDatabase(fixture.level).getData(village.getUUID()) == village,
                        "Rejected administration must not delete or replace the village");
                helper.assertValueEqual(village.getLevel(), 2, "village level after rejected " + command);
                helper.assertValueEqual(village.getCoins(), 1_000, "treasury after rejected " + command);
                helper.assertValueEqual(village.getLastRanDaily(), OLD_DATE, "upkeep date after rejected " + command);
                helper.assertValueEqual(fixture.playerData(player).getCoins(), 125,
                        "player coins after rejected " + command);
                helper.assertValueEqual(ModEvents.getPlayerDatabase(fixture.level).getSize(), playerRows,
                        "player row count after rejected " + command);
                helper.assertValueEqual(ModEvents.getVillageDatabase(fixture.level).getSize(), villageRows,
                        "village row count after rejected " + command);
            }
        }
        helper.succeed();
    }

    private static void nonopTreasuryCommands(GameTestHelper helper) {
        try (var fixture = new Fixture(helper)) {
            var village = fixture.village(1, 1_000);
            var player = fixture.onlineMember(village, 125);
            assertNonop(helper, player.createCommandSourceStack());
            // These legacy handlers return zero even on success; verify their persisted effects, not their result code.
            fixture.execute(player, "player givevillagecoins 75");
            assertBalances(helper, fixture, player, village, 50, 1_075);
            fixture.execute(player, "player takevillagecoins 25");
            assertBalances(helper, fixture, player, village, 75, 1_050);
            fixture.execute(player, "player givevillagecoins 76");
            assertBalances(helper, fixture, player, village, 75, 1_050);
            fixture.execute(player, "player takevillagecoins 1051");
            assertBalances(helper, fixture, player, village, 75, 1_050);
            fixture.execute(player, "player border color teal");
            helper.assertValueEqual(fixture.playerData(player).getBorderColor(), "teal",
                    "ordinary non-op preference command must persist its argument");
        } catch (CommandSyntaxException failure) {
            throw new IllegalStateException("Ordinary non-op commands must reach their registered handlers", failure);
        }
        helper.succeed();
    }

    private static void upkeepTickBoundary(GameTestHelper helper) {
        try (var fixture = new Fixture(helper)) {
            var village = fixture.village(2, 50);
            var first = fixture.onlineMember(village, 125);
            var second = fixture.onlineMember(village, 75);
            fixture.chunk(village, "village", first);
            fixture.chunk(village, "farm", first);
            var offline = fixture.village(1, 600);
            fixture.offlineMember(offline);
            try (var clock = new SchedulerClock(fixture.level)) {
                clock.prime(71_998);
                clock.schedulerTick();
                helper.assertValueEqual(WorldScheduler.getTicksUntilNextRun(), 1, "ticks before hourly boundary");
                helper.assertValueEqual(village.getCoins(), 50, "no early upkeep charge");
                helper.assertValueEqual(village.getLastRanDaily(), OLD_DATE, "no early upkeep date");
                clock.schedulerTick();
                helper.assertValueEqual(WorldScheduler.getTicksUntilNextRun(), 72_000, "hourly timer reset");
                assertBalances(helper, fixture, first, village, 125, -165);
                helper.assertValueEqual(fixture.playerData(second).getCoins(), 75, "second member's personal balance");
                helper.assertValueEqual(village.getLastRanDaily(), LocalDate.now().toString(),
                        "online village daily marker after scheduled charge");
                helper.assertValueEqual(offline.getCoins(), 600, "offline-only village must not pay upkeep");
                helper.assertValueEqual(offline.getLastRanDaily(), OLD_DATE, "offline village must remain due");
                clock.prime(71_999);
                clock.schedulerTick();
                assertBalances(helper, fixture, first, village, 125, -165);
                helper.assertValueEqual(offline.getCoins(), 600, "offline village after a second hourly run");
            }
        }
        helper.succeed();
    }

    private static void upkeepStructureIncome(GameTestHelper helper) {
        try (var fixture = new Fixture(helper)) {
            var village = fixture.village(1, 100);
            var player = fixture.onlineMember(village, 40);
            var center = fixture.chunk(village, "village", player);
            var farm = fixture.chunk(village, "farm", player);
            var kitchen = fixture.chunk(village, "kitchen", player);
            fixture.structure(center, true);
            fixture.structure(farm, true);
            fixture.structure(kitchen, false);
            var other = fixture.village(1, 800);
            var foreign = fixture.chunk(other, "farm", player);
            // A stale claimed-chunk entry must not bill a plot or pay structure income owned by another village.
            village.addClaimedChunk(foreign);
            fixture.structure(foreign, true);
            try (var clock = new SchedulerClock(fixture.level)) {
                clock.prime(71_999);
                clock.schedulerTick();
                assertBalances(helper, fixture, player, village, 40, 170);
                helper.assertValueEqual(village.getLastRanDaily(), LocalDate.now().toString(),
                        "income-bearing village daily marker");
                helper.assertValueEqual(other.getCoins(), 800, "foreign offline treasury must not change");
                helper.assertValueEqual(other.getLastRanDaily(), OLD_DATE, "foreign offline date must not change");
                clock.prime(71_999);
                clock.schedulerTick();
                assertBalances(helper, fixture, player, village, 40, 170);
            }
        }
        helper.succeed();
    }

    private static void assertNonop(GameTestHelper helper, CommandSourceStack source) {
        helper.assertTrue(source.getEntity() instanceof ServerPlayer,
                "Commands must use the level-backed player's own source, not a console with substituted permissions");
        helper.assertTrue(!source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER),
                "The test ServerPlayer must actually be non-op");
        helper.assertTrue(source.getServer().getPlayerList().getPlayer(source.getEntity().getUUID()) == source.getEntity(),
                "The command player must be registered in the real server player list");
    }

    private static void assertBalances(GameTestHelper helper, Fixture fixture, ServerPlayer player,
                                       VillageData village, int playerCoins, int villageCoins) {
        helper.assertValueEqual(fixture.playerData(player).getCoins(), playerCoins, "persisted personal balance");
        helper.assertValueEqual(ModEvents.getVillageDatabase(fixture.level).getData(village.getUUID()).getCoins(),
                villageCoins, "persisted village treasury");
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(FarmUpCraftNeoForge.MOD_ID, path);
    }

    private static final class Fixture implements AutoCloseable {
        private final GameTestHelper helper;
        final ServerLevel level;
        private final Map<UUID, PlayerData> previousPlayers = new LinkedHashMap<>();
        private final Map<UUID, VillageData> previousVillages = new LinkedHashMap<>();
        private final Map<Long, ChunkData> previousChunks = new LinkedHashMap<>();
        private final Map<Long, GameStructureData> previousStructures = new LinkedHashMap<>();
        private final List<ServerPlayer> onlinePlayers = new ArrayList<>();
        private final List<EmbeddedChannel> channels = new ArrayList<>();
        private final int coordinate = 300_000 + Math.floorMod(UUID.randomUUID().hashCode(), 100_000);
        private int nextVillage;
        private int nextChunk;

        Fixture(GameTestHelper helper) {
            this.helper = helper;
            level = helper.getLevel();
        }

        VillageData village(int villageLevel, int coins) {
            UUID uuid = UUID.randomUUID();
            var database = ModEvents.getVillageDatabase(level);
            previousVillages.put(uuid, database.getData(uuid));
            var village = new VillageData(uuid, "regression_" + uuid.toString().substring(0, 8),
                    new ChunkPos(coordinate, coordinate + nextVillage++ * 16), villageLevel, List.of(),
                    true, coins, OLD_DATE, "", OLD_DATE, "", List.of());
            database.putData(uuid, village);
            return village;
        }

        ServerPlayer onlineMember(VillageData village, int coins) {
            var profile = new GameProfile(UUID.randomUUID(), "reg_" + UUID.randomUUID().toString().substring(0, 8));
            var cookie = CommonListenerCookie.createInitial(profile, false);
            var player = new ServerPlayer(level.getServer(), level, profile, cookie.clientInformation());
            seedMember(village, player.getUUID(), player.getId(), coins);
            var connection = new Connection(PacketFlow.SERVERBOUND);
            channels.add(new EmbeddedChannel(connection));
            NetworkRegistry.configureMockConnection(connection);
            onlinePlayers.add(player);
            player.connection = new ServerGamePacketListenerImpl(level.getServer(), connection, player, cookie);
            // Only arrange online membership: full login dispatches unrelated integration hooks which assume
            // a non-dedicated server has a Minecraft client, unlike the headless GameTestServer.
            serverPlayers().add(player);
            level.getServer().getPlayerList().getPlayersByUUID().put(player.getUUID(), player);
            player.setGameMode(GameType.SURVIVAL);
            var pos = helper.absolutePos(new BlockPos(1, 2, 1));
            player.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            helper.assertTrue(player.level() == level, "Test player must use the actual GameTest ServerLevel");
            assertNonop(helper, player.createCommandSourceStack());
            return player;
        }

        void offlineMember(VillageData village) {
            seedMember(village, UUID.randomUUID(), -1, 20);
        }

        private void seedMember(VillageData village, UUID uuid, int entityId, int coins) {
            var database = ModEvents.getPlayerDatabase(level);
            previousPlayers.put(uuid, database.getData(uuid));
            database.putData(uuid, new PlayerData(entityId, uuid, village.getUUID(), coins));
            village.addMember(uuid);
            ModEvents.getVillageDatabase(level).putData(village.getUUID(), village);
        }

        ChunkPos chunk(VillageData village, String type, ServerPlayer owner) {
            var database = ModEvents.getChunkDataDatabase(level);
            ChunkPos pos;
            do {
                pos = new ChunkPos(coordinate + ++nextChunk, village.getPosition().z());
            } while (database.getData(pos.pack()) != null);
            previousChunks.put(pos.pack(), null);
            var chunk = new ChunkData(type, owner.getId(), village.getUUID(), owner.getUUID());
            chunk.setBoughtY(80);
            chunk.setPlotLevel(1);
            database.putData(pos.pack(), chunk);
            village.addClaimedChunk(pos);
            ModEvents.getVillageDatabase(level).putData(village.getUUID(), village);
            return pos;
        }

        void structure(ChunkPos chunk, boolean claimed) {
            var database = ModEvents.getGameStructureDatabase(level);
            var pos = chunk.getMiddleBlockPosition(80);
            long key = pos.asLong();
            previousStructures.put(key, database.getData(key));
            database.putData(key, new GameStructureData(key, "regression_structure", pos, "village", claimed, false));
        }

        PlayerData playerData(ServerPlayer player) {
            return ModEvents.getPlayerDatabase(level).getData(player.getUUID());
        }

        int execute(ServerPlayer player, String command) throws CommandSyntaxException {
            return level.getServer().getCommands().getDispatcher().execute(command, player.createCommandSourceStack());
        }

        @SuppressWarnings("unchecked")
        private List<ServerPlayer> serverPlayers() {
            try {
                Field players = PlayerList.class.getDeclaredField("players");
                players.setAccessible(true);
                return (List<ServerPlayer>) players.get(level.getServer().getPlayerList());
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Cannot arrange online membership for real server players", failure);
            }
        }

        @Override
        public void close() {
            try {
                for (var player : onlinePlayers) {
                    serverPlayers().remove(player);
                    level.getServer().getPlayerList().getPlayersByUUID().remove(player.getUUID(), player);
                    player.getTextFilter().leave();
                    player.discard();
                }
            } finally {
                try {
                    channels.forEach(EmbeddedChannel::finishAndReleaseAll);
                } finally {
                    restore(ModEvents.getGameStructureDatabase(level), previousStructures);
                    restore(ModEvents.getChunkDataDatabase(level), previousChunks);
                    restore(ModEvents.getPlayerDatabase(level), previousPlayers);
                    restore(ModEvents.getVillageDatabase(level), previousVillages);
                }
            }
        }

        private static <K, V> void restore(DataBase<K, V> database, Map<K, V> previous) {
            previous.forEach((key, value) -> {
                if (value == null) {
                    database.removeDataBatch(List.of(key));
                } else {
                    database.putData(key, value);
                }
            });
        }
    }

    private static final class SchedulerClock implements AutoCloseable {
        private final ServerLevel level;
        private final Map<Field, Object> previousScheduler = new LinkedHashMap<>();
        private final WorldData previousWorld;
        private final String previousMarketDate;
        private final Field scheduleField;
        private final Field hourlyTicksField;

        SchedulerClock(ServerLevel level) {
            this.level = level;
            var worlds = ModEvents.getWorldDataDatabase(level);
            previousWorld = worlds.getData(0);
            previousMarketDate = previousWorld == null ? null : previousWorld.getLastRanMarketDaily();
            try {
                for (String name : List.of("activeServer", "stoppingServer", "schedule")) {
                    Field field = WorldScheduler.class.getDeclaredField(name);
                    field.setAccessible(true);
                    previousScheduler.put(field, field.get(null));
                }
                scheduleField = WorldScheduler.class.getDeclaredField("schedule");
                scheduleField.setAccessible(true);
                hourlyTicksField = scheduleField.getType().getDeclaredField("hourlyTicks");
                hourlyTicksField.setAccessible(true);
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Scheduler test clock no longer matches production fields", failure);
            }
            var world = previousWorld == null ? new WorldData() : previousWorld;
            // Isolate upkeep from market refresh/integration work; never advance the real calendar or wait an hour.
            world.markMarketDailyRanToday();
            worlds.putData(0, world);
            WorldScheduler.start(level.getServer());
        }

        void prime(int elapsedTicks) {
            try {
                // Only arrange elapsed time. All execution/accounting must come from the production tick listener.
                hourlyTicksField.setInt(scheduleField.get(null), elapsedTicks);
            } catch (IllegalAccessException failure) {
                throw new IllegalStateException("Cannot prime scheduler test clock", failure);
            }
        }

        void schedulerTick() {
            // Exercise the real scheduler listener, not global config/login tick subscribers: these fixtures
            // arrange online membership without a full network login. Event-bus registration is not covered here.
            WorldScheduler.onServerTick(new ServerTickEvent.Post(() -> true, level.getServer()));
        }

        @Override
        public void close() {
            try {
                for (var entry : previousScheduler.entrySet()) {
                    entry.getKey().set(null, entry.getValue());
                }
            } catch (IllegalAccessException failure) {
                throw new IllegalStateException("Cannot restore scheduler after regression test", failure);
            } finally {
                var worlds = ModEvents.getWorldDataDatabase(level);
                if (previousWorld == null) {
                    worlds.removeDataBatch(List.of(0));
                } else {
                    previousWorld.setLastRanMarketDaily(previousMarketDate);
                    worlds.putData(0, previousWorld);
                }
            }
        }
    }
}
