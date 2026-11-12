package com.falazar.farmupcraft.client;

import com.falazar.farmupcraft.database.DataBaseManager;
import com.falazar.farmupcraft.database.message.DataBaseEntryS2C;
import com.falazar.farmupcraft.database.message.DataBaseFullS2C;
import com.falazar.farmupcraft.database.message.DataBasePayloadHandler;
import com.falazar.farmupcraft.database.message.AddJMWaypointPacket;
import com.falazar.farmupcraft.database.message.RemoveModWaypointsPacket;
import com.falazar.farmupcraft.database.message.ScanWaypointsPacket;
import com.falazar.farmupcraft.database.message.HighlightChunkPacket;
import com.falazar.farmupcraft.database.message.ShowVillageChunksPacket;
import com.falazar.farmupcraft.database.message.OpenJeiRecipePacket;
import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

@EventBusSubscriber(modid = FarmUpCraftNeoForge.MOD_ID, value = Dist.CLIENT)
public final class ClientDatabaseEvents {
        private ClientDatabaseEvents() {
        }

        @SubscribeEvent
        public static void registerPayloadHandlers(RegisterClientPayloadHandlersEvent event) {
                event.register(DataBaseEntryS2C.TYPE, DataBasePayloadHandler::handleEntry);
                event.register(DataBaseFullS2C.TYPE, DataBasePayloadHandler::handleFull);
                event.register(AddJMWaypointPacket.TYPE, WaypointPayloadHandler::handleAdd);
                event.register(RemoveModWaypointsPacket.TYPE, WaypointPayloadHandler::handleRemove);
                event.register(ScanWaypointsPacket.TYPE, WaypointPayloadHandler::handleScan);
                event.register(HighlightChunkPacket.TYPE, CommandPayloadHandler::handleHighlight);
                event.register(ShowVillageChunksPacket.TYPE, CommandPayloadHandler::handleVillage);
                event.register(OpenJeiRecipePacket.TYPE, CommandPayloadHandler::handleRecipe);
                event.register(com.falazar.farmupcraft.database.message.NpcSpeechPacket.TYPE,
                                (packet, context) -> NpcSpeechBubbles.show(packet));
                event.register(com.falazar.farmupcraft.database.message.MarketSaleReceiptPacket.TYPE,
                                (packet, context) -> MarketScreen.showSaleReceipt(packet.coins()));
                event.register(com.falazar.farmupcraft.database.message.MarketViewPacket.TYPE,
                                (packet, context) -> MarketScreen.open(packet.view()));
                event.register(com.falazar.farmupcraft.database.message.VillageViewPacket.TYPE,
                                (packet, context) -> VillageInfoScreen.open(packet.view()));
                event.register(com.falazar.farmupcraft.database.message.VillageLevelUpResultPacket.TYPE,
                                (packet, context) -> VillageOverviewScreen.showLevelUpResult(packet.message(),
                                                packet.success()));
                event.register(com.falazar.farmupcraft.database.message.VillageStructuresViewPacket.TYPE,
                                (packet, context) -> VillageStructuresScreen.open(packet.view()));
                event.register(com.falazar.farmupcraft.database.message.VillageListViewPacket.TYPE,
                                (packet, context) -> VillageListScreen.open(packet.view()));
                event.register(com.falazar.farmupcraft.database.message.PlotViewPacket.TYPE,
                                (packet, context) -> PlotInfoScreen.open(packet.view()));
                event.register(com.falazar.farmupcraft.database.message.BuildPreviewPacket.TYPE,
                                (packet, context) -> BuildPreviewRenderer.receive(packet));
        }

        @SubscribeEvent
        public static void loggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
                DataBaseManager.clearClientCaches();
                WaypointPayloadHandler.loggedOut();
                CommandPayloadHandler.loggedOut();
                BuildPreviewRenderer.clear();
        }
}
