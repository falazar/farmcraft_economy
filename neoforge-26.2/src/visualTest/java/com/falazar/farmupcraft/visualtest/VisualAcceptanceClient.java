package com.falazar.farmupcraft.visualtest;

import com.falazar.farmupcraft.CookwareManager;
import com.falazar.farmupcraft.client.border.VisualBorderProbe;
import com.falazar.farmupcraft.client.overlay.PlayerDataOverlay;
import com.falazar.farmupcraft.client.overlay.VisualHudProbe;
import com.falazar.farmupcraft.events.ModEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

import static com.falazar.farmupcraft.visualtest.VisualAcceptance.require;

/** Real dedicated connection, production GUI extraction, and GPU framebuffer readback only. */
@EventBusSubscriber(modid = "farmupcraft", value = Dist.CLIENT)
public final class VisualAcceptanceClient {
    private static final String[] STAGES = {"hud-borders", "crop-tooltip", "cookware", "pause-weather",
            "jei-output-focus", "journeymap-waypoints", "journeymap-editor", "journeymap-polygons",
            "journeymap-cleanup"};
    private static int stage = -1;
    private static int stableFrames;
    private static long stageStarted;
    private static boolean capturing;
    private static boolean waitingAck;
    private static boolean finished;
    private static boolean joined;
    private static boolean readySent;
    private static boolean fixtureBarrier;
    private static long waitStarted;
    private static long lastProgress;
    private static String lastFrameGuard = "no stage frame observed";
    private static boolean hud;
    private static boolean borders;
    private static boolean tooltip;
    private static List<String> hudLines = List.of();
    private static List<String> tooltipLines = List.of();
    private static HoverInventory inventory;
    private static Double originalWeather;

