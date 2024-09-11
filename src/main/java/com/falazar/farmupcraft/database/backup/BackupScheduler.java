package com.falazar.farmupcraft.database.backup;

import com.falazar.farmupcraft.FarmUpCraft;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = FarmUpCraft.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class BackupScheduler {

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            ServerLevel serverLevel = event.getServer().overworld();
            BackupQueue.processQueue(serverLevel);
        }
    }
}
