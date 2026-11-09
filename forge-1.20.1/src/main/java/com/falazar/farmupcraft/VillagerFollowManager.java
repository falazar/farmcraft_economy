package com.falazar.farmupcraft;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;

public final class VillagerFollowManager {
    public static final String FOLLOW_PLAYER_KEY = "farmupcraft_follow_player";

    private VillagerFollowManager() {}

    public static void toggle(ServerPlayer player, Villager villager) {
        String owner = villager.getPersistentData().getString(FOLLOW_PLAYER_KEY);
        if (!owner.isEmpty() && !owner.equals(player.getUUID().toString())) {
            player.sendSystemMessage(Component.literal(villager.getName().getString()
                    + " is already following another player."));
            return;
        }
        NpcManager.clearConversation(player.getUUID());
        villager.getNavigation().stop();
        if (owner.isEmpty()) {
            villager.getPersistentData().putString(FOLLOW_PLAYER_KEY, player.getUUID().toString());
            if (villager.isSleeping()) villager.stopSleeping();
        } else {
            villager.getPersistentData().remove(FOLLOW_PLAYER_KEY);
        }
        player.sendSystemMessage(Component.literal(villager.getName().getString()
                + (owner.isEmpty() ? " will follow you. Right-click with bread again to stop."
                    : " stopped following you.")));
    }

    public static boolean tickFollowing(Villager villager) {
        if (!(villager.level() instanceof ServerLevel level)) return false;
        String owner = villager.getPersistentData().getString(FOLLOW_PLAYER_KEY);
        if (owner.isEmpty()) return false;
        ServerPlayer player = level.getServer().getPlayerList().getPlayers().stream()
                .filter(candidate -> candidate.getUUID().toString().equals(owner))
                .findFirst().orElse(null);
        if (player == null || !player.isAlive() || player.level() != level
                || player.distanceToSqr(villager) > 48 * 48 || villager.getTradingPlayer() != null) {
            villager.getNavigation().stop();
            return true;
        }
        if (villager.isSleeping()) villager.stopSleeping();
        villager.getLookControl().setLookAt(player, 30, 30);
        if (villager.distanceToSqr(player) <= 4) {
            villager.getNavigation().stop();
        } else if (villager.tickCount % 10 == 0 || villager.getNavigation().isDone()) {
            villager.getNavigation().moveTo(player, 0.6);
        }
        return true;
    }
}
