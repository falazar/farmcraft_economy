package com.falazar.farmupcraft.events;

import com.falazar.farmupcraft.FarmUpCraft;
import com.falazar.farmupcraft.client.ClientEvents;
import com.falazar.farmupcraft.client.ForgeClientEvents;
import com.falazar.farmupcraft.currency.CoinStack;
import com.falazar.farmupcraft.currency.Wallet;
import com.falazar.farmupcraft.data.*;
import com.falazar.farmupcraft.database.message.DataBaseChunkS2C;
import com.falazar.farmupcraft.registry.BiomeRegistryHolder;
import com.falazar.farmupcraft.database.DataBase;
import com.falazar.farmupcraft.database.DataBaseAccess;
import com.falazar.farmupcraft.database.DataBaseManager;
import com.falazar.farmupcraft.database.message.DataBaseFullS2C;
import com.falazar.farmupcraft.database.message.EDBMessages;
import com.falazar.farmupcraft.registry.CoinRegistry;
import com.falazar.farmupcraft.structure.BuildableStructure;
import com.falazar.farmupcraft.structure.BuildableStructureInstance;
import com.falazar.farmupcraft.structure.BuildableStructureRegistry;
import com.falazar.farmupcraft.util.AsyncLocator;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.awt.event.ItemEvent;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Handles various Forge events related to server lifecycle and resource reloads.
 * <p>
 * This class is subscribed to Forge's event bus and contains methods that react to server
 * start, stop, and resource reload events. It is part of the FarmUpCraft mod.
 */
@Mod.EventBusSubscriber(modid = FarmUpCraft.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ForgeEvents {
    public static final List<BuildableStructureInstance> ACTIVE_BUILDS = new ArrayList<>();


    @SubscribeEvent
    public static void structurePlaceItem(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        Level level = player.level();
        //if (!(level instanceof ServerLevel serverLevel)) return;

        ItemStack stack = event.getItemStack();

        if (stack.is(Items.STICK) && event.getHand() == InteractionHand.MAIN_HAND) {
            BlockPos origin = event.getPos();
            BuildableStructureInstance instance = new BuildableStructureInstance(BuildableStructureRegistry.get(TEST_ID), origin, Rotation.CLOCKWISE_90);
            //ACTIVE_BUILDS.add(instance);
            BuildableStructure structure = BuildableStructureRegistry.get(TEST_ID);

            ForgeClientEvents.PREVIEW_MANAGER.showPreview(instance);

            if (player.isShiftKeyDown()) {
                ForgeClientEvents.PREVIEW_MANAGER.clearAll();
            }
        }
    }

    @SubscribeEvent
    public static void serverTickEvent(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) return;
        tickAll(event.getServer().overworld(), 5);
    }

    public static void tickAll(ServerLevel level, int blocksPerTick) {
        Iterator<BuildableStructureInstance> it = ACTIVE_BUILDS.iterator();
        while (it.hasNext()) {
            BuildableStructureInstance instance = it.next();
            instance.tickBuild(level, blocksPerTick);
            if (instance.isFinished()) it.remove();
        }
    }


    /**
     * Called when the server is about to start.
     * <p>
     * This method handles tasks that need to be done just before the server starts.
     * Currently, it manages asynchronous tasks related to server startup.
     *
     * @param event The event containing server startup details.
     */
    @SubscribeEvent
    public static void serverAboutToStart(final ServerAboutToStartEvent event) {
        AsyncLocator.handleServerAboutToStartEvent();
        BiomeRegistryHolder.setupBiomeRegistry(event.getServer());
    }

    /**
     * Called when the server has started.
     * <p>
     * This method populates crop block and crop item data after the server starts.
     * It uses the server's overworld to initialize data for crop blocks and items.
     *
     * @param event The event containing server start details.
     */

    public static final ResourceLocation TEST_ID = new ResourceLocation("farmupcraft", "test_house");

    @SubscribeEvent
    public static void serverStarted(final ServerStartedEvent event) {
        CropBlockDataJsonManager.populateCropBlockEntries(event.getServer().overworld());
        CropItemDataJsonManager.populateCropItemEntries(event.getServer().overworld());
        BiomeRulesDataJsonManager.populateBiomeRulesInstances(event.getServer().overworld());
        BuildableStructure testStructure = new BuildableStructure(new ResourceLocation("minecraft", "ancient_city/city_center/city_center_1"));
        BuildableStructureRegistry.register(TEST_ID, testStructure);




        BuildableStructureRegistry.getStructures().values().forEach(buildableStructure -> buildableStructure.gatherAndPopulateStructureData(event.getServer().overworld()));
    }

    /**
     * Called when the server is stopping.
     * <p>
     * This method handles cleanup tasks when the server is about to stop, such as
     * clearing cached entries and handling asynchronous tasks.
     *
     * @param event The event containing server stopping details.
     */
    @SubscribeEvent
    public static void onServerStopping(final ServerStoppingEvent event) {
        AsyncLocator.handleServerStoppingEvent();
        CropBlockDataJsonManager.clearEntries();
        CropItemDataJsonManager.clearEntries();
        BiomeRulesDataJsonManager.clearEntries();


    }

    @SubscribeEvent
    public static void serverStopped(final ServerStoppedEvent event) {
        DataBaseManager.shutDownDataBases(event.getServer().overworld());
    }

    /**
     * Called when the reload listeners are being registered.
     * <p>
     * This method registers new reload listeners for handling JSON resource data related
     * to crop blocks, crop items, and the market. These listeners will be triggered
     * when the resources are reloaded.
     *
     * @param event The event containing reload listener registration details.
     */
    @SubscribeEvent
    public static void onRegisterReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new CropBlockDataJsonManager());
        event.addListener(new CropItemDataJsonManager());
        //event.addListener(new MarketDataJsonManager());
        event.addListener(new BiomeRulesDataJsonManager());
    }

    @SubscribeEvent
    public static void onLoginEvent(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity().level() instanceof ServerLevel level) {

            DataBase<UUID, PlayerData> playerDatabase = ModEvents.getPlayerDatabase();
            UUID uuid = ((ServerPlayer) event.getEntity()).getUUID();

            if (!playerDatabase.containsKey(uuid)) {
                CoinStack bronzeStack = new CoinStack(CoinRegistry.getCoin(CoinRegistry.BRONZE_COIN), 10);
                playerDatabase.putData(uuid, new PlayerData(event.getEntity().getId(), UUID.randomUUID(), new Wallet(List.of(bronzeStack))));
            }

            for (ResourceLocation dataBaseName : DataBaseManager.getDataBasesToSync()) {
                DataBaseAccess<?, ?> dataBaseAccess = DataBaseManager.getDataBaseAccess(dataBaseName);

                CompletableFuture.runAsync(() -> {
                    DataBase<?, ?> dataBase = dataBaseAccess.get(level);
                    List<CompoundTag> chunks = dataBase.saveChunked(50); // ← send always in chunks

                    level.getServer().execute(() -> {
                        ServerPlayer player = (ServerPlayer) event.getEntity();
                        for (int i = 0; i < chunks.size(); i++) {
                            EDBMessages.sendToPlayer(new DataBaseChunkS2C<>(
                                    chunks.get(i),
                                    dataBase.getDatabaseName(),
                                    i,
                                    chunks.size()
                            ), player);
                        }
                    });
                });
            }
        }
    }


}
