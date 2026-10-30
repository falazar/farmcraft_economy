package com.falazar.farmupcraft.networktest;

import com.falazar.farmupcraft.data.PlayerData;
import com.falazar.farmupcraft.database.DataBase;
import com.falazar.farmupcraft.events.ModEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.net.InetSocketAddress;
import java.util.UUID;

@EventBusSubscriber(modid = "farmupcraft", value = Dist.CLIENT)
public final class NetworkAcceptanceClient {
    private static int stage;
    private static long deadline;
    private static ClientLevel previousLevel;
    private static DataBase<UUID, PlayerData> previousPlayers;
    private static DataBase<String, String> previousProbe;
    private static boolean failed;

    private NetworkAcceptanceClient() {}

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!NetworkAcceptance.enabled() || failed || stage == 4) return;
        if (deadline == 0) {
            NetworkAcceptance.directory();
            deadline = System.nanoTime() + 240_000_000_000L;
            NetworkAcceptance.report("client", "START real client; awaiting localhost connection");
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (System.nanoTime() > deadline) {
            fail("240s deadline; waiting for observation stage " + stage);
            return;
        }
        if (minecraft.level == null || minecraft.player == null || minecraft.getConnection() == null) return;
        if (minecraft.hasSingleplayerServer()) {
            fail("Integrated server is not a connected dedicated-server acceptance test");
            return;
        }
        var remote = minecraft.getConnection().getConnection().getRemoteAddress();
        if (!(remote instanceof InetSocketAddress address) || address.getAddress() == null
                || !address.getAddress().isLoopbackAddress()) {
            fail("Acceptance client must connect to a loopback socket");
            return;
        }
        var level = minecraft.level;
        var players = ModEvents.getPlayerDatabase(level);
        var probe = NetworkAcceptance.PROBE.get(level);
        var uuid = minecraft.player.getUUID();
        var player = players.getData(uuid);
        if (player == null || !uuid.equals(player.getPlayerUUID())) return;
        switch (stage) {
            case 0 -> {
                if (!level.dimension().equals(Level.OVERWORLD) || player.getCoins() != 10
                        || !"login".equals(probe.getData("phase"))) return;
                previousLevel = level;
                previousPlayers = players;
                previousProbe = probe;
                acknowledge(player, 0);
            }
            case 1 -> {
                if (player.getCoins() != NetworkAcceptance.UPDATED_COINS) return;
                probe.putData("client-only", "must disappear");
                acknowledge(player, 0);
            }
            case 2 -> {
                if (!level.dimension().equals(Level.NETHER)
                        || player.getCoins() != NetworkAcceptance.UPDATED_COINS
                        || !"login".equals(probe.getData("phase"))) return;
                if (level == previousLevel || players == previousPlayers || probe == previousProbe
                        || probe.getData("client-only") != null) {
                    fail("Dimension resync retained the previous level's client cache");
                    return;
                }
                previousLevel = level;
                previousPlayers = players;
                previousProbe = probe;
                acknowledge(player, 0);
            }
            case 3 -> {
                if (probe.getSize() != ProbeData.ROWS) return;
                int digest = 1;
                for (int i = 0; i < ProbeData.ROWS; i++) {
                    String actual = probe.getData("large-" + i);
                    if (!ProbeData.row(i).equals(actual)) return;
                    digest = 31 * digest + actual.hashCode();
                }
                if (probe.getData("phase") != null || probe.getData("client-only") != null) {
                    fail("Full oversized snapshot did not replace previous contents");
                    return;
                }
                acknowledge(player, digest);
            }
            default -> throw new IllegalStateException("Invalid stage " + stage);
        }
    }

    private static void acknowledge(PlayerData player, int digest) {
        NetworkAcceptance.report("client", "OBSERVED stage=" + stage + " uuid=" + player.getPlayerUUID()
                + " coins=" + player.getCoins() + " digest=" + digest);
        ClientPacketDistributor.sendToServer(new NetworkAcceptance.Receipt(stage++, player.getCoins(), digest));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void loggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
        if (!NetworkAcceptance.enabled() || previousLevel == null || failed) return;
        // Use the SAME previously connected real level: a new level would clear the cache on its own
        // and falsely credit the production logout handler. Do not retain this new empty cache.
        var players = ModEvents.getPlayerDatabase(previousLevel);
        var probe = NetworkAcceptance.PROBE.get(previousLevel);
        boolean cleared = players != previousPlayers && probe != previousProbe
                && players.getSize() == 0 && probe.getSize() == 0;
        com.falazar.farmupcraft.database.DataBaseManager.clearClientCaches();
        if (stage == 4 && cleared) {
            NetworkAcceptance.report("client", "PASS connected-receipts dimension-new-cache logout-cache-cleared");
        } else {
            fail("Premature disconnect or logout cache retained; stage=" + stage + " cleared=" + cleared);
        }
        previousLevel = null;
        previousPlayers = null;
        previousProbe = null;
    }

    private static void fail(String reason) {
        failed = true;
        NetworkAcceptance.report("client", "FAIL " + reason);
        var connection = Minecraft.getInstance().getConnection();
        if (connection != null) connection.getConnection().disconnect(Component.literal(reason));
    }
}
