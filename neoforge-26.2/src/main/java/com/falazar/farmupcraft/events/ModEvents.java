package com.falazar.farmupcraft.events;

import com.falazar.farmupcraft.data.*;
import com.falazar.farmupcraft.database.*;
import com.falazar.farmupcraft.database.serializers.*;
import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import com.falazar.farmupcraft.saveddata.BiomeRulesInstance;
import com.mojang.logging.LogUtils;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public class ModEvents {
    public static final Logger LOGGER = LogUtils.getLogger();

    private static final DataBaseAccess<Holder<Biome>, BiomeRulesInstance> BIOME_RULES_DATABASE =
            new DataBaseBuilder<Holder<Biome>, BiomeRulesInstance>(prefix("biome_rules_database"))
                    .setKeySerializer(new VersionedDataSerializer<>(new HolderDataSerializer<>(Registries.BIOME),
                            new HolderDataSerializer<>(Registries.BIOME), 1))
                    .setValueSerializer(new CodecDataSerializer<>(BiomeRulesInstance.CODEC)).build();
    private static final DataBaseAccess<UUID, PlayerData> PLAYER_DATABASE =
            new DataBaseBuilder<UUID, PlayerData>(prefix("player_database"))
                    .setKeySerializer(new UUIDDataSerializer())
                    .setValueSerializer(new CodecDataSerializer<>(PlayerData.CODEC)).autoSync().build();
    private static final DataBaseAccess<Long, ChunkData> CHUNK_DATA_DATABASE =
            new DataBaseBuilder<Long, ChunkData>(prefix("chunk_data_database"))
                    .setKeySerializer(new LongDataSerializer())
                    .setValueSerializer(new CodecDataSerializer<>(ChunkData.CODEC)).autoSync().build();
    private static final DataBaseAccess<UUID, VillageData> VILLAGE_DATABASE =
            new DataBaseBuilder<UUID, VillageData>(prefix("village_database"))
                    .setKeySerializer(new UUIDDataSerializer())
                    .setValueSerializer(new CodecDataSerializer<>(VillageData.CODEC)).autoSync().build();
    // Keep the legacy spelling; the original FarmUpCraft.prefix also lowercased identifiers.
    private static final DataBaseAccess<String, GoodsData> GOODS_DATA_DATABASE =
            new DataBaseBuilder<String, GoodsData>(prefix("goods_data_database_NEW"))
                    .setKeySerializer(new StringDataSerializer())
                    .setValueSerializer(new CodecDataSerializer<>(GoodsData.CODEC)).autoSync().build();
    private static final DataBaseAccess<Integer, WorldData> WORLD_DATA_DATABASE =
            new DataBaseBuilder<Integer, WorldData>(prefix("world_data_database"))
                    .setKeySerializer(new IntDataSerializer())
                    .setValueSerializer(new CodecDataSerializer<>(WorldData.CODEC)).build();
    private static final DataBaseAccess<UUID, NpcData> NPC_DATABASE =
            new DataBaseBuilder<UUID, NpcData>(prefix("npc_database"))
                    .setKeySerializer(new UUIDDataSerializer())
                    .setValueSerializer(new CodecDataSerializer<>(NpcData.CODEC)).autoSync().build();
    private static final DataBaseAccess<Long, GameStructureData> GAME_STRUCTURE_DATABASE =
            new DataBaseBuilder<Long, GameStructureData>(prefix("game_structure_database"))
                    .setKeySerializer(new LongDataSerializer())
                    .setValueSerializer(new CodecDataSerializer<>(GameStructureData.CODEC)).autoSync().build();
    private static final DataBaseAccess<UUID, GameZone> GAME_ZONE_DATABASE =
            new DataBaseBuilder<UUID, GameZone>(prefix("game_zone_database"))
                    .setKeySerializer(new UUIDDataSerializer())
                    .setValueSerializer(new CodecDataSerializer<>(GameZone.CODEC)).autoSync().build();

    private static final List<DataBaseAccess<?, ?>> DATABASES = List.of(BIOME_RULES_DATABASE, CHUNK_DATA_DATABASE,
            PLAYER_DATABASE, VILLAGE_DATABASE, GOODS_DATA_DATABASE, WORLD_DATA_DATABASE, NPC_DATABASE,
            GAME_STRUCTURE_DATABASE, GAME_ZONE_DATABASE);

    private static Identifier prefix(String name) {
        return Identifier.fromNamespaceAndPath(FarmUpCraftNeoForge.MOD_ID, name.toLowerCase(Locale.ROOT));
    }

    public static WorldData getWorldData() {
        return getWorldDataDatabase().getOrCreate(0, WorldData::new);
    }

    public static WorldData getWorldData(Level level) {
        return getWorldDataDatabase(level).getOrCreate(0, WorldData::new);
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

    public static DataBase<UUID, VillageData> getVillageDatabase(Level level) {
        return getDatabase(VILLAGE_DATABASE, level);
    }

    public static DataBase<Long, ChunkData> getChunkDataDatabase() {
        return getDatabase(CHUNK_DATA_DATABASE);
    }

    public static DataBase<Long, ChunkData> getChunkDataDatabase(Level level) {
        return getDatabase(CHUNK_DATA_DATABASE, level);
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

    public static DataBase<String, GoodsData> getGoodsDataDatabase() {
        return getDatabase(GOODS_DATA_DATABASE);
    }

    public static DataBase<String, GoodsData> getGoodsDataDatabase(Level level) {
        return getDatabase(GOODS_DATA_DATABASE, level);
    }

    public static DataBase<UUID, NpcData> getNpcDatabase() {
        return getDatabase(NPC_DATABASE);
    }

    public static DataBase<UUID, NpcData> getNpcDatabase(Level level) {
        return getDatabase(NPC_DATABASE, level);
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

    /** No-argument access is server-only; clients must supply their actual client level. */
    public static <M, V> DataBase<M, V> getDatabase(DataBaseAccess<M, V> access) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || server.overworld() == null) {
            throw new IllegalStateException("Database access requires a running server with an overworld");
        }
        return getDatabase(access, server.overworld());
    }

    public static <M, V> DataBase<M, V> getDatabase(DataBaseAccess<M, V> access, Level level) {
        Objects.requireNonNull(access, "access");
        Objects.requireNonNull(level, "level");
        if (!level.isClientSide() && !(level instanceof ServerLevel)) {
            throw new IllegalArgumentException("Persistent database access requires a server level");
        }
        DataBaseAccess<M, V> registered = DataBaseManager.getDataBaseAccess(access.getDatabaseName());
        if (registered == null) {
            throw new IllegalStateException("Database is not registered: " + access.getDatabaseName());
        }
        return registered.get(level);
    }

    public static void initDataBases(FMLCommonSetupEvent event) {
        event.enqueueWork(ModEvents::registerDataBases);
    }

    static void registerDataBases() {
        DATABASES.forEach(ModEvents::register);
    }

    private static <M, V> void register(DataBaseAccess<M, V> access) {
        DataBaseManager.registerDataBaseAccess(access.getDatabaseName(), access);
    }

    public static void serverStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        // The registry-only test server has no levels; never fabricate one for database initialization.
        if (server.overworld() == null) return;
        DataBaseManager.start(server);
        DATABASES.forEach(access -> access.get(server.overworld()));
        com.falazar.farmupcraft.command.MarketCommand.initializeMarket(server.createCommandSourceStack());
    }

    public static void serverStopping(ServerStoppingEvent event) {
        DataBaseManager.shutDownDataBases(event.getServer());
    }

    public static void serverStopped(ServerStoppedEvent event) {
        DataBaseManager.shutDownDataBases(event.getServer());
        DataBaseManager.clearClientCaches();
    }
}
