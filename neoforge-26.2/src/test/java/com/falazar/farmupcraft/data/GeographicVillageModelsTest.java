package com.falazar.farmupcraft.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class GeographicVillageModelsTest {
    private static final UUID ID = UUID.fromString("01234567-89ab-cdef-fedc-ba9876543210");
    private static final String VILLAGE = """
            {"uuid":"01234567-89ab-cdef-fedc-ba9876543210","name":"雪 Village",
             "position":{"chunk_long":146028888047},"level":2,
             "claimed_chunks":[{"chunk_long":146028888047}],"bought":true}
            """;

    @Test
    void worldLegacyRequiredAndOptionalFieldsHaveExactJsonAndNbtShapes() throws Exception {
        WorldData defaults = assertShape(WorldData.CODEC, new WorldData(),
                "{\"upkeep_tick\":0}", "{upkeep_tick:0}");
        assertEquals(0, defaults.getUpkeepTick());
        assertEquals("2000-01-01", defaults.getLastRanMarketDaily());
        assertTrue(defaults.isUseVillageFarms());
        assertTrue(defaults.isUseBiomeCropRules());
        assertTrue(defaults.isLycaniteLightBlock());
        assertTrue(defaults.isLanNoticeEnabled());
        assertFalse(defaults.hasMarketRanTodayAlready());
        defaults.setUpkeepTick(12);
        defaults.increaseUpkeepTick();
        defaults.setUseVillageFarms(false);
        defaults.setUseBiomeCropRules(false);
        defaults.setLycaniteLightBlock(false);
        defaults.setLanNoticeEnabled(false);
        defaults.setLastRanMarketDaily("2020-04-03");
        assertShape(WorldData.CODEC, defaults, """
                {"upkeep_tick":13,"last_ran_market_daily":"2020-04-03","use_farms":false,
                 "use_biome_crop_rules":false,"lycanite_light_block":false,"lan_notice_enabled":false}
                """, """
                {upkeep_tick:13,last_ran_market_daily:"2020-04-03",use_farms:0b,
                 use_biome_crop_rules:0b,lycanite_light_block:0b,lan_notice_enabled:0b}
                """);
        defaults.setLastRanMarketDaily(null);
        assertEquals("2000-01-01", defaults.getLastRanMarketDaily());
        assertEquals("2000-01-01", new WorldData(1, null, false, false, false, false).getLastRanMarketDaily());
        defaults.markMarketDailyRanToday();
        assertTrue(defaults.hasMarketRanTodayAlready());
        defaults.setUpkeepTick(Integer.MAX_VALUE);
        defaults.increaseUpkeepTick();
        assertEquals(Integer.MIN_VALUE, defaults.getUpkeepTick());
        assertTrue(WorldData.CODEC.parse(JsonOps.INSTANCE, json("{}")).error().isPresent());
        assertTrue(WorldData.CODEC.parse(JsonOps.INSTANCE, json("{\"upkeep_tick\":0,\"use_farms\":\"bad\"}"))
                .error().isPresent());
    }

    @Test
    void zoneAndVillagerRetainDifferentUuidShapesPackedSectionsAndCoordinateArrays() throws Exception {
        GameZone zone = new GameZone(ID, "Cave", List.of(SectionPos.of(-1, -4, 2)),
                GameZone.ZoneType.CAVE, new BlockPos(-17, -64, 33));
        GameZone decoded = assertShape(GameZone.CODEC, zone, """
                {"uuid":[19088743,-1985229329,-19088744,1985229328],"name":"Cave",
                 "sections":[-4398043365380],"type":"CAVE","center":[-17,-64,33]}
                """, """
                {uuid:[I;19088743,-1985229329,-19088744,1985229328],name:"Cave",
                 sections:[L;-4398043365380L],type:"CAVE",center:[I;-17,-64,33]}
                """);
        assertEquals(zone, decoded);
        assertEquals(zone.hashCode(), decoded.hashCode());
        assertEquals(zone.getSections(), decoded.getSections());
        assertEquals(zone.getCenter(), decoded.getCenter());
        assertShape(GameZone.CODEC,
                new GameZone(ID, "", List.of(), GameZone.ZoneType.LAKE, BlockPos.ZERO), """
                {"uuid":[19088743,-1985229329,-19088744,1985229328],"name":"",
                 "sections":[],"type":"LAKE","center":[0,0,0]}
                """, """
                {uuid:[I;19088743,-1985229329,-19088744,1985229328],name:"",
                 sections:[],type:"LAKE",center:[I;0,0,0]}
                """);
        assertEquals("GameZone{name='Cave', type=CAVE, size=1, center=" + zone.getCenter()
                + ", uuid=" + ID + "}", zone.toString());
        assertEquals(zone, new GameZone(ID, "Other", List.of(), GameZone.ZoneType.OCEAN, BlockPos.ZERO));
        assertNotEquals(zone, new GameZone(List.of(), GameZone.ZoneType.CAVE));
        assertNotEquals(zone, null);
        assertNotEquals(zone, "Cave");
        for (var type : GameZone.ZoneType.values()) {
            assertEquals(type, GameZone.ZoneType.CODEC.parse(JsonOps.INSTANCE,
                    json("\"" + type.name() + "\"")).getOrThrow());
        }
        VillagerRecord record = new VillagerRecord(ID, "Ada 雪", new BlockPos(-17, -64, 33));
        VillagerRecord restored = assertShape(VillagerRecord.CODEC, record, """
                {"uuid":"01234567-89ab-cdef-fedc-ba9876543210","name":"Ada 雪","death_pos":[-17,-64,33]}
                """, """
                {uuid:"01234567-89ab-cdef-fedc-ba9876543210",name:"Ada 雪",death_pos:[I;-17,-64,33]}
                """);
        assertEquals(ID, restored.getUuid());
        assertEquals(record.getName(), restored.getName());
        assertEquals(record.getDeathPos(), restored.getDeathPos());
        assertEquals("VillagerRecord{name=Ada 雪, uuid=" + ID + ", deathPos=" + record.getDeathPos() + "}",
                record.toString());
        assertRequiredFields(GameZone.CODEC, json("""
                {"uuid":[19088743,-1985229329,-19088744,1985229328],"name":"Cave",
                 "sections":[],"type":"CAVE","center":[0,0,0]}
                """), "uuid", "name", "sections", "type", "center");
        assertRequiredFields(VillagerRecord.CODEC, json("""
                {"uuid":"01234567-89ab-cdef-fedc-ba9876543210","name":"Ada","death_pos":[0,0,0]}
                """), "uuid", "name", "death_pos");
        assertTrue(VillagerRecord.CODEC.parse(JsonOps.INSTANCE, json("""
                {"uuid":"bad uuid","name":"Ada","death_pos":[0,0,0]}
                """)).error().isPresent());
    }

    @Test
    void zonesUseSectionOriginBoundingBoxCentersAndExactThreeDimensionalBoundaries() {
        List<SectionPos> sections = new ArrayList<>(List.of(SectionPos.of(-2, -4, -1),
                SectionPos.of(4, 2, 5), SectionPos.of(4, 3, 5)));
        GameZone zone = new GameZone(sections, GameZone.ZoneType.ISLAND, "Custom");
        assertEquals(new BlockPos(16, -8, 32), zone.getCenter());
        assertEquals("Custom", zone.getName());
        assertEquals(3, zone.getSize());
        assertTrue(zone.containsSection(SectionPos.of(-2, -4, -1)));
        assertTrue(zone.containsBlock(new BlockPos(-32, -64, -16)));
        assertTrue(zone.containsBlock(new BlockPos(-17, -49, -1)));
        assertFalse(zone.containsBlock(new BlockPos(-33, -64, -16)));
        assertFalse(zone.containsBlock(new BlockPos(-32, -65, -16)));
        assertFalse(zone.containsBlock(new BlockPos(-32, -64, 0)));
        assertEquals(List.of(new ChunkPos(-2, -1), new ChunkPos(4, 5)), zone.getChunkPositions());
        sections.clear();
        assertEquals(0, zone.getSize());
        assertEquals(new BlockPos(16, -8, 32), zone.getCenter());
        GameZone empty = new GameZone(List.of(), GameZone.ZoneType.LAKE);
        assertEquals(BlockPos.ZERO, empty.getCenter());
        assertTrue(empty.getName().matches("LAKE-[1-9][0-9]"));
        assertNotNull(empty.getUuid());
        GameZone chunks = GameZone.fromChunks(List.of(new ChunkPos(-1, 2), new ChunkPos(3, -4)),
                GameZone.ZoneType.COUNTRY);
        assertEquals(List.of(SectionPos.of(-1, 4, 2), SectionPos.of(3, 4, -4)), chunks.getSections());
        assertEquals(new BlockPos(16, 64, -16), chunks.getCenter());
        assertTrue(chunks.containsBlock(new BlockPos(-1, 79, 47)));
        assertFalse(chunks.containsBlock(new BlockPos(-1, 80, 47)));
    }

    @Test
    void villageRequiredLegacyShapeDefaultsAndAllOptionalFieldsRoundTrip() throws Exception {
        VillageData village = VillageData.CODEC.parse(JsonOps.INSTANCE, json(VILLAGE)).getOrThrow();
        assertShape(VillageData.CODEC, village, VILLAGE, """
                {uuid:"01234567-89ab-cdef-fedc-ba9876543210",name:"雪 Village",
                 position:{chunk_long:146028888047L},level:2,
                 claimed_chunks:[{chunk_long:146028888047L}],bought:1b}
                """);
        assertEquals(ID, village.getUUID());
        assertEquals(new ChunkPos(-17, 33), village.getPosition());
        assertEquals(2, village.getLevel());
        assertTrue(village.isBought());
        assertEquals(0, village.getCoins());
        assertEquals("2000-01-01", village.getLastRanDaily());
        assertEquals("", village.getAnimalType());
        assertEquals("2000-01-01", village.getLastAnimalTypeChange());
        assertEquals("", village.getFounder());
        assertTrue(village.getMemberUUIDs().isEmpty());
        assertEquals("", village.getAnimalGrains());
        assertTrue(village.getReincarnationPool().isEmpty());
        assertTrue(village.getClaimedChunkSet().contains(146028888047L));
        assertRequiredFields(VillageData.CODEC, json(VILLAGE), "uuid", "name", "position",
                "level", "claimed_chunks", "bought");
        village.setCoins(25);
        village.setLastRanDaily("2020-02-03");
        village.setAnimalType("cow");
        village.setLastAnimalTypeChange("2020-01-02");
        village.setFounder("Founder");
        village.addMember(ID);
        village.setAnimalGrains("cow=minecraft:wheat,harvestcraft:oatsitem");
        village.addToReincarnationPool(new VillagerRecord(ID, "Ada", new BlockPos(-1, 64, 2)));
        VillageData restored = assertShape(VillageData.CODEC, village, """
                {"uuid":"01234567-89ab-cdef-fedc-ba9876543210","name":"雪 Village",
                 "position":{"chunk_long":146028888047},"level":2,
                 "claimed_chunks":[{"chunk_long":146028888047}],"bought":true,"coins":25,
                 "last_ran_daily":"2020-02-03","animal_type":"cow","last_animal_type_change":"2020-01-02",
                 "founder":"Founder","member_uuids":["01234567-89ab-cdef-fedc-ba9876543210"],
                 "animal_grains":"cow=minecraft:wheat,harvestcraft:oatsitem",
                 "reincarnation_pool":[{"uuid":"01234567-89ab-cdef-fedc-ba9876543210",
                 "name":"Ada","death_pos":[-1,64,2]}]}
                """, """
                {uuid:"01234567-89ab-cdef-fedc-ba9876543210",name:"雪 Village",
                 position:{chunk_long:146028888047L},level:2,
                 claimed_chunks:[{chunk_long:146028888047L}],bought:1b,coins:25,
                 last_ran_daily:"2020-02-03",animal_type:"cow",last_animal_type_change:"2020-01-02",
                 founder:"Founder",member_uuids:["01234567-89ab-cdef-fedc-ba9876543210"],
                 animal_grains:"cow=minecraft:wheat,harvestcraft:oatsitem",
                 reincarnation_pool:[{uuid:"01234567-89ab-cdef-fedc-ba9876543210",
                 name:"Ada",death_pos:[I;-1,64,2]}]}
                """);
        assertTrue(restored.isMember(ID));
        assertEquals("Ada", restored.pollReincarnation().getName());
        assertNull(restored.pollReincarnation());
        VillageData separate = VillageData.CODEC.parse(JsonOps.INSTANCE, json(VILLAGE)).getOrThrow();
        assertTrue(separate.getMemberUUIDs().isEmpty());
        assertTrue(separate.getReincarnationPool().isEmpty());
    }

    @Test
    void villageMutationsKeepLegacyCopiesLiveCollectionsCoinsDatesAndDuplicateClaims() {
        ChunkPos origin = new ChunkPos(-17, 33);
        List<ChunkPos> claims = new ArrayList<>(List.of(origin));
        List<UUID> members = new ArrayList<>(List.of(ID));
        List<VillagerRecord> pool = new ArrayList<>();
        VillageData village = new VillageData(ID, "Old", origin, 1, claims, false, 0,
                null, null, null, null, members, null, pool);
        claims.clear();
        members.clear();
        pool.add(new VillagerRecord(ID, "External", BlockPos.ZERO));
        assertEquals(List.of(origin), village.getClaimedChunks());
        assertEquals(List.of(ID), village.getMemberUUIDs());
        assertNull(village.pollReincarnation());
        assertEquals("2000-01-01", village.getLastRanDaily());
        assertEquals("", village.getAnimalType());
        assertEquals("2000-01-01", village.getLastAnimalTypeChange());
        assertEquals("", village.getFounder());
        assertEquals("", village.getAnimalGrains());
        village.setName("New");
        village.setLevel(-2);
        assertEquals("New", village.getName());
        assertEquals(-2, village.getLevel());
        village.addClaimedChunk(origin);
        assertEquals(2, village.getClaimedChunks().size());
        assertEquals(1, village.getClaimedChunkSet().size());
        village.removeClaimedChunk(origin);
        assertEquals(List.of(origin), village.getClaimedChunks());
        assertTrue(village.getClaimedChunkSet().isEmpty());
        assertEquals("Village ID: " + ID + ", Owner: New, Chunks: " + village.getClaimedChunks(), village.toString());
        village.getClaimedChunks().clear();
        village.getClaimedChunkSet().add(7L);
        assertEquals(1, village.getClaimedChunkSet().size());
        village.addCoins(10);
        assertTrue(village.hasEnoughCoins(10));
        assertFalse(village.hasEnoughCoins(11));
        village.subtractCoins(11);
        assertEquals(-1, village.getCoins());
        assertThrows(IllegalArgumentException.class, () -> village.addCoins(-1));
        assertThrows(IllegalArgumentException.class, () -> village.subtractCoins(-1));
        village.setCoins(Integer.MAX_VALUE);
        village.addCoins(1);
        assertEquals(Integer.MIN_VALUE, village.getCoins());
        village.setLastRanDaily(null);
        assertFalse(village.hasRanTodayAlready());
        village.markDailyRanToday();
        assertTrue(village.hasRanTodayAlready());
        village.setAnimalType(null);
        village.setFounder(null);
        village.setLastAnimalTypeChange(null);
        assertEquals("", village.getAnimalType());
        assertEquals("", village.getFounder());
        assertTrue(village.canChangeAnimalType());
        village.setLastAnimalTypeChange(LocalDate.now().minusDays(13).toString());
        assertFalse(village.canChangeAnimalType());
        village.setLastAnimalTypeChange(LocalDate.now().minusDays(14).toString());
        assertTrue(village.canChangeAnimalType());
        village.setLastAnimalTypeChange("bad date");
        assertThrows(DateTimeParseException.class, village::canChangeAnimalType);
        village.addMember(ID);
        assertEquals(1, village.getMemberUUIDs().size());
        village.removeMember(ID);
        assertFalse(village.isMember(ID));
        village.getMemberUUIDs().add(ID);
        assertTrue(village.isMember(ID));
        var first = new VillagerRecord(ID, "First", BlockPos.ZERO);
        var second = new VillagerRecord(UUID.randomUUID(), "Second", BlockPos.ZERO);
        village.addToReincarnationPool(first);
        village.getReincarnationPool().add(second);
        assertSame(first, village.pollReincarnation());
        assertSame(second, village.pollReincarnation());
        assertNull(village.pollReincarnation());
        VillageData convenience = new VillageData(ID, "", origin, 0, List.of(), false,
                0, null, null, null, null, null);
        assertTrue(convenience.getMemberUUIDs().isEmpty());
        assertTrue(convenience.getReincarnationPool().isEmpty());
        assertFalse(convenience.hasAnimalGrainsAssigned());
    }

    @Test
    void grainStringsPreserveOrderingWhitespaceMalformedEntryAndDuplicateKeySemantics() {
        VillageData village = VillageData.CODEC.parse(JsonOps.INSTANCE, json(VILLAGE)).getOrThrow();
        village.setAnimalGrains("cow=wheat,,oats|malformed|sheep=|cow=rice, barley|=seed|pig=a=b");
        assertTrue(village.hasAnimalGrainsAssigned());
        assertEquals(List.of("cow", "sheep", "", "pig"), new ArrayList<>(village.getAnimalGrainsMap().keySet()));
        assertEquals(List.of("rice", " barley"), village.getGrainsForAnimal("cow"));
        assertEquals(List.of(), village.getGrainsForAnimal("sheep"));
        assertEquals(List.of("seed"), village.getGrainsForAnimal(""));
        assertEquals(List.of("a=b"), village.getGrainsForAnimal("pig"));
        assertTrue(village.getGrainsForAnimal("missing").isEmpty());
        var map = new LinkedHashMap<String, List<String>>();
        map.put("cow", List.of("wheat", "oats"));
        map.put("sheep", List.of());
        village.setAnimalGrainsFromMap(map);
        assertEquals("cow=wheat,oats|sheep=", village.getAnimalGrains());
        assertEquals(map, village.getAnimalGrainsMap());
        village.setAnimalGrains(null);
        assertEquals("", village.getAnimalGrains());
        assertFalse(village.hasAnimalGrainsAssigned());
        assertTrue(village.getAnimalGrainsMap().isEmpty());
    }

    private static <T> T assertShape(Codec<T> codec, T value, String legacyJson, String legacyNbt) throws Exception {
        JsonElement expectedJson = json(legacyJson);
        var expectedNbt = TagParser.parseCompoundFully(legacyNbt);
        assertEquals(expectedJson, codec.encodeStart(JsonOps.INSTANCE, value).getOrThrow());
        assertEquals(expectedNbt, codec.encodeStart(NbtOps.INSTANCE, value).getOrThrow());
        T fromJson = codec.parse(JsonOps.INSTANCE, expectedJson).getOrThrow();
        T fromNbt = codec.parse(NbtOps.INSTANCE, expectedNbt).getOrThrow();
        assertEquals(expectedNbt, codec.encodeStart(NbtOps.INSTANCE, fromJson).getOrThrow());
        assertEquals(expectedJson, codec.encodeStart(JsonOps.INSTANCE, fromNbt).getOrThrow());
        return fromNbt;
    }

    private static <T> void assertRequiredFields(Codec<T> codec, JsonElement complete, String... fields) {
        for (String field : fields) {
            var missing = complete.deepCopy().getAsJsonObject();
            missing.remove(field);
            assertTrue(codec.parse(JsonOps.INSTANCE, missing).error().isPresent(), field);
        }
    }

    private static JsonElement json(String value) { return JsonParser.parseString(value); }
}
