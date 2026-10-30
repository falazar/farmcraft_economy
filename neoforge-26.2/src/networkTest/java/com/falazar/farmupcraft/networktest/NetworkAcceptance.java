package com.falazar.farmupcraft.networktest;

import com.falazar.farmupcraft.data.PlayerData;
import com.falazar.farmupcraft.database.DataBase;
import com.falazar.farmupcraft.database.DataBaseAccess;
import com.falazar.farmupcraft.database.DataBaseBuilder;
import com.falazar.farmupcraft.database.DataBaseManager;
import com.falazar.farmupcraft.database.message.DataBaseFullS2C;
import com.falazar.farmupcraft.database.message.EDBMessages;
import com.falazar.farmupcraft.database.serializers.StringDataSerializer;
import com.falazar.farmupcraft.events.ModEvents;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

/** Development source set only; the two JVMs communicate solely through normal game networking. */
@EventBusSubscriber(modid = "farmupcraft")
public final class NetworkAcceptance {
    public static final Identifier NAME = Identifier.fromNamespaceAndPath("farmupcraft", "network_acceptance");
    public static final int UPDATED_COINS = 654321;
    public static final DataBaseAccess<String, String> PROBE = new DataBaseBuilder<String, String>(NAME)
            .setKeySerializer(new StringDataSerializer()).setValueSerializer(new StringDataSerializer())
            .autoSync().build();
    private static UUID participant;
    private static int stage;
    private static long deadline;
    private static boolean finished;

    private NetworkAcceptance() {}

    public static boolean enabled() {
        return Boolean.getBoolean("farmupcraft.networkAcceptance");
    }

    public static Path directory() {
        Path path = FMLPaths.GAMEDIR.get().toAbsolutePath().normalize();
        Path expected = Path.of(System.getProperty("farmupcraft.networkAcceptanceRoot", "missing"))
                .toAbsolutePath().normalize();
        if (!expected.endsWith(Path.of("build", "network-acceptance"))
                || !path.startsWith(expected) || path.equals(expected)) {
            throw new IllegalStateException("Acceptance requires a disposable build\\network-acceptance child");
        }
        return path;
    }

