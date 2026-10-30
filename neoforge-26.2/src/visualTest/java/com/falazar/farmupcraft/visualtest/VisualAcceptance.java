package com.falazar.farmupcraft.visualtest;

import com.falazar.farmupcraft.CookwareManager;
import com.falazar.farmupcraft.data.ChunkData;
import com.falazar.farmupcraft.data.PlayerData;
import com.falazar.farmupcraft.data.VillageData;
import com.falazar.farmupcraft.database.message.EDBMessages;
import com.falazar.farmupcraft.database.message.AddJMWaypointPacket;
import com.falazar.farmupcraft.database.message.HighlightChunkPacket;
import com.falazar.farmupcraft.database.message.OpenJeiRecipePacket;
import com.falazar.farmupcraft.database.message.RemoveModWaypointsPacket;
import com.falazar.farmupcraft.database.message.ScanWaypointsPacket;
import com.falazar.farmupcraft.database.message.ShowVillageChunksPacket;
import com.falazar.farmupcraft.events.ModEvents;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Never included in the production source set or jar. Two ordinary game JVMs, no mock connection. */
@EventBusSubscriber(modid = "farmupcraft")
public final class VisualAcceptance {
    public static final UUID VILLAGE = UUID.fromString("7985360c-8634-46da-a085-e613a00c7341");
    public static final int COINS = 123456;
    public static final String VILLAGE_NAME = "Visual Acceptance";
    public static final String WAYPOINT_NAME = "Visual Acceptance Native";
    public static final String EDITED_WAYPOINT_NAME = "Visual Acceptance Edited";
    public static final String SCAN_NAME = "Visual Acceptance Scan";
    public static final int FINAL_MASK = 511;
    private static volatile boolean complete;
    private static Path directory;
    private static UUID participant;
    private static UUID readyParticipant;
    private static int seedTicks;
    private static long readyAtNanos;
    private static int stopTicks;
    private static int receivedMask;

    private VisualAcceptance() {}

    public static boolean enabled() {
        return Boolean.getBoolean("farmupcraft.visualAcceptance");
    }

    public static synchronized Path directory() {
        if (directory != null) return directory;
        String property = System.getProperty("farmupcraft.visualAcceptanceRoot", "");
        Path root = Path.of(property);
        Path game = FMLPaths.GAMEDIR.get().toAbsolutePath().normalize();
        if (!enabled() || FMLEnvironment.isProduction() || !root.isAbsolute()) {
            throw new IllegalStateException("Visual acceptance requires its gate and an absolute disposable root");
        }
        root = root.normalize();
        if (!root.endsWith(Path.of("build", "visual-acceptance"))
                || !(game.equals(root.resolve("server")) || game.equals(root.resolve("client")))) {
            throw new IllegalStateException("Visual acceptance game directory must be root\\server or root\\client");
        }
        try {
            // A junction/symlink must not redirect fixture writes, screenshots, options, or saves.
            for (Path path = game; path != null; path = path.getParent()) {
                if (Files.exists(path) && !path.toRealPath().equals(path.toAbsolutePath().normalize())) {
                    throw new IllegalStateException("Redirected acceptance ancestor: " + path);
                }
            }
            try (var paths = Files.walk(game)) {
                if (paths.anyMatch(path -> Files.isSymbolicLink(path) || redirected(path))) {
                    throw new IllegalStateException("Redirected acceptance child");
                }
            }
        } catch (IOException error) {
            throw new IllegalStateException("Cannot validate disposable game directory", error);
        }
        directory = game;
        return directory;
    }

    private static boolean redirected(Path path) {
        try {
            return !path.toRealPath().equals(path.toAbsolutePath().normalize());
        } catch (IOException error) {
            throw new IllegalStateException(error);
        }
    }

