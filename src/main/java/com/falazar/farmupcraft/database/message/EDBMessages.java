package com.falazar.farmupcraft.database.message;

import com.falazar.farmupcraft.FarmUpCraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.server.ServerLifecycleHooks;

public class EDBMessages {
    private static SimpleChannel INSTANCE;

    private static int packetId = 0;
    private static int id() {
        return packetId++;
    }

    public static void register() {
        SimpleChannel net = NetworkRegistry.ChannelBuilder
                .named(new ResourceLocation(FarmUpCraft.MODID, "messages"))
                .networkProtocolVersion(() -> "1.0")
                .clientAcceptedVersions(s -> true)
                .serverAcceptedVersions(s -> true)
                .simpleChannel();

        INSTANCE = net;

        net.messageBuilder(DataBaseEntryS2C.class, id(), NetworkDirection.PLAY_TO_CLIENT)
                .decoder(DataBaseEntryS2C::new)
                .encoder(DataBaseEntryS2C::toBytes)
                .consumerMainThread(DataBaseEntryS2C::handle)
                .add();

        net.messageBuilder(DataBaseFullS2C.class, id(), NetworkDirection.PLAY_TO_CLIENT)
                .decoder(DataBaseFullS2C::new)
                .encoder(DataBaseFullS2C::toBytes)
                .consumerMainThread(DataBaseFullS2C::handle)
                .add();

        //net.registerMessage(id(), DataBaseEntryS2CO.class,
        //        DataBaseEntryS2CO::encode,
        //        DataBaseEntryS2CO::decode,
        //        DataBaseEntryS2CO::onPacketReceived);
//
        //net.registerMessage(id(), DataBaseFullS2C.class,
        //        DataBaseFullS2C::encode,
        //        DataBaseFullS2C::decode,
        //        DataBaseFullS2C::onPacketReceived);
    }

    public static <MSG> void sendToServer(MSG message) {
        INSTANCE.sendToServer(message);
    }

    public static <MSG> void sendToPlayer(MSG message, ServerPlayer player) {
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), message);
    }

    public static <MSG> void sendToClients(MSG message) {
        INSTANCE.send(PacketDistributor.ALL.noArg(), message);
    }


    public static <MSG> void sendMSGToAll(MSG message) {
        for (ServerPlayer player : ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayers()) {
            sendNonLocal(message, player);
        }
    }

    public static <MSG> void sendNonLocal(MSG msg, ServerPlayer player) {
        INSTANCE.sendTo(msg, player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
    }
}