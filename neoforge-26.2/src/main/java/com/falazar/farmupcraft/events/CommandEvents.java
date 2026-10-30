package com.falazar.farmupcraft.events;

import com.falazar.farmupcraft.command.*;
import com.falazar.farmupcraft.database.DataBaseCommand;
import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = FarmUpCraftNeoForge.MOD_ID)
public final class CommandEvents {
    private CommandEvents() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        var dispatcher = event.getDispatcher();
        ShowBiomesCommand.register(dispatcher);
        ManagersCommand.register(dispatcher);
        DataBaseCommand.register(dispatcher);
        PlayerCommand.register(dispatcher);
        PlotCommand.register(dispatcher);
        VillageCommand.register(dispatcher);
        MarketCommand.register(dispatcher);
        FarmCraftCommand.register(dispatcher);
        BuildCommand.register(dispatcher);
        TesterCommand.register(dispatcher);
        NpcCommand.register(dispatcher);
        StructureCommand.register(dispatcher);
        ZoneCommand.register(dispatcher);
        AiCommand.register(dispatcher);
        ScanCommand.register(dispatcher);
        LegacySavedDataCommand.register(dispatcher);
    }
}
