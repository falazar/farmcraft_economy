package com.falazar.farmupcraft.client;

import com.falazar.farmupcraft.database.message.NpcSpeechPacket;
import com.falazar.farmupcraft.networktest.PlayerActionAcceptance;
import com.falazar.farmupcraft.networktest.SpeechBubbleAcceptance;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

import java.io.IOException;
import java.util.List;

@EventBusSubscriber(modid = "farmupcraft", value = Dist.CLIENT)
public final class SpeechBubbleAcceptanceClient {
    private static int stage;
    private static long started;
    private static long deadline;
    private static Villager npc;
    private static Display.TextDisplay original;
    private static int captures;
    private static String screenshot;
    private static boolean failed;
    private static long npcSeenAt = -1;

    private static List<Display.TextDisplay> bubbles() {
        var level = Minecraft.getInstance().level;
        return level.getEntitiesOfClass(Display.TextDisplay.class, npc.getBoundingBox().inflate(3))
                .stream().filter(display -> display.getId() < 0 && !display.isRemoved()).toList();
    }

    private static void require(boolean condition, String reason) {
        if (!condition) throw new IllegalStateException(reason);
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!SpeechBubbleAcceptance.enabled() || failed || stage == 9) return;
        var minecraft = Minecraft.getInstance();
        if (deadline == 0) deadline = System.nanoTime() + 180_000_000_000L;
        try {
            require(System.nanoTime() < deadline, "Speech probe timed out at stage " + stage);
            if (minecraft.level == null || minecraft.player == null || minecraft.gameMode == null) return;
            if (minecraft.gui.screen() != null) minecraft.gui.setScreen(null);
            minecraft.gui.hud.getChat().clearMessages(true);
            minecraft.gui.toastManager().clear();
            minecraft.gui.hud.clearTitles();
            if (stage == 0) {
                var villagers = minecraft.level.getEntitiesOfClass(Villager.class,
                        minecraft.player.getBoundingBox().inflate(8));
                npc = villagers.stream().filter(v -> "BubbleProbe".equals(v.getName().getString()))
                        .findFirst().orElse(null);
                if (npc == null) return;
                if (npcSeenAt < 0) npcSeenAt = minecraft.level.getGameTime();
                if (minecraft.level.getGameTime() - npcSeenAt < 60) return;
                minecraft.player.setYRot(0);
                minecraft.player.setXRot(-12);
                PlayerActionAcceptance.report("START real interaction at " + minecraft.player.position()
                        + " NPC=" + npc.position());
                minecraft.gameMode.interact(minecraft.player, npc, new EntityHitResult(npc), InteractionHand.MAIN_HAND);
                stage = 1;
                deadline = System.nanoTime() + 30_000_000_000L;
                return;
            }
            if (stage == 1) {
                if (bubbles().isEmpty()) return;
                original = bubbles().getFirst();
                deadline = System.nanoTime() + 30_000_000_000L;
                started = minecraft.level.getGameTime();
                stage = 2;
            }
            long age = minecraft.level.getGameTime() - started;
            if (stage == 2 && age >= 10) {
                require(bubbles().size() == 1, "Greeting did not create exactly one bubble");
                require(original.renderState().billboardConstraints() == Display.BillboardConstraints.CENTER,
                        "Bubble must face each viewer's camera");
                require(original.textRenderState().text().getString().contains("Hello, I am BubbleProbe!"),
                        "Real NPC greeting packet was not rendered");
                require((original.textRenderState().flags() & Display.TextDisplay.FLAG_SEE_THROUGH) == 0,
                        "Bubble must respect depth/walls");
                screenshot = "speech-front.png";
                PlayerActionAcceptance.report("OBSERVED production right-click greeting; camera-facing depth-tested text");
                stage = 3;
            }
            if (stage == 3 && captures >= 1 && age >= 25) {
                NpcSpeechBubbles.show(NpcSpeechPacket.speech(npc.getUUID(),
                        "Lovely day for a walk! The wheat is looking healthy. "
                                + "Would you help bring some bread to the village today?"));
                require(original.isRemoved(), "Replacing speech left the old bubble alive");
                original = bubbles().getFirst();
                started = minecraft.level.getGameTime();
                stage = 4;
                return;
            }
            if (stage == 4 && age >= 12) {
                require(bubbles().size() == 1, "Replacement stacked speech");
                require(original.textRenderState().text().getString().lines().count() <= 3,
                        "Speech exceeded three lines");
                minecraft.player.setPos(35.5, 81, 35.5);
                minecraft.player.setYRot(90);
                minecraft.player.setXRot(-12);
                stage = 5;
            }
            if (stage == 5 && age >= 30) {
                screenshot = "speech-side.png";
                PlayerActionAcceptance.report("OBSERVED replacement; <=3 lines; second camera angle; old display removed");
                stage = 6;
            }
            if (stage == 6 && captures >= 2 && age >= 310) {
                int opacity = original.textRenderState().textOpacity().get(1) & 255;
                require(opacity >= 4 && opacity < 200, "Last-second text fade did not occur: " + opacity);
                screenshot = "speech-fading.png";
                stage = 7;
            }
            if (stage == 7 && captures >= 3 && age >= 325) {
                require(bubbles().isEmpty() && original.isRemoved(), "Bubble did not expire after sixteen seconds");
                NpcSpeechBubbles.show(NpcSpeechPacket.speech(npc.getUUID(), "Distance cleanup test"));
                original = bubbles().getFirst();
                minecraft.player.setPos(npc.getX() + 17, 81, npc.getZ());
                stage = 8;
            }
            if (stage == 8 && age >= 330) {
                require(original.isRemoved() && bubbles().isEmpty(), "Distant bubble did not get removed");
                PlayerActionAcceptance.report("PASS speech production packet, billboard, depth flags, replacement, "
                        + "3-line bound, fade, expiry, distance cleanup; three actual framebuffer screenshots");
                stage = 9;
                minecraft.stop();
            }
        } catch (RuntimeException error) {
            failed = true;
            PlayerActionAcceptance.report("FAIL " + error);
            minecraft.stop();
        }
    }

    @SubscribeEvent
    public static void frame(RenderFrameEvent.Post event) {
        if (!SpeechBubbleAcceptance.enabled() || screenshot == null || failed) return;
        String file = screenshot;
        screenshot = null;
        Screenshot.takeScreenshot(Minecraft.getInstance().gameRenderer.mainRenderTarget(), image -> {
            try (image) {
                image.writeToFile(PlayerActionAcceptance.directory().resolve(file));
                captures++;
            } catch (IOException error) {
                failed = true;
                PlayerActionAcceptance.report("FAIL screenshot: " + error);
                Minecraft.getInstance().execute(Minecraft.getInstance()::stop);
            }
        });
    }
}
