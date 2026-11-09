package com.falazar.farmupcraft.networktest;

import com.falazar.farmupcraft.GemContent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Set;

@EventBusSubscriber(modid = "farmupcraft")
public final class GemRenderAcceptance {
    private static ServerPlayer pending;
    private static int delay;

    public static boolean enabled() {
        return Boolean.getBoolean("farmupcraft.gemRenderAcceptance");
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
            throw new IllegalStateException("Gem render participant left before setup");
        }
        if (!player.level().getServer().isDedicatedServer()) {
            throw new IllegalStateException("Gem render probe requires disposable dedicated peers");
        }
        for (int x = 27; x <= 39; x++) {
            for (int z = 27; z <= 39; z++) {
                player.level().setBlockAndUpdate(new BlockPos(x, 80, z), Blocks.STONE.defaultBlockState());
            }
        }
        for (int i = 0; i < GemContent.TYPES.size(); i++) {
            String gem = GemContent.TYPES.get(i);
            player.level().setBlockAndUpdate(new BlockPos(30 + i, 81, 36), GemContent.ore(gem, false).defaultBlockState());
            player.level().setBlockAndUpdate(new BlockPos(30 + i, 82, 36), GemContent.ore(gem, true).defaultBlockState());
            player.getInventory().setItem(i, new ItemStack(GemContent.gem(gem)));
        }
        player.setGameMode(GameType.CREATIVE);
        player.teleportTo(player.level(), 32.5, 81, 30.5, Set.of(), 0, 0, true);
        PlayerActionAcceptance.report("PASS server seeded all five gem items and ten ore variants");
    }
}
