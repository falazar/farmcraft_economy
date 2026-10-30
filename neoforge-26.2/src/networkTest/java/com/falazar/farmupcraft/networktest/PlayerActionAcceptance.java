package com.falazar.farmupcraft.networktest;

import com.falazar.farmupcraft.events.ModEvents;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Set;
import java.util.UUID;

@EventBusSubscriber(modid = "farmupcraft")
public final class PlayerActionAcceptance {
    public static final BlockPos BREAK = new BlockPos(36, 81, 32);
    public static final BlockPos PLACE = new BlockPos(36, 81, 33);
    private static UUID participant;
    private static int stage = -1;
    private static long deadline;
    private static int shutdownTicks;
    private static boolean breakEvent;
    private static boolean placeEvent;
    private static boolean finished;

    private PlayerActionAcceptance() {}

    public static boolean enabled() {
        return Boolean.getBoolean("farmupcraft.playerActionAcceptance");
    }

    public static Path directory() {
        Path root = Path.of(System.getProperty("farmupcraft.playerActionAcceptanceRoot", "missing"))
                .toAbsolutePath().normalize();
        Path directory = FMLPaths.GAMEDIR.get().toAbsolutePath().normalize();
        if (!root.endsWith(Path.of("build", "player-action-acceptance"))
                || !(directory.equals(root.resolve("client")) || directory.equals(root.resolve("server")))) {
            throw new IllegalStateException("Player-action acceptance requires isolated build peer directories");
        }
        return directory;
    }

    public static synchronized void report(String message) {
        try {
            Files.writeString(directory().resolve("player-action-receipt.txt"), message + System.lineSeparator(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException error) {
            throw new IllegalStateException("Cannot persist player-action receipt", error);
        }
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        if (!enabled()) return;
        var registrar = event.registrar("player-actions-1");
        registrar.playToClient(Step.TYPE, Step.CODEC);
        registrar.playToServer(Acknowledge.TYPE, Acknowledge.CODEC, PlayerActionAcceptance::acknowledge);
    }

    @SubscribeEvent
    public static void started(ServerStartedEvent event) {
        if (!enabled()) return;
        directory();
        if (!event.getServer().isDedicatedServer() || NetworkAcceptance.enabled()) {
            throw new IllegalStateException("Separate dedicated player-action acceptance required");
        }
        participant = null;
        stage = -1;
        finished = false;
        breakEvent = false;
        placeEvent = false;
        shutdownTicks = 0;
        deadline = System.nanoTime() + 240_000_000_000L;
        report("START dedicated survival/non-op movement break place; disposable fixture only");
    }

    private static void acknowledge(Acknowledge packet, IPayloadContext context) {
        if (!enabled()) return;
        if (!(context.player() instanceof ServerPlayer player)) {
            throw new IllegalStateException("Player-action receipt requires a server player");
        }
        var server = player.level().getServer();
        if (finished || server.getPlayerList().getPlayerCount() != 1
                || server.getPlayerList().isOp(player.nameAndId())) {
            fail(server, "Exactly one non-operator participant required");
            return;
        }
        if (packet.stage() == -1 && stage == -1 && participant == null) {
            participant = player.getUUID();
            if (ModEvents.getChunkDataDatabase(player.level())
                    .getData(ChunkPos.containing(BREAK).pack()) != null) {
                fail(server, "Fixture must be in unclaimed wilderness");
                return;
            }
            for (int x = 30; x <= 39; x++) {
                for (int z = 30; z <= 36; z++) {
                    player.level().setBlockAndUpdate(new BlockPos(x, 80, z), Blocks.STONE.defaultBlockState());
                }
            }
            player.level().setBlockAndUpdate(BREAK, Blocks.DIRT.defaultBlockState());
            player.level().setBlockAndUpdate(PLACE, Blocks.AIR.defaultBlockState());
            if (!player.setGameMode(GameType.SURVIVAL)
                    && player.gameMode.getGameModeForPlayer() != GameType.SURVIVAL) {
                fail(server, "Cannot establish survival participant");
                return;
            }
            player.getInventory().clearContent();
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.DIRT, 3));
            player.inventoryMenu.sendAllDataToRemote();
            player.teleportTo(player.level(), 32.5, 81, 32.5, Set.of(), -90, 15, true);
            report("FIXTURE uuid=" + participant + " op=false survival=true wilderness=true start=32.5,81,32.5"
                    + " break=" + BREAK + " place=" + PLACE + " dirt=3");
            advance(player, 0);
        } else if (packet.stage() == 3 && stage == 3 && player.getUUID().equals(participant)) {
            finished = true;
            shutdownTicks = 40;
            report("PASS server-authoritative non-op survival movement break place; client confirmed final state");
            advance(player, 4);
        } else {
            fail(server, "Unexpected acknowledgement " + packet.stage() + " at stage " + stage);
        }
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        if (!enabled() || deadline == 0) return;
        var server = event.getServer();
        if (finished) {
            if (shutdownTicks > 0 && --shutdownTicks == 0) server.halt(false);
            return;
        }
        if (System.nanoTime() > deadline) {
            fail(server, "240s deadline at stage " + stage);
            return;
        }
        if (participant == null) return;
        ServerPlayer player = server.getPlayerList().getPlayer(participant);
        if (player == null || server.getPlayerList().isOp(player.nameAndId())
                || player.gameMode.getGameModeForPlayer() != GameType.SURVIVAL) {
            fail(server, "Participant disconnected or lost non-op survival invariants");
            return;
        }
        if (stage == 0 && player.getX() >= 34.5 && player.getX() < 36
                && Math.abs(player.getZ() - 32.5) < 0.25 && Math.abs(player.getY() - 81) < 0.1) {
            report("OBSERVED movement x=" + player.getX() + " y=" + player.getY() + " z=" + player.getZ());
            advance(player, 1);
        } else if (stage == 1 && breakEvent && player.level().getBlockState(BREAK).isAir()) {
            report("OBSERVED survival break event and authoritative air at " + BREAK);
            advance(player, 2);
        } else if (stage == 2 && placeEvent && player.level().getBlockState(PLACE).is(Blocks.DIRT)
                && player.getMainHandItem().is(Items.DIRT) && player.getMainHandItem().getCount() == 2) {
            report("OBSERVED survival place event authoritative dirt at " + PLACE + " heldDirt=2");
            advance(player, 3);
        }
    }

