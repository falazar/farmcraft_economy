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
        if (!GemRenderAcceptance.enabled() || finished)
            return;
        var minecraft = Minecraft.getInstance();
        if (deadline == 0)
            deadline = System.nanoTime() + 180_000_000_000L;
        try {
            if (System.nanoTime() > deadline)
                throw new IllegalStateException("Gem render probe timed out");
            if (minecraft.level == null || minecraft.player == null || minecraft.gameMode == null)
                return;
            if (minecraft.gui.screen() != null && (seenAt < 0 || !Boolean.getBoolean("farmupcraft.mapFilterAcceptance")))
                minecraft.gui.setScreen(null);
            minecraft.gui.hud.getChat().clearMessages(true);
            minecraft.gui.toastManager().clear();
            minecraft.gui.hud.clearTitles();
            for (int i = 0; i < GemContent.TYPES.size(); i++) {
                String gem = GemContent.TYPES.get(i);
                if (!minecraft.level.getBlockState(new BlockPos(30 + i, 81, 36)).is(GemContent.ore(gem, false))
                        || !minecraft.level.getBlockState(new BlockPos(30 + i, 82, 36)).is(GemContent.ore(gem, true))
                        || !minecraft.player.getInventory().getItem(i).is(GemContent.gem(gem)))
                    return;
                for (String resource : new String[] { "textures/item/" + gem + ".png",
                        "textures/block/" + gem + "_ore.png", "textures/block/deepslate_" + gem + "_ore.png",
                        "items/" + gem + ".json", "models/item/" + gem + ".json" }) {
                    if (minecraft.getResourceManager()
                            .getResource(Identifier.fromNamespaceAndPath("farmupcraft", resource)).isEmpty()) {
                        throw new IllegalStateException("Missing gem render resource " + resource);
                    }
                }
            }
            if (seenAt < 0) {
                if (Boolean.getBoolean("farmupcraft.soundRetryAcceptance"))
                    verifySoundRetryCooldown(minecraft);
                if (Boolean.getBoolean("farmupcraft.mapFilterAcceptance"))
                    JourneyMapFilterAcceptance.verify(minecraft);
                seenAt = minecraft.level.getGameTime();
            }
            minecraft.player.setYRot(0);
            minecraft.player.setXRot(0);
            if (Boolean.getBoolean("farmupcraft.mapFilterAcceptance")
                    && minecraft.level.getGameTime() - seenAt == 20)
                JourneyMapFilterAcceptance.openFullscreen();
            if (minecraft.level.getGameTime() - seenAt >=
                    (Boolean.getBoolean("farmupcraft.mapFilterAcceptance") ? 200 : 60))
                capture = true;
        } catch (RuntimeException error) {
            finished = true;
            PlayerActionAcceptance.report("FAIL gem render " + error);
            minecraft.stop();
        }
    }

    private static void verifySoundRetryCooldown(Minecraft minecraft) {
        try {
            var manager = minecraft.getSoundManager();
            var engineField = manager.getClass().getDeclaredField("soundEngine");
            engineField.setAccessible(true);
            var engine = engineField.get(manager);
            var throttleField = engine.getClass().getDeclaredField("farmupcraft$failureLogCooldown");
            throttleField.setAccessible(true);
            if (throttleField.get(engine) == null) {
                throw new IllegalStateException("Sound failure log throttle was not initialized");
            }
            var throttle = new SoundRetryCooldown();
            long now = System.nanoTime();
            long oneHour = java.util.concurrent.TimeUnit.HOURS.toNanos(1);
            if (!throttle.shouldLogFailure(now) || throttle.shouldLogFailure(now + 1)
                    || !throttle.shouldLogFailure(now + oneHour)) {
                throw new IllegalStateException("Sound failure warnings are not limited to one per hour");
            }
            var retry = (SoundRetryCooldown) throttleField.get(engine);
            var checkDevice = engine.getClass().getDeclaredMethod("shouldChangeDevice");
            checkDevice.setAccessible(true);
            var devicesField = engine.getClass().getDeclaredField("lastSeenDevices");
            devicesField.setAccessible(true);
            var previousDevices = devicesField.get(engine);
            try {
                devicesField.set(engine, null);
                retry.recordAttempt(false, System.nanoTime());
                if (!Boolean.FALSE.equals(checkDevice.invoke(engine)) || devicesField.get(engine) != null)
                    throw new IllegalStateException("Automatic device checks ran during the retry cooldown");
                retry.recordAttempt(false, System.nanoTime() - java.util.concurrent.TimeUnit.MINUTES.toNanos(5));
                if (!Boolean.TRUE.equals(checkDevice.invoke(engine)) && devicesField.get(engine) == null)
                    throw new IllegalStateException("Automatic device checks did not resume after the cooldown");
                retry.recordAttempt(false, System.nanoTime());
                manager.reload();
                var loadedField = engine.getClass().getDeclaredField("loaded");
                loadedField.setAccessible(true);
                if (!loadedField.getBoolean(engine) || !retry.allowsAutomaticRetry(System.nanoTime()))
                    throw new IllegalStateException("Manual sound reload did not bypass and reset the cooldown");
            } finally {
                devicesField.set(engine, previousDevices);
            }
            PlayerActionAcceptance
                    .report("PASS sound retry transformed client: automatic cooldown, due retry and manual reload");
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot verify transformed sound retry mixin", error);
        }
    }

    @SubscribeEvent
    public static void frame(RenderFrameEvent.Post event) {
        if (!GemRenderAcceptance.enabled() || !capture || finished)
            return;
        capture = false;
        finished = true;
        if (Boolean.getBoolean("farmupcraft.mapFilterAcceptance")) {
            try {
                JourneyMapFilterAcceptance.verifyFullscreen(Minecraft.getInstance());
            } catch (RuntimeException error) {
                PlayerActionAcceptance.report("FAIL map fullscreen " + error);
                Minecraft.getInstance().stop();
                return;
            }
        }
        Screenshot.takeScreenshot(Minecraft.getInstance().gameRenderer.mainRenderTarget(), image -> {
            try (image) {
                image.writeToFile(PlayerActionAcceptance.directory().resolve(
                        Boolean.getBoolean("farmupcraft.mapFilterAcceptance") ? "map-filter.png" : "gem-render.png"));
                PlayerActionAcceptance.report(
                        "PASS gem render synchronized five items, ten blocks, loaded assets, framebuffer screenshot");
            } catch (IOException error) {
                PlayerActionAcceptance.report("FAIL gem render screenshot " + error);
            } finally {
                Minecraft.getInstance().stop();
            }
        });
    }
}
