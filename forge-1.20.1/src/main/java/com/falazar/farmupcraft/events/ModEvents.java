package com.falazar.farmupcraft.events;

import com.falazar.farmupcraft.FarmUpCraft;
import com.falazar.farmupcraft.data.*;
import com.falazar.farmupcraft.saveddata.BiomeRulesInstance;
import com.falazar.farmupcraft.database.*;
import com.falazar.farmupcraft.database.serializers.*;
import com.falazar.farmupcraft.util.CustomLogger;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.UUID;

import static com.falazar.farmupcraft.FarmUpCraft.prefix;

@Mod.EventBusSubscriber(modid = FarmUpCraft.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ModEvents {
    public static final CustomLogger LOGGER = new CustomLogger(ModEvents.class.getSimpleName());

    private static final DataBaseAccess<Holder<Biome>, BiomeRulesInstance> BIOME_RULES_DATABASE = new DataBaseBuilder<Holder<Biome>, BiomeRulesInstance>(prefix("biome_rules_database"))
            .setKeySerializer(new VersionedDataSerializer<>(new HolderDataSerializer<>(Registries.BIOME), new HolderDataSerializer<>(Registries.BIOME), 1))
            .setValueSerializer(new CodecDataSerializer<>(BiomeRulesInstance.CODEC))
            .build();

    private static final DataBaseAccess<UUID, PlayerData> PLAYER_DATABASE = new DataBaseBuilder<UUID, PlayerData>(prefix("player_database"))
            .setKeySerializer(new UUIDDataSerializer())
            .setValueSerializer(new CodecDataSerializer<>(PlayerData.CODEC))
            .autoSync()
            .build();

    private static final DataBaseAccess<Long, ChunkData> CHUNK_DATA_DATABASE = new DataBaseBuilder<Long, ChunkData>(prefix("chunk_data_database"))
            .setKeySerializer(new LongDataSerializer())
            .setValueSerializer(new CodecDataSerializer<>(ChunkData.CODEC))
            .autoSync()
            .build();

    private static final DataBaseAccess<UUID, VillageData> VILLAGE_DATABASE = new DataBaseBuilder<UUID, VillageData>(prefix("village_database"))
            .setKeySerializer(new UUIDDataSerializer())
            .setValueSerializer(new CodecDataSerializer<>(VillageData.CODEC))
            .autoSync()
            .build();

    // NOTE: temp changed to NEW to avoid conflicts with old database.
    private static final DataBaseAccess<String, GoodsData> GOODS_DATA_DATABASE = new DataBaseBuilder<String, GoodsData>(prefix("goods_data_database_NEW"))
            .setKeySerializer(new StringDataSerializer())
            .setValueSerializer(new CodecDataSerializer<>(GoodsData.CODEC))
            .autoSync()
            .build();

    private static final DataBaseAccess<Integer, WorldData> WORLD_DATA_DATABASE = new DataBaseBuilder<Integer, WorldData>(prefix("world_data_database"))
            .setKeySerializer(new IntDataSerializer())
            .setValueSerializer(new CodecDataSerializer<>(WorldData.CODEC))
            .build();

    private static final DataBaseAccess<UUID, NpcData> NPC_DATABASE = new DataBaseBuilder<UUID, NpcData>(prefix("npc_database"))
            .setKeySerializer(new UUIDDataSerializer())
            .setValueSerializer(new CodecDataSerializer<>(NpcData.CODEC))
            .autoSync()
            .build();

    private static final DataBaseAccess<Long, GameStructureData> GAME_STRUCTURE_DATABASE = new DataBaseBuilder<Long, GameStructureData>(prefix("game_structure_database"))
            .setKeySerializer(new LongDataSerializer())
            .setValueSerializer(new CodecDataSerializer<>(GameStructureData.CODEC))
            .autoSync()
            .build();

    private static final DataBaseAccess<UUID, GameZone> GAME_ZONE_DATABASE = new DataBaseBuilder<UUID, GameZone>(prefix("game_zone_database"))
            .setKeySerializer(new UUIDDataSerializer())
            .setValueSerializer(new CodecDataSerializer<>(GameZone.CODEC))
            .autoSync()
            .build();

    public static WorldData getWorldData() {
        return getWorldDataDatabase().getOrCreate(0, WorldData::new);
    }

    public static DataBase<Integer, WorldData> getWorldDataDatabase() {
        return getDatabase(WORLD_DATA_DATABASE);
    }

    public static DataBase<Integer, WorldData> getWorldDataDatabase(Level level) {
        return getDatabase(WORLD_DATA_DATABASE, level);
    }

    public static DataBase<UUID, VillageData> getVillageDatabase() {
        return getDatabase(VILLAGE_DATABASE);
    }

    public static DataBase<Long, ChunkData> getChunkDataDatabase(Level level) {
        return getDatabase(CHUNK_DATA_DATABASE, level);
    }

    public static DataBase<Long, ChunkData> getChunkDataDatabase() {
        return getDatabase(CHUNK_DATA_DATABASE);
    }

    public static DataBase<UUID, PlayerData> getPlayerDatabase() {
        return getDatabase(PLAYER_DATABASE);
    }

    public static DataBase<UUID, PlayerData> getPlayerDatabase(Level level) {
        return getDatabase(PLAYER_DATABASE, level);
    }

    public static DataBase<Holder<Biome>, BiomeRulesInstance> getBiomeRulesDatabase() {
        return getDatabase(BIOME_RULES_DATABASE);
    }

    public static DataBase<UUID, VillageData> getVillageDatabase(Level level) {
        return getDatabase(VILLAGE_DATABASE, level);
    }

    public static DataBase<String, GoodsData> getGoodsDataDatabase() {
        return getDatabase(GOODS_DATA_DATABASE);
    }

    public static DataBase<UUID, NpcData> getNpcDatabase() {
        return getDatabase(NPC_DATABASE);
    }

    public static DataBase<Long, GameStructureData> getGameStructureDatabase() {
        return getDatabase(GAME_STRUCTURE_DATABASE);
    }

    public static DataBase<Long, GameStructureData> getGameStructureDatabase(Level level) {
        return getDatabase(GAME_STRUCTURE_DATABASE, level);
    }

    public static DataBase<UUID, GameZone> getGameZoneDatabase() {
        return getDatabase(GAME_ZONE_DATABASE);
    }

    public static DataBase<UUID, GameZone> getGameZoneDatabase(Level level) {
        return getDatabase(GAME_ZONE_DATABASE, level);
    }

    public static <M, V> DataBase<M, V> getDatabase(DataBaseAccess<M, V> access) {
        return getDatabase(access, ServerLifecycleHooks.getCurrentServer().overworld());
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

                    // NOTE: Comment out to load without this DB which has size issue.
                    DataBaseManager.registerDataBaseAccess(GOODS_DATA_DATABASE.getDatabaseName(), GOODS_DATA_DATABASE);

                    // Clear the database after loading - didnt work
//                    DataBase<String, GoodsData> goodsDb = getDatabase(GOODS_DATA_DATABASE);
//                    goodsDb.clearDataBase(true); // This removes all entries

                    // Clear only the goods database - didnt work
//                    DataBase<String, GoodsData> goodsDb = getDatabase(GOODS_DATA_DATABASE);
//                    for (Object key : new ArrayList<>(goodsDb.getKeys())) {
//                        goodsDb.removeDataAsync(key.toString(), null);
//                    }
//                    goodsDb.setDirty();

                    DataBaseManager.registerDataBaseAccess(WORLD_DATA_DATABASE.getDatabaseName(), WORLD_DATA_DATABASE);

                    DataBaseManager.registerDataBaseAccess(NPC_DATABASE.getDatabaseName(), NPC_DATABASE);
                    
                    DataBaseManager.registerDataBaseAccess(GAME_STRUCTURE_DATABASE.getDatabaseName(), GAME_STRUCTURE_DATABASE);
                    
                    DataBaseManager.registerDataBaseAccess(GAME_ZONE_DATABASE.getDatabaseName(), GAME_ZONE_DATABASE);
                }
        );

        //DataBaseAccess<UUID, PlayerData> dataBaseAccess = DataBaseManager.getDataBaseAccess(ModEvents.PLAYER_DATABASE.getDatabaseName());
        //DataBase<UUID, PlayerData> playerDataDataBase = dataBaseAccess.get(serverlevel).putDataBatch(manager.getData());
        //PlayerData data = playerDataDataBase.getData(UUID);
        //data.setCoins(400);
    }
}