    @SubscribeEvent
    public static void broke(BreakBlockEvent event) {
        if (enabled() && stage == 1 && event.getPlayer() != null
                && event.getPlayer().getUUID().equals(participant) && event.getPos().equals(BREAK)
                && event.getLevel().getBlockState(BREAK).is(Blocks.DIRT) && !event.isCanceled()) {
            breakEvent = true;
        }
    }

    @SubscribeEvent
    public static void placed(BlockEvent.EntityPlaceEvent event) {
        if (enabled() && stage == 2 && event.getEntity() instanceof ServerPlayer player
                && player.getUUID().equals(participant) && event.getPos().equals(PLACE)
                && event.getPlacedBlock().is(Blocks.DIRT) && !event.isCanceled()) {
            placeEvent = true;
        }
    }

    private static void advance(ServerPlayer player, int next) {
        stage = next;
        PacketDistributor.sendToPlayer(player, new Step(next));
    }

    private static void fail(MinecraftServer server, String reason) {
        finished = true;
        shutdownTicks = 40;
        report("FAIL " + reason);
        server.getPlayerList().getPlayers().forEach(
                player -> player.connection.disconnect(Component.literal("Player-action acceptance failed: " + reason)));
    }

    public record Step(int stage) implements CustomPacketPayload {
        public static final Type<Step> TYPE = new Type<>(
                Identifier.fromNamespaceAndPath("farmupcraft", "player_action_step"));
        public static final StreamCodec<ByteBuf, Step> CODEC =
                StreamCodec.composite(ByteBufCodecs.VAR_INT, Step::stage, Step::new);
        @Override public Type<Step> type() { return TYPE; }
    }

    public record Acknowledge(int stage) implements CustomPacketPayload {
        public static final Type<Acknowledge> TYPE = new Type<>(
                Identifier.fromNamespaceAndPath("farmupcraft", "player_action_ack"));
        public static final StreamCodec<ByteBuf, Acknowledge> CODEC =
                StreamCodec.composite(ByteBufCodecs.VAR_INT, Acknowledge::stage, Acknowledge::new);
        @Override public Type<Acknowledge> type() { return TYPE; }
    }
}
