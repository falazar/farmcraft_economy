package com.falazar.farmupcraft.database.backup;

import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class BackupScheduler {
    private BackupScheduler() {}

    public static void onTick(ServerTickEvent.Post event) {
        processServer(event.getServer());
    }

    static void processServer(MinecraftServer server) {
        var level = server.overworld();
        if (level != null) {
            BackupQueue.processQueue(level);
        }
    }
}
