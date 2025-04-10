package com.falazar.farmupcraft.events;

import com.falazar.farmupcraft.FarmUpCraft;
import com.falazar.farmupcraft.command.VillageCommand;
import com.falazar.farmupcraft.data.ChunkData;
import com.falazar.farmupcraft.data.PlayerData;
import com.falazar.farmupcraft.data.VillageData;
import com.falazar.farmupcraft.saveddata.BiomeRulesInstance;
import com.falazar.farmupcraft.database.*;
import com.falazar.farmupcraft.database.serializers.*;
import com.falazar.farmupcraft.util.CustomLogger;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.Collection;

import static com.falazar.farmupcraft.FarmUpCraft.prefix;

@Mod.EventBusSubscriber(modid = FarmUpCraft.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ModEvents {
    public static final CustomLogger LOGGER = new CustomLogger(ModEvents.class.getSimpleName());

    private static final DataBaseAccess<Holder<Biome>, BiomeRulesInstance> BIOME_RULES_DATABASE = new DataBaseBuilder<Holder<Biome>, BiomeRulesInstance>(prefix("biome_rules_database"))
            .setKeySerializer(new VersionedDataSerializer<>(new HolderDataSerializer<>(Registries.BIOME), new HolderDataSerializer<>(Registries.BIOME), 1))
            .setValueSerializer(new CodecDataSerializer<>(BiomeRulesInstance.CODEC))
            .build();

    private static final DataBaseAccess<Integer, PlayerData> PLAYER_DATABASE = new DataBaseBuilder<Integer, PlayerData>(prefix("player_database"))
            .setKeySerializer(new IntDataSerializer())
            .setValueSerializer(new CodecDataSerializer<>(PlayerData.CODEC))
            .build();

    private static final DataBaseAccess<ChunkPos, ChunkData> CHUNK_DATA_DATABASE = new DataBaseBuilder<ChunkPos, ChunkData>(prefix("chunk_data_database"))
            .setKeySerializer(new ChunkPosDataSerializer())
            .setValueSerializer(new CodecDataSerializer<>(ChunkData.CODEC))
            .build();

    private static final DataBaseAccess<String, VillageData> VILLAGE_DATABASE = new DataBaseBuilder<String, VillageData>(prefix("village_database"))
            .setKeySerializer(new StringDataSerializer())
            .setValueSerializer(new CodecDataSerializer<>(VillageData.CODEC))
            .autoSync()
            .build();


    public static DataBase<String, VillageData> getVillageDatabase() {
        return getDatabase(VILLAGE_DATABASE);
    }

    public static DataBase<ChunkPos, ChunkData> getChunkDataDatabase() {
        return getDatabase(CHUNK_DATA_DATABASE);
    }

    public static DataBase<Integer, PlayerData> getPlayerDatabase() {
        return getDatabase(PLAYER_DATABASE);
    }

    public static DataBase<Holder<Biome>, BiomeRulesInstance> getBiomeRulesDatabase() {
        return getDatabase(BIOME_RULES_DATABASE);
    }

    public static <M, V> DataBase<M, V> getDatabase(DataBaseAccess<M, V> access) {
        return getDatabase(access, ServerLifecycleHooks.getCurrentServer().overworld());
    }

    public static DataBase<String, VillageData> getVillageDatabase(Level level) {
        return getDatabase(VILLAGE_DATABASE, level);
    }

    public static <M, V> DataBase<M, V> getDatabase(DataBaseAccess<M, V> access, Level level) {
        DataBaseAccess<M, V> dataBaseAccess = DataBaseManager.getDataBaseAccess(access.getDatabaseName());
        return dataBaseAccess.get(level);
    }




    @SubscribeEvent
    public static void initDataBases(FMLCommonSetupEvent event) {

        event.enqueueWork(
                () -> {
                    DataBaseManager.registerDataBaseAccess(BIOME_RULES_DATABASE.getDatabaseName(), BIOME_RULES_DATABASE);
                    DataBaseManager.registerDataBaseAccess(CHUNK_DATA_DATABASE.getDatabaseName(), CHUNK_DATA_DATABASE);
                    DataBaseManager.registerDataBaseAccess(PLAYER_DATABASE.getDatabaseName(), PLAYER_DATABASE);
                    DataBaseManager.registerDataBaseAccess(VILLAGE_DATABASE.getDatabaseName(), VILLAGE_DATABASE);
                }
        );

        //DataBaseAccess<UUID, PlayerData> dataBaseAccess = DataBaseManager.getDataBaseAccess(ModEvents.PLAYER_DATABASE.getDatabaseName());
        //DataBase<UUID, PlayerData> playerDataDataBase = dataBaseAccess.get(serverlevel).putDataBatch(manager.getData());
        //PlayerData data = playerDataDataBase.getData(UUID);
        //data.setCoins(400);
    }
}