    private VisualAcceptanceClient() {}

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!VisualAcceptance.enabled() || finished) return;
        var minecraft = Minecraft.getInstance();
        try {
            if (minecraft.level == null || minecraft.player == null || minecraft.getConnection() == null) return;
            require(!minecraft.hasSingleplayerServer(), "Requires a real dedicated connection");
            var remote = minecraft.getConnection().getConnection().getRemoteAddress();
            require(remote instanceof InetSocketAddress address && address.getAddress() != null
                            && address.getAddress().isLoopbackAddress() && address.getPort() == 25579,
                    "Client must connect only to loopback:25579");
            require("VisualProbe".equals(minecraft.player.getGameProfile().name()), "Unexpected client identity");
            if (!readySent) {
                readySent = true;
                waitStarted = System.nanoTime();
                lastProgress = waitStarted;
                VisualAcceptance.report("CLIENT_READY mask=0; real play connection established; awaiting fresh snapshots/barrier");
                ClientPacketDistributor.sendToServer(new VisualAcceptance.Receipt(0));
            }
            if (stage < 0 && fixtureBarrier && minecraft.gui.screen() == null && fixtureReady(minecraft)) {
                begin(minecraft, 0);
            }
            if (stage >= 0) clearTransientUi(minecraft);
            if (stage == 7 && !capturing && !waitingAck) VisualIntegrationProbe.tick(minecraft);
            long now = System.nanoTime();
            if (now - lastProgress >= 5_000_000_000L) {
                VisualAcceptance.report("CLIENT_WAIT stage=" + stage + " barrier=" + fixtureBarrier
                        + " capturing=" + capturing + " waitingAck=" + waitingAck + " "
                        + fixtureDiagnostics(minecraft) + "; lastFrame=" + lastFrameGuard);
                lastProgress = now;
            }
            require(now - waitStarted < 30_000_000_000L,
                    "30s client stage guard deadline; stage=" + stage + " barrier=" + fixtureBarrier
                            + " capturing=" + capturing + " waitingAck=" + waitingAck + " "
                            + fixtureDiagnostics(minecraft) + "; lastFrame=" + lastFrameGuard);
        } catch (RuntimeException error) {
            fail(error);
        }
    }

    private static boolean fixtureReady(Minecraft minecraft) {
        var player = minecraft.player;
        var level = minecraft.level;
        var data = ModEvents.getPlayerDatabase(level).getData(player.getUUID());
        var claim = ModEvents.getChunkDataDatabase(level).getData(ChunkPos.pack(player.blockPosition()));
        var village = ModEvents.getVillageDatabase(level).getData(VisualAcceptance.VILLAGE);
        var pot = player.getInventory().getItem(2);
        return data != null && data.getCoins() == VisualAcceptance.COINS
                && VisualAcceptance.VILLAGE.equals(data.getHomeVillageUUID())
                && claim != null && VisualAcceptance.VILLAGE.equals(claim.getVillageId())
                && "farm".equals(claim.getType()) && claim.getPlotLevel() == 3
                && village != null && VisualAcceptance.VILLAGE_NAME.equals(village.getName())
                && player.getMainHandItem().is(Items.DIAMOND)
                && player.getInventory().getItem(1).is(Items.WHEAT_SEEDS)
                && CookwareManager.isCookware(pot) && CookwareManager.getRemainingUses(pot) == 250;
    }

    private static void begin(Minecraft minecraft, int next) {
        stage = next;
        stableFrames = 0;
        stageStarted = System.nanoTime();
        waitStarted = stageStarted;
        lastProgress = stageStarted;
        lastFrameGuard = "waiting for extraction";
        capturing = false;
        waitingAck = false;
        clearFrame();
        clearTransientUi(minecraft);
        tooltipLines = List.of();
        if (stage == 1 || stage == 2) {
            inventory = new HoverInventory(minecraft, stage);
            minecraft.gui.setScreen(inventory);
        } else if (stage == 3) {
            minecraft.gui.setScreen(null);
            var weather = minecraft.options.getSoundSourceOptionInstance(SoundSource.WEATHER);
            originalWeather = weather.get();
            weather.set(0.625);
            minecraft.options.save();
            requireWeather(minecraft, 0.625);
            minecraft.pauseGame(false);
            require(minecraft.gui.screen() instanceof PauseScreen, "Real pause menu did not open");
            requireWeather(minecraft, 0.0);
            VisualAcceptance.report("SOURCE pause=real PauseScreen; weather option and options.txt=0.0 (was 0.625)");
        } else if (stage >= 4) {
            VisualIntegrationProbe.begin(minecraft, stage);
        }
        VisualAcceptance.report("CLIENT_STAGE_BEGIN index=" + stage + " name=" + STAGES[stage]);
    }

    private static void clearFrame() {
        hud = false;
        borders = false;
        tooltip = false;
    }

    private static void clearTransientUi(Minecraft minecraft) {
        minecraft.gui.hud.getChat().clearMessages(true);
        minecraft.gui.toastManager().clear();
        minecraft.gui.hud.clearTitles();
        minecraft.gui.hud.setOverlayMessage(Component.empty(), false);
    }

    private static String fixtureDiagnostics(Minecraft minecraft) {
        var player = minecraft.player;
        var level = minecraft.level;
        if (player == null || level == null) return "player/level absent";
        var data = ModEvents.getPlayerDatabase(level).getData(player.getUUID());
        var claim = ModEvents.getChunkDataDatabase(level).getData(ChunkPos.pack(player.blockPosition()));
        var villages = ModEvents.getVillageDatabase(level);
        var pot = player.getInventory().getItem(2);
        return "coins=" + (data == null ? "missing" : data.getCoins())
                + " home=" + (data == null ? "missing" : data.getHomeVillageUUID())
                + " claim=" + (claim == null ? "missing" : claim.getType() + "/" + claim.getPlotLevel())
                + " villages=" + villages.getSize() + " expectedVillage=" + (villages.getData(VisualAcceptance.VILLAGE) != null)
                + " diamond=" + player.getMainHandItem().is(Items.DIAMOND)
                + " seeds=" + player.getInventory().getItem(1).is(Items.WHEAT_SEEDS)
                + " pot=" + CookwareManager.isCookware(pot)
                + " potUses=" + (CookwareManager.isCookware(pot) ? CookwareManager.getRemainingUses(pot) : -1)
                + " position=" + player.position() + " yaw=" + player.getYRot() + " pitch=" + player.getXRot()
                + " screen=" + (minecraft.gui.screen() == null ? "none" : minecraft.gui.screen().getClass().getSimpleName());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void hud(RenderGuiLayerEvent.Post event) {
        if (!active() || stage != 0 || event.getLayer() != PlayerDataOverlay.HUD_PLAYER_DATA) return;
        try {
            var minecraft = Minecraft.getInstance();
            if (!fixtureReady(minecraft) || minecraft.gui.hud.isHidden()) return;
            hudLines = VisualHudProbe.lines(minecraft);
            hud = true;
        } catch (RuntimeException error) {
            fail(error);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void borders(ExtractLevelRenderStateEvent event) {
        if (!active() || stage != 0) return;
        try {
            borders = VisualBorderProbe.hasClaimWalls(event);
        } catch (RuntimeException error) {
            fail(error);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void tooltip(RenderTooltipEvent.Texture event) {
        if (!active() || (stage != 1 && stage != 2)) return;
        tooltip |= stage == 1 ? event.getItemStack().is(Items.WHEAT_SEEDS)
                : CookwareManager.isCookware(event.getItemStack());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void frameEnd(RenderFrameEvent.Post event) {
        if (!active()) {
            clearFrame();
            return;
        }
        try {
            var minecraft = Minecraft.getInstance();
            boolean synchronizedFixture = fixtureReady(minecraft);
            boolean ready = synchronizedFixture && switch (stage) {
                case 0 -> minecraft.gui.screen() == null && hud && borders
                        && Math.abs(minecraft.player.getX() - 4.5) < 0.1
                        && Math.abs(minecraft.player.getY() - 92) < 0.1
                        && Math.abs(minecraft.player.getZ() - 4.5) < 0.1
                        && Math.abs(Math.IEEEremainder(minecraft.player.getYRot() + 45, 360)) < 0.1
                        && Math.abs(minecraft.player.getXRot() - 38) < 0.1;
                case 1 -> minecraft.gui.screen() == inventory && tooltip
                        && tooltipLines.stream().anyMatch(line -> line.equals("Requires: farm plot"))
                        && tooltipLines.stream().anyMatch(line -> line.contains("Biome rules apply"));
                case 2 -> minecraft.gui.screen() == inventory && tooltip && !tooltipLines.isEmpty()
                        && cookwareBar(minecraft.player.getInventory().getItem(2));
                case 3 -> minecraft.gui.screen() instanceof PauseScreen
                        && minecraft.options.getSoundSourceOptionInstance(SoundSource.WEATHER).get() == 0.0;
                case 4, 5, 6, 7, 8 -> VisualIntegrationProbe.ready(minecraft, stage);
                default -> false;
            };
            lastFrameGuard = "fixture=" + synchronizedFixture + " hud=" + hud + " borders=" + borders
                    + " tooltip=" + tooltip + " tooltipLines=" + tooltipLines + " ready=" + ready
                    + (stage >= 4 ? "; " + VisualIntegrationProbe.detail() : "");
            stableFrames = ready ? stableFrames + 1 : 0;
            long settleNanos = stage == 0 ? 3_000_000_000L : 1_000_000_000L;
            if (stableFrames >= 5 && System.nanoTime() - stageStarted >= settleNanos) capture(minecraft);
        } catch (RuntimeException error) {
            fail(error);
        } finally {
            // In 26.2 extraction precedes RenderFrameEvent.Pre; clear only AFTER consuming this frame.
            clearFrame();
        }
    }

    private static boolean active() {
        return VisualAcceptance.enabled() && !finished && stage >= 0 && !capturing && !waitingAck;
    }

    private static boolean cookwareBar(ItemStack pot) {
        require(CookwareManager.getRemainingUses(pot) == 250 && CookwareManager.getMaxUsesForDisplay(pot) == 500,
                "Unexpected synchronized cookware uses");
        require(pot.isBarVisible() && pot.getBarWidth() == 7, "Production cookware durability mixin/bar missing");
        return true;
    }

    private static void capture(Minecraft minecraft) {
        capturing = true;
        int capturedStage = stage;
        VisualAcceptance.report("SOURCE stage=" + capturedStage + " HUD=" + hudLines + " tooltip=" + tooltipLines
                + (stage == 0 ? " extractedWalls=4 blue fullLevelHeight; production HUD layer completed" : "")
                + (stage == 0 ? "; observerFeet=4.5,92,4.5 yaw=-45 pitch=38;"
                        + " chat/toasts/titles cleared through public HUD/ToastManager APIs;"
                        + " wall pixel visibility requires manual review" : "")
                + (stage == 2 ? " potUses=250/500 barVisible=true barWidth=7" : "")
                + (stage >= 4 ? "; " + VisualIntegrationProbe.detail() : ""));
        // Post-frame: this reads the rendered game target, never the desktop or a synthetic image.
        Screenshot.takeScreenshot(minecraft.gameRenderer.mainRenderTarget(), image -> {
            try (image) {
                int width = image.getWidth();
                int height = image.getHeight();
                require(width >= 320 && height >= 180, "Framebuffer too small");
                var colors = new java.util.HashSet<Integer>();
                for (int y = 0; y < height; y += Math.max(1, height / 32)) {
                    for (int x = 0; x < width; x += Math.max(1, width / 32)) colors.add(image.getPixel(x, y));
                }
                require(colors.size() > 8, "Framebuffer appears blank; not a visual PASS");
                var path = VisualAcceptance.directory().resolve("visual-shots").resolve(STAGES[capturedStage] + ".png");
                require(!Files.exists(path), "Refusing to overwrite an existing screenshot");
                image.writeToFile(path);
                byte[] bytes = Files.readAllBytes(path);
                require(bytes.length > 100 && bytes[0] == (byte) 0x89 && bytes[1] == 'P'
                        && bytes[2] == 'N' && bytes[3] == 'G', "Screenshot was not persisted as PNG");
                String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
                VisualAcceptance.report("FRAMEBUFFER stage=" + capturedStage + " file=" + path.getFileName()
                        + " dimensions=" + width + "x" + height + " bytes=" + bytes.length + " sha256=" + sha
                        + "; VISUAL_JUDGMENT=PENDING");
                minecraft.execute(() -> captured(minecraft, capturedStage));
            } catch (java.io.IOException | java.security.NoSuchAlgorithmException | RuntimeException error) {
                minecraft.execute(() -> fail(error));
            }
        });
    }

    private static void captured(Minecraft minecraft, int capturedStage) {
        if (finished) return;
        try {
            require(stage == capturedStage && capturing && !waitingAck, "Unexpected screenshot callback");
            if (stage == 3) {
                minecraft.gui.setScreen(null);
                requireWeather(minecraft, 0.625);
                VisualAcceptance.report("SOURCE real pause closed; weather option and options.txt restored=0.625;"
                        + " audible weather not asserted; multiplayer server is not paused");
                restoreWeather(minecraft);
            }
            if (stage >= 4) VisualIntegrationProbe.captured(stage);
            waitingAck = true;
            int mask = (1 << (stage + 1)) - 1;
            VisualAcceptance.report("CLIENT_RECEIPT stage=" + stage + " mask=" + mask + " PNG persisted");
            ClientPacketDistributor.sendToServer(new VisualAcceptance.Receipt(mask));
        } catch (RuntimeException error) {
            fail(error);
        }
    }

    public static void acknowledge(int mask) {
        if (!VisualAcceptance.enabled() || finished) return;
        try {
            if (mask == 0) {
                require(readySent && stage == -1 && !fixtureBarrier, "Unexpected fixture barrier");
                fixtureBarrier = true;
                VisualAcceptance.report("CLIENT_FIXTURE_BARRIER mask=0; fresh production full snapshots precede this marker");
                return;
            }
            require(waitingAck && mask == (1 << (stage + 1)) - 1, "Unexpected server stage acknowledgement");
            VisualAcceptance.report("CLIENT_ACK mask=" + mask);
            var minecraft = Minecraft.getInstance();
            if (stage == STAGES.length - 1) {
                VisualAcceptance.report("MACHINE_COMPLETE nine persisted framebuffer PNGs and ordered server ACKs;"
                        + " VISUAL_JUDGMENT=PENDING; manual PNG review required; no visual PASS");
                finished = true;
                VisualAcceptance.clientComplete();
                minecraft.stop();
            } else {
                begin(minecraft, stage + 1);
            }
        } catch (RuntimeException error) {
            fail(error);
        }
    }

    private static void requireWeather(Minecraft minecraft, double expected) {
        require(minecraft.options.getSoundSourceOptionInstance(SoundSource.WEATHER).get() == expected,
                "Weather option did not become " + expected);
        try {
            require(Files.readAllLines(minecraft.options.getFile().toPath()).stream()
                            .anyMatch(line -> line.startsWith("soundCategory_weather:")
                                    && Double.parseDouble(line.substring(line.indexOf(':') + 1)) == expected),
                    "Weather option was not persisted as " + expected);
        } catch (java.io.IOException error) {
            throw new IllegalStateException("Cannot inspect disposable options.txt", error);
        }
    }

    private static void restoreWeather(Minecraft minecraft) {
        if (originalWeather == null) return;
        minecraft.options.getSoundSourceOptionInstance(SoundSource.WEATHER).set(originalWeather);
        minecraft.options.save();
        originalWeather = null;
    }

    @SubscribeEvent
    public static void connected(ClientPlayerNetworkEvent.LoggingIn event) {
        if (VisualAcceptance.enabled()) joined = true;
    }

    @SubscribeEvent
    public static void disconnected(ClientPlayerNetworkEvent.LoggingOut event) {
        if (!VisualAcceptance.enabled() || finished) return;
        // ConnectScreen tears down the old (possibly absent) world before its first connection.
        if (!joined && event.getPlayer() == null) {
            VisualAcceptance.report("CLIENT_PRECONNECT_TEARDOWN ignored; no logged-in player yet");
            return;
        }
        fail(new IllegalStateException("Disconnected before final ACK"));
    }

    private static void fail(Exception error) {
        if (finished) return;
        finished = true;
        var minecraft = Minecraft.getInstance();
        try {
            VisualAcceptance.report("FAIL client stage=" + stage + " " + error
                    + "; MACHINE=INCOMPLETE; VISUAL_JUDGMENT=PENDING; no visual PASS");
        } finally {
            try {
                minecraft.gui.setScreen(null);
                restoreWeather(minecraft);
            } finally {
                try {
                    VisualIntegrationProbe.cleanupOnFailure();
                } finally {
                    minecraft.stop();
                }
            }
        }
    }

    /** Only fixes the hover coordinate. Vanilla inventory, tooltips, icons and mod hooks render normally. */
    private static final class HoverInventory extends InventoryScreen {
        private final int hotbarSlot;

        private HoverInventory(Minecraft minecraft, int hotbarSlot) {
            super(minecraft.player);
            this.hotbarSlot = hotbarSlot;
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            var slot = getMenu().slots.get(36 + hotbarSlot);
            super.extractRenderState(graphics, getLeftPos() + slot.x + 8, getTopPos() + slot.y + 8, partialTick);
        }

        @Override
        protected List<Component> getTooltipFromContainerItem(ItemStack stack) {
            var lines = super.getTooltipFromContainerItem(stack);
            tooltipLines = lines.stream().map(Component::getString).toList();
            return lines;
        }
    }
}