    public static synchronized void report(String text) {
        try {
            Files.writeString(directory().resolve("visual-receipt.txt"), text + System.lineSeparator(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException error) {
            throw new IllegalStateException("Cannot persist visual acceptance receipt", error);
        }
    }

    public static void require(boolean condition, String reason) {
        if (!condition) throw new IllegalStateException(reason);
    }

    @SubscribeEvent
    public static void setup(FMLCommonSetupEvent event) {
        if (!enabled()) return;
        Path game = directory();
        try {
            Files.writeString(game.resolve("visual-receipt.txt"),
                    "START development-only graphical acceptance; MACHINE=INCOMPLETE; VISUAL_JUDGMENT=PENDING\n",
                    StandardOpenOption.CREATE_NEW);
            require(!Files.exists(game.resolve("visual-shots")), "Stale screenshots; use fresh disposable directories");
            Files.createDirectory(game.resolve("visual-shots"));
        } catch (IOException error) {
            throw new IllegalStateException("Stale/unwritable receipt; use fresh disposable directories", error);
        }
        Thread.ofPlatform().daemon().name("visual-acceptance-watchdog").start(() -> {
            try {
                Thread.sleep(240_000);
                try {
                    if (!complete) {
                        report("FAIL 240s wall-clock deadline; MACHINE=INCOMPLETE; no visual PASS");
                    }
                } finally {
                    // Bound shutdown too, including a stalled render/server thread after the final receipt.
                    Runtime.getRuntime().halt(complete ? 0 : 2);
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        });
    }

    @SubscribeEvent
    public static void payloads(RegisterPayloadHandlersEvent event) {
        if (!enabled()) return;
        var registrar = event.registrar("visual-acceptance-4");
        registrar.playToServer(Receipt.TYPE, Receipt.CODEC,
                (packet, context) -> context.enqueueWork(() -> receive(packet, context)));
        registrar.playToClient(Acknowledgement.TYPE, Acknowledgement.CODEC,
                (packet, context) -> context.enqueueWork(() -> VisualAcceptanceClient.acknowledge(packet.mask())));
    }

    @SubscribeEvent
    public static void started(ServerStartedEvent event) {
        if (!enabled()) return;
        var server = event.getServer();
        try {
            require(server.isDedicatedServer() && "127.0.0.1".equals(server.getLocalIp())
                    && server.getPort() == 25579, "Requires dedicated server bound only to 127.0.0.1:25579");
            Path world = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
            require(world.startsWith(directory()) && !world.equals(directory()),
                    "Fixture world must be inside the disposable server directory");
            report("SERVER_READY loopback=127.0.0.1:25579; awaiting one real VisualProbe");
        } catch (RuntimeException error) {
            fail(server, error);
        }
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        if (!enabled()) return;
        var server = event.getServer();
        try {
            if (stopTicks > 0) {
                if (--stopTicks == 0) server.halt(false);
                return;
            }
            var players = server.getPlayerList().getPlayers();
            require(players.size() <= 1, "Only one acceptance participant is allowed");
            require((participant == null && readyParticipant == null) || !players.isEmpty(),
                    "Participant disconnected before final receipt");
            if (participant == null && players.size() == 1) {
                ServerPlayer player = players.getFirst();
                require("VisualProbe".equals(player.getGameProfile().name()), "Unexpected participant");
                if (readyParticipant == null) return;
                require(readyParticipant.equals(player.getUUID()), "Ready participant changed");
                if (seedTicks > 0 && --seedTicks > 0) return;
                require(System.nanoTime() - readyAtNanos < 30_000_000_000L,
                        "30s fixture handshake deadline; waiting for production player database");
                if (ModEvents.getPlayerDatabase(player.level()).getData(player.getUUID()) == null) return;
                seed(player);
                participant = player.getUUID();
                // Drain login work first, then use the real production full-snapshot sender.
                // This invokes only its sender, not the login event bus or player initialization.
                EDBMessages.playerLoggedIn(new PlayerEvent.PlayerLoggedInEvent(player));
                server.execute(() -> {
                    PacketDistributor.sendToPlayer(player, new Acknowledgement(0));
                    report("SERVER_FIXTURE_BARRIER mask=0; fresh production full snapshots queued before marker");
                });
            }
        } catch (RuntimeException error) {
            fail(server, error);
        }
    }

    private static void seed(ServerPlayer player) {
        var level = player.level().getServer().overworld();
        player.setGameMode(GameType.SURVIVAL);
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                level.setBlockAndUpdate(new BlockPos(x, 80, z), Blocks.GRASS_BLOCK.defaultBlockState());
                for (int y = 81; y < 87; y++) {
                    level.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
                }
            }
        }
        level.setBlockAndUpdate(new BlockPos(3, 80, 11), Blocks.FARMLAND.defaultBlockState());
        level.setBlockAndUpdate(new BlockPos(3, 81, 11),
                Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7));
        level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack(),
                "weather clear 6000");
        level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack(),
                "time set 6000");
        // A fixed survival-mode perch keeps the camera elevated without flight/options changes.
        level.setBlockAndUpdate(new BlockPos(4, 91, 4), Blocks.STONE.defaultBlockState());
        require(player.teleportTo(level, 4.5, 92, 4.5, Set.of(), -45, 38, true),
                "Could not position elevated claim-corner observer");

