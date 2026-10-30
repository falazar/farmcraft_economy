package com.falazar.farmupcraft.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ClientUtils {
    public static final List<UUID> blockedEntityRenders = new ArrayList<>();

    private ClientUtils() {}

    public static Player getClientPlayer() {
        return getMinecraft().player;
    }

    public static ClientLevel getClientLevel() {
        return getMinecraft().level;
    }

    public static Level getLevel() {
        return getClientLevel();
    }

    public static Minecraft getMinecraft() {
        return Minecraft.getInstance();
    }

    public static boolean isFirstPersonPlayer(Entity entity) {
        Minecraft minecraft = getMinecraft();
        return entity != null && entity == minecraft.getCameraEntity()
                && minecraft.options.getCameraType().isFirstPerson();
    }

    public static void blockRenderingEntity(UUID id) {
        blockedEntityRenders.add(id);
    }

    public static void releaseRenderingEntity(UUID id) {
        blockedEntityRenders.remove(id);
    }
}
