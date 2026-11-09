package com.falazar.farmupcraft.networktest;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.BlockPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Set;

@EventBusSubscriber(modid = "farmupcraft")
public final class SpeechBubbleAcceptance {
    private static ServerPlayer pending;
    private static int delay;
    public static boolean enabled() {
        return Boolean.getBoolean("farmupcraft.speechBubbleAcceptance");
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (!enabled() || !(event.getEntity() instanceof ServerPlayer player)) return;
        pending = player;
        delay = 40;
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        if (!enabled() || pending == null || --delay > 0) return;
        var player = pending;
        pending = null;
        if (event.getServer().getPlayerList().getPlayer(player.getUUID()) != player) {
            throw new IllegalStateException("Speech participant left before fixture setup");
        }
        PlayerActionAcceptance.directory();
        if (!player.level().getServer().isDedicatedServer()) {
            throw new IllegalStateException("Speech probe requires isolated dedicated peers");
        }
        for (int x = 28; x <= 52; x++) {
            for (int z = 28; z <= 40; z++) {
                player.level().setBlockAndUpdate(new BlockPos(x, 80, z), Blocks.GRASS_BLOCK.defaultBlockState());
            }
        }
        player.setGameMode(GameType.CREATIVE);
        player.teleportTo(player.level(), 32.5, 81, 32.5, Set.of(), 0, -12, true);
        var villager = EntityTypes.VILLAGER.create(player.level(), EntitySpawnReason.COMMAND);
        if (villager == null) throw new IllegalStateException("Cannot create speech fixture villager");
        villager.setNoAi(true);
        villager.setCustomName(Component.literal("BubbleProbe"));
        villager.setCustomNameVisible(true);
        villager.setPos(32.5, 81, 35.5);
        if (!player.level().addFreshEntity(villager)) throw new IllegalStateException("Cannot add speech fixture");
        PlayerActionAcceptance.report("PASS server seeded isolated speech fixture; production NPC interactions enabled");
    }
}