    public static synchronized void report(String side, String message) {
        try {
            Files.writeString(directory().resolve(side + "-receipt.txt"), message + System.lineSeparator(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException error) {
            throw new IllegalStateException("Cannot persist network acceptance result", error);
        }
    }

    @SubscribeEvent
    public static void setup(FMLCommonSetupEvent event) {
        if (enabled()) {
            directory();
            event.enqueueWork(() -> DataBaseManager.registerDataBaseAccess(NAME, PROBE));
        }
    }

    @SubscribeEvent
    public static void payloads(RegisterPayloadHandlersEvent event) {
        if (enabled()) {
            event.registrar("network-acceptance-1").playToServer(Receipt.TYPE, Receipt.CODEC,
                    NetworkAcceptance::receive);
        }
    }

    @SubscribeEvent
    public static void started(ServerStartedEvent event) {
        if (!enabled()) return;
        directory();
        if (!event.getServer().isDedicatedServer()) {
            throw new IllegalStateException("Acceptance requires a separate dedicated server JVM");
        }
        participant = null;
        stage = 0;
        finished = false;
        deadline = System.nanoTime() + 240_000_000_000L;
        PROBE.get(event.getServer().overworld()).putData("phase", "login");
        report("server", "START separate dedicated server; awaiting real client");
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        if (enabled() && !finished && deadline != 0 && System.nanoTime() > deadline) {
            fail(event.getServer(), "240s deadline; waiting for receipt stage " + stage);
        }
    }

    private static void receive(Receipt receipt, IPayloadContext context) {
        if (!enabled()) return;
        if (!(context.player() instanceof ServerPlayer player)) return;
        var server = player.level().getServer();
        try {
            if (finished || receipt.stage() != stage) {
                throw new IllegalStateException("Unexpected/duplicate receipt stage " + receipt.stage());
            }
            if (server.getPlayerList().getPlayerCount() != 1
                    || (participant != null && !participant.equals(player.getUUID()))) {
                throw new IllegalStateException("Exactly one stable client identity is required");
            }
            participant = player.getUUID();
            PlayerData authoritative = ModEvents.getPlayerDatabase(player.level()).getData(participant);
            int expected = stage == 0 ? 10 : UPDATED_COINS;
            if (authoritative == null || !participant.equals(authoritative.getPlayerUUID())
                    || authoritative.getCoins() != expected || receipt.coins() != expected) {
                throw new IllegalStateException("Server/client player identity or balance mismatch");
            }
            if (stage == 2 && !player.level().dimension().equals(Level.NETHER)) {
                throw new IllegalStateException("Dimension receipt did not come from the Nether");
            }
            if (receipt.digest() != (stage == 3 ? ProbeData.largeDigest() : 0)) {
                throw new IllegalStateException("Client receipt digest mismatch");
            }
            report("server", "RECEIVED stage=" + stage + " uuid=" + participant
                    + " coins=" + receipt.coins() + " digest=" + receipt.digest()
                    + " dimension=" + player.level().dimension().identifier());
            int completed = stage++;
            switch (completed) {
                case 0 -> {
                    authoritative.setCoins(UPDATED_COINS);
                    ModEvents.getPlayerDatabase(player.level()).putData(participant, authoritative);
                }
                case 1 -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),
                        "execute in minecraft:the_nether run tp " + player.getGameProfile().name() + " 0 100 0");
                case 2 -> sendOversized(player);
                case 3 -> {
                    finished = true;
                    report("server", "PASS login-full entry-update dimension-resync oversized-full");
                    player.connection.disconnect(Component.literal("Network acceptance complete"));
                }
                default -> throw new IllegalStateException("Invalid stage");
            }
        } catch (RuntimeException error) {
            fail(server, error.toString());
        }
    }

    private static void sendOversized(ServerPlayer player) {
        // A detached database uses this real server's registries, but cannot broadcast per-entry updates.
        // Only ONE full production payload is sent; receipt cannot be satisfied by incremental entries.
        try (var snapshot = new DataBase<>(NAME.toString(), new StringDataSerializer(),
                new StringDataSerializer(), false, 0, false, player.level().registryAccess())) {
            for (int i = 0; i < ProbeData.ROWS; i++) {
                snapshot.putData("large-" + i, ProbeData.row(i));
            }
            var payload = new DataBaseFullS2C(snapshot.save(new CompoundTag()), NAME.toString());
            ByteBuf buffer = Unpooled.buffer();
            try {
                DataBaseFullS2C.STREAM_CODEC.encode(buffer, payload);
                int bytes = buffer.readableBytes();
                if (bytes <= 1_048_576) throw new IllegalStateException("Snapshot was not oversized: " + bytes);
                report("server", "SENT oversized-full encoded-bytes=" + bytes);
            } finally {
                buffer.release();
            }
            EDBMessages.sendToPlayer(payload, player);
        }
    }

    private static void fail(MinecraftServer server, String reason) {
        finished = true;
        report("server", "FAIL " + reason);
        server.getPlayerList().getPlayers().forEach(
                player -> player.connection.disconnect(Component.literal("Network acceptance failed: " + reason)));
    }

    public record Receipt(int stage, int coins, int digest) implements CustomPacketPayload {
        public static final Type<Receipt> TYPE =
                new Type<>(Identifier.fromNamespaceAndPath("farmupcraft", "network_acceptance_receipt"));
        public static final StreamCodec<ByteBuf, Receipt> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Receipt::stage, ByteBufCodecs.VAR_INT, Receipt::coins,
                ByteBufCodecs.INT, Receipt::digest, Receipt::new);

        @Override
        public Type<Receipt> type() {
            return TYPE;
        }
    }
}