        var village = new VillageData(VILLAGE, VILLAGE_NAME, new ChunkPos(0, 0), 1,
                List.of(new ChunkPos(0, 0)), true, 1000, "2000-01-01", "", "2000-01-01",
                "VisualProbe", List.of(player.getUUID()));
        ModEvents.getVillageDatabase(level).putData(VILLAGE, village);
        var plot = new ChunkData("farm", player.getId(), VILLAGE, player.getUUID());
        plot.setPlotLevel(3);
        ModEvents.getChunkDataDatabase(level).putData(ChunkPos.pack(0, 0), plot);
        ModEvents.getPlayerDatabase(level).putData(player.getUUID(),
                new PlayerData(player.getId(), player.getUUID(), VILLAGE, COINS, true, "blue"));

        var potItem = BuiltInRegistries.ITEM.getValue(
                Identifier.fromNamespaceAndPath("pamhc2foodcore", "potitem"));
        require(potItem != null && potItem != Items.AIR, "Required HarvestCraft pot missing");
        var pot = new ItemStack(potItem);
        // Drive the real production crafted-event handler; this is fixture seeding, not a recipe test.
        var crafted = BuiltInRegistries.ITEM.stream()
                .filter(item -> BuiltInRegistries.ITEM.getKey(item).getNamespace().equals("pamhc2foodcore"))
                .filter(item -> !CookwareManager.isCookware(new ItemStack(item))).findFirst().orElseThrow();
        var grid = new SimpleContainer(pot);
        for (int use = 0; use < 250; use++) {
            CookwareManager.onItemCrafted(new PlayerEvent.ItemCraftedEvent(player, new ItemStack(crafted), grid));
        }
        require(CookwareManager.getRemainingUses(pot) == 250, "Production cookware fixture did not consume uses");
        player.getInventory().clearContent();
        player.getInventory().setItem(0, new ItemStack(Items.DIAMOND));
        player.getInventory().setItem(1, new ItemStack(Items.WHEAT_SEEDS));
        player.getInventory().setItem(2, pot);
        player.getInventory().setSelectedSlot(0);
        player.inventoryMenu.broadcastChanges();
        report("FIXTURE uuid=" + player.getUUID() + " village=" + VILLAGE
                + " claim=0,0 farmLevel=3 coins=" + COINS + " potUses=250; production database sync;"
                + " observerFeet=4.5,92,4.5 yaw=-45 pitch=38 target=southeast-claim-corner(16,80,16)");
    }

    private static void receive(Receipt receipt, IPayloadContext context) {
        if (!enabled()) return;
        if (!(context.player() instanceof ServerPlayer player)) return;
        var server = player.level().getServer();
        try {
            if (receipt.mask() == 0) {
                require(!complete && participant == null && readyParticipant == null,
                        "Duplicate/unexpected client-ready receipt");
                require("VisualProbe".equals(player.getGameProfile().name())
                                && server.getPlayerList().getPlayerCount() == 1,
                        "Unexpected client-ready participant");
                readyParticipant = player.getUUID();
                readyAtNanos = System.nanoTime();
                seedTicks = 2;
                report("SERVER_CLIENT_READY mask=0; waiting two server ticks before fixture seeding");
                return;
            }
            int expected = (receivedMask << 1) | 1;
            require(!complete && player.getUUID().equals(participant) && expected <= FINAL_MASK
                            && receipt.mask() == expected,
                    "Invalid/duplicate visual stage receipt");
            require(server.getPlayerList().getPlayerCount() == 1, "Participant changed");
            require(ModEvents.getPlayerDatabase(player.level()).getData(participant).getCoins() == COINS,
                    "Authoritative coins changed");
            require(CookwareManager.getRemainingUses(player.getInventory().getItem(2)) == 250,
                    "Authoritative cookware changed");
            var claim = ModEvents.getChunkDataDatabase(player.level()).getData(ChunkPos.pack(0, 0));
            require(claim != null && VILLAGE.equals(claim.getVillageId()) && claim.getPlotLevel() == 3,
                    "Authoritative claim changed");
            receivedMask = receipt.mask();
            report("SERVER_ACK mask=" + receivedMask + " authoritative coins/claim/cookware unchanged");
            integrationPayloads(player, receivedMask);
            PacketDistributor.sendToPlayer(player, new Acknowledgement(receivedMask));
            if (receivedMask == FINAL_MASK) {
                report("MACHINE_COMPLETE ordered client stages=HUD_BORDERS,CROP_TOOLTIP,COOKWARE,PAUSE_WEATHER,"
                        + "JEI_OUTPUT_FOCUS,JM_WAYPOINTS,JM_EDITOR,JM_POLYGONS,JM_CLEANUP;"
                        + " VISUAL_JUDGMENT=PENDING; manual PNG review required");
                complete = true;
                stopTicks = 40;
            }
        } catch (RuntimeException error) {
            fail(server, error);
        }
    }

    private static void integrationPayloads(ServerPlayer player, int mask) {
        switch (mask) {
            case 15 -> {
                PacketDistributor.sendToPlayer(player, new OpenJeiRecipePacket("minecraft:bread"));
                report("PRODUCTION_PAYLOAD OpenJeiRecipePacket minecraft:bread; queued before ACK=15");
            }
            case 31 -> {
                PacketDistributor.sendToPlayer(player, new AddJMWaypointPacket(8, 81, 8, WAYPOINT_NAME,
                        "minecraft:overworld", true, 0xFFD700));
                PacketDistributor.sendToPlayer(player, new ScanWaypointsPacket(ScanWaypointsPacket.ADD,
                        List.of(new ScanWaypointsPacket.Entry(SCAN_NAME, 24, 81, 8, 0x00FF00))));
                report("PRODUCTION_PAYLOAD AddJMWaypointPacket persistent native; ScanWaypointsPacket temporary;"
                        + " queued before ACK=31");
            }
            case 127 -> {
                PacketDistributor.sendToPlayer(player, new HighlightChunkPacket(0, 0, "Visual Acceptance Highlight",
                        "minecraft:overworld", 0xFF0000));
                PacketDistributor.sendToPlayer(player, new ShowVillageChunksPacket(
                        List.of(new int[]{1, 0, 0x0000FF}), true, "minecraft:overworld"));
                report("PRODUCTION_PAYLOAD HighlightChunkPacket red chunk=0,0; ShowVillageChunksPacket blue chunk=1,0;"
                        + " queued before ACK=127");
            }
            case 255 -> {
                PacketDistributor.sendToPlayer(player, new ScanWaypointsPacket(ScanWaypointsPacket.CLEAR, List.of()));
                PacketDistributor.sendToPlayer(player, new RemoveModWaypointsPacket(8, 81, 8, 64,
                        false, "Visual Acceptance", false));
                PacketDistributor.sendToPlayer(player, new ShowVillageChunksPacket(
                        List.of(), false, "minecraft:overworld"));
                report("PRODUCTION_PAYLOAD scan CLEAR; filtered native removal; polygon clear; queued before ACK=255");
            }
            default -> { }
        }
    }

    private static void fail(MinecraftServer server, RuntimeException error) {
        report("FAIL " + error + "; MACHINE=INCOMPLETE; VISUAL_JUDGMENT=PENDING");
        server.halt(false);
    }

    public static void clientComplete() {
        complete = true;
    }

    public record Receipt(int mask) implements CustomPacketPayload {
        public static final Type<Receipt> TYPE = new Type<>(
                Identifier.fromNamespaceAndPath("farmupcraft", "visual_receipt"));
        public static final StreamCodec<ByteBuf, Receipt> CODEC =
                StreamCodec.composite(ByteBufCodecs.INT, Receipt::mask, Receipt::new);
        @Override public Type<Receipt> type() { return TYPE; }
    }

    public record Acknowledgement(int mask) implements CustomPacketPayload {
        public static final Type<Acknowledgement> TYPE = new Type<>(
                Identifier.fromNamespaceAndPath("farmupcraft", "visual_ack"));
        public static final StreamCodec<ByteBuf, Acknowledgement> CODEC =
                StreamCodec.composite(ByteBufCodecs.INT, Acknowledgement::mask, Acknowledgement::new);
        @Override public Type<Acknowledgement> type() { return TYPE; }
    }
}
