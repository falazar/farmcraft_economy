package com.falazar.farmupcraft.client;

import com.falazar.farmupcraft.GemContent;
import com.falazar.farmupcraft.networktest.GemRenderAcceptance;
import com.falazar.farmupcraft.networktest.PlayerActionAcceptance;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

import java.io.IOException;

@EventBusSubscriber(modid = "farmupcraft", value = Dist.CLIENT)
public final class GemRenderAcceptanceClient {
    private static long deadline;
    private static long seenAt = -1;
    private static boolean capture;
    private static boolean finished;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!GemRenderAcceptance.enabled() || finished) return;
        var minecraft = Minecraft.getInstance();
        if (deadline == 0) deadline = System.nanoTime() + 180_000_000_000L;
        try {
            if (System.nanoTime() > deadline) throw new IllegalStateException("Gem render probe timed out");
            if (minecraft.level == null || minecraft.player == null || minecraft.gameMode == null) return;
            if (minecraft.gui.screen() != null) minecraft.gui.setScreen(null);
            minecraft.gui.hud.getChat().clearMessages(true);
            minecraft.gui.toastManager().clear();
            minecraft.gui.hud.clearTitles();
            for (int i = 0; i < GemContent.TYPES.size(); i++) {
                String gem = GemContent.TYPES.get(i);
                if (!minecraft.level.getBlockState(new BlockPos(30 + i, 81, 36)).is(GemContent.ore(gem, false))
                        || !minecraft.level.getBlockState(new BlockPos(30 + i, 82, 36)).is(GemContent.ore(gem, true))
                        || !minecraft.player.getInventory().getItem(i).is(GemContent.gem(gem))) return;
                for (String resource : new String[]{"textures/item/" + gem + ".png",
                        "textures/block/" + gem + "_ore.png", "textures/block/deepslate_" + gem + "_ore.png",
                        "items/" + gem + ".json", "models/item/" + gem + ".json"}) {
                    if (minecraft.getResourceManager().getResource(Identifier.fromNamespaceAndPath("farmupcraft", resource)).isEmpty()) {
                        throw new IllegalStateException("Missing gem render resource " + resource);
                    }
                }
            }
            if (seenAt < 0) {
                if (Boolean.getBoolean("farmupcraft.soundRetryAcceptance")) verifySoundRetry(minecraft);
                seenAt = minecraft.level.getGameTime();
            }
            minecraft.player.setYRot(0);
            minecraft.player.setXRot(0);
            if (minecraft.level.getGameTime() - seenAt >= 60) capture = true;
        } catch (RuntimeException error) {
            finished = true;
            PlayerActionAcceptance.report("FAIL gem render " + error);
            minecraft.stop();
        }
    }

    private static void verifySoundRetry(Minecraft minecraft) {
        try {
            var manager = minecraft.getSoundManager();
            var engineField = manager.getClass().getDeclaredField("soundEngine");
            engineField.setAccessible(true);
            var engine = engineField.get(manager);
            var loadedField = engine.getClass().getDeclaredField("loaded");
            loadedField.setAccessible(true);
            var retryField = engine.getClass().getDeclaredField("farmupcraft$audioRetry");
            retryField.setAccessible(true);
            var retry = (SoundRetryCooldown) retryField.get(engine);
            var shouldChange = engine.getClass().getDeclaredMethod("shouldChangeDevice");
            shouldChange.setAccessible(true);
            boolean wasLoaded = loadedField.getBoolean(engine);
            try {
                loadedField.setBoolean(engine, false);
                retry.recordAttempt(false, System.nanoTime());
                for (int i = 0; i < 1000; i++) {
                    if (!Boolean.FALSE.equals(shouldChange.invoke(engine))) {
                        throw new IllegalStateException("Failed sound retried during cooldown");
                    }
                }
                retry.recordAttempt(false, System.nanoTime() - java.util.concurrent.TimeUnit.MINUTES.toNanos(5) - 1_000_000);
                if (!Boolean.TRUE.equals(shouldChange.invoke(engine))) {
                    throw new IllegalStateException("Hourly sound retry did not become due");
                }
            } finally {
                loadedField.setBoolean(engine, wasLoaded);
            }
            // Exercise an actual manual reload while the automatic cooldown is active.
            retry.recordAttempt(false, System.nanoTime());
            var attemptTime = SoundRetryCooldown.class.getDeclaredField("failedAt");
            attemptTime.setAccessible(true);
            long before = attemptTime.getLong(retry);
            manager.reload();
            if (attemptTime.getLong(retry) == before || retry.hasFailed() == loadedField.getBoolean(engine)) {
                throw new IllegalStateException("Manual reload did not update cooldown from actual load result");
            }
            PlayerActionAcceptance.report("PASS sound retry transformed client: 1000 suppressed checks, five-minute retry, manual reload");
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot verify transformed sound retry mixin", error);
        }
    }

    @SubscribeEvent
    public static void frame(RenderFrameEvent.Post event) {
        if (!GemRenderAcceptance.enabled() || !capture || finished) return;
        capture = false;
        finished = true;
        Screenshot.takeScreenshot(Minecraft.getInstance().gameRenderer.mainRenderTarget(), image -> {
            try (image) {
                image.writeToFile(PlayerActionAcceptance.directory().resolve("gem-render.png"));
                PlayerActionAcceptance.report("PASS gem render synchronized five items, ten blocks, loaded assets, framebuffer screenshot");
            } catch (IOException error) {
                PlayerActionAcceptance.report("FAIL gem render screenshot " + error);
            } finally {
                Minecraft.getInstance().stop();
            }
        });
    }
}
