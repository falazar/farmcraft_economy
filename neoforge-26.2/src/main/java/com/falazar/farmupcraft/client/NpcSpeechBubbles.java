package com.falazar.farmupcraft.client;

import com.falazar.farmupcraft.database.message.NpcSpeechPacket;
import com.falazar.farmupcraft.mixin.SpeechDisplayAccessor;
import com.falazar.farmupcraft.mixin.SpeechTextAccessor;
import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.math.Transformation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.joml.Vector3f;

@EventBusSubscriber(modid = FarmUpCraftNeoForge.MOD_ID, value = Dist.CLIENT)
public final class NpcSpeechBubbles {
    static final int DURATION = 320;
    static final int FADE_TICKS = 20;
    static final double MAX_DISTANCE = 16;
    private static final int LINE_WIDTH = 150;
    private static final Map<UUID, Bubble> BUBBLES = new HashMap<>();
    private static ClientLevel bubbleLevel;
    private static int nextId = -1;

    private NpcSpeechBubbles() {}

    public static void show(NpcSpeechPacket packet) {
        var minecraft = Minecraft.getInstance();
        var level = minecraft.level;
        if (level != bubbleLevel) clear();
        bubbleLevel = level;
        if (level == null || minecraft.player == null) return;
        var entity = level.getEntity(packet.npc());
        if (!(entity instanceof LivingEntity npc) || !npc.isAlive()
                || minecraft.player.distanceToSqr(npc) > MAX_DISTANCE * MAX_DISTANCE) return;
        var previous = BUBBLES.remove(packet.npc());
        if (previous != null) previous.display().discard();

        var display = new Display.TextDisplay(EntityTypes.TEXT_DISPLAY, level);
        // Client-only negative IDs never enter server saves or entity tracking.
        while (level.getEntity(nextId) != null) nextId--;
        display.setId(nextId--);
        ((SpeechDisplayAccessor) display).farmupcraft$billboard(Display.BillboardConstraints.CENTER);
        ((SpeechDisplayAccessor) display).farmupcraft$transformation(
                new Transformation(null, null, new Vector3f(0.6F), null));
        var text = (SpeechTextAccessor) display;
        text.farmupcraft$text(wrapped(packet.text()));
        text.farmupcraft$lineWidth(LINE_WIDTH);
        text.farmupcraft$flags(Display.TextDisplay.FLAG_SHADOW);
        text.farmupcraft$opacity((byte) 255);
        text.farmupcraft$background(0xB0202020);
        position(display, npc);
        display.setOldPosAndRot();
        level.addEntity(display);
        BUBBLES.put(packet.npc(), new Bubble(npc, display, level.getGameTime()));
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.level != bubbleLevel || minecraft.player == null) {
            clear();
            return;
        }
        var iterator = BUBBLES.values().iterator();
        while (iterator.hasNext()) {
            var bubble = iterator.next();
            long age = bubbleLevel.getGameTime() - bubble.started();
            if (age >= DURATION || bubble.npc().isRemoved() || !bubble.npc().isAlive()
                    || bubble.display().isRemoved()
                    || minecraft.player.distanceToSqr(bubble.npc()) >= MAX_DISTANCE * MAX_DISTANCE) {
                bubble.display().discard();
                iterator.remove();
                continue;
            }
            position(bubble.display(), bubble.npc());
            float distance = minecraft.player.distanceTo(bubble.npc());
            int alpha = opacity(age, distance);
            var text = (SpeechTextAccessor) bubble.display();
            text.farmupcraft$opacity((byte) alpha);
            text.farmupcraft$background((alpha * 176 / 255 << 24) | 0x202020);
        }
    }

    static int opacity(long age, float distance) {
        float timeFade = Math.clamp((DURATION - age) / (float) FADE_TICKS, 0, 1);
        float distanceFade = Math.clamp((16 - distance) / 4, 0, 1);
        // Display text treats opacity values 0-3 as fully opaque.
        return Math.max(4, Math.round(255 * timeFade * distanceFade));
    }

    static String preview(String text) {
        String normalized = text.replaceAll("\\s+", " ").strip();
        if (normalized.length() <= 100) return normalized;
        int end = normalized.lastIndexOf(' ', 97);
        return normalized.substring(0, end >= 60 ? end : 97).stripTrailing() + "...";
    }

    private static Component wrapped(String text) {
        var font = Minecraft.getInstance().font;
        var lines = font.split(Component.literal(preview(text)), LINE_WIDTH);
        var result = new StringBuilder();
        for (int i = 0; i < Math.min(3, lines.size()); i++) {
            if (i > 0) result.append('\n');
            var line = new StringBuilder();
            lines.get(i).accept((index, style, point) -> {
                line.appendCodePoint(point);
                return true;
            });
            result.append(i == 2 && lines.size() > 3
                    ? font.plainSubstrByWidth(line.toString(), LINE_WIDTH - font.width("...")).stripTrailing() + "..."
                    : line);
        }
        return Component.literal(result.toString());
    }

    private static void position(Display.TextDisplay display, LivingEntity npc) {
        display.setPos(npc.getX(), npc.getY() + npc.getBbHeight() + 0.65, npc.getZ());
    }

    @SubscribeEvent
    public static void registerCommand(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("fbubble")
                .executes(context -> demo("Lovely day for a walk! How is your farm doing?"))
                .then(Commands.argument("text", StringArgumentType.greedyString())
                        .executes(context -> demo(StringArgumentType.getString(context, "text")))));
    }

    private static int demo(String text) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return 0;
        if (!(minecraft.hitResult instanceof EntityHitResult hit) || !(hit.getEntity() instanceof Villager npc)) {
            minecraft.player.sendSystemMessage(Component.literal("Look directly at a nearby villager, then use /fbubble."));
            return 0;
        }
        show(NpcSpeechPacket.speech(npc.getUUID(), text));
        return 1;
    }

    @SubscribeEvent
    public static void loggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }

    private static void clear() {
        BUBBLES.values().forEach(bubble -> bubble.display().discard());
        BUBBLES.clear();
        bubbleLevel = null;
    }

    private record Bubble(LivingEntity npc, Display.TextDisplay display, long started) {}
}
