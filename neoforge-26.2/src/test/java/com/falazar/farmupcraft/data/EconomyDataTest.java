package com.falazar.farmupcraft.data;

import com.falazar.farmupcraft.database.DataBase;
import com.falazar.farmupcraft.database.serializers.CodecDataSerializer;
import com.falazar.farmupcraft.database.serializers.StringDataSerializer;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class EconomyDataTest {
    private static final UUID PLAYER = UUID.fromString("12345678-1234-5678-9876-123456789abc");
    private static final UUID VILLAGE = UUID.fromString("87654321-4321-8765-6789-cba987654321");

    @AfterEach
    void clearLoader() {
        MarketDataJsonManager.clearEntries();
    }

    @Test
    void playerLegacyJsonAndNbtSchemasRetainDefaultsAndNullableVillageSentinel(MinecraftServer server) {
        var jsonOps = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        var nbtOps = server.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        JsonObject minimal = JsonParser.parseString("{\"id\":17}").getAsJsonObject();
        PlayerData defaults = PlayerData.CODEC.parse(jsonOps, minimal).getOrThrow();
        assertEquals(17, defaults.getId());
        assertEquals(new UUID(0, 0), defaults.getPlayerUUID());
        assertNull(defaults.getHomeVillageUUID());
        assertEquals(0, defaults.getCoins());
        assertFalse(defaults.isBorderShow());
        assertEquals("blue", defaults.getBorderColor());
        assertEquals(minimal, PlayerData.CODEC.encodeStart(jsonOps, defaults).getOrThrow());
        CompoundTag minimalNbt = new CompoundTag();
        minimalNbt.putInt("id", 17);
        assertEquals(minimalNbt, PlayerData.CODEC.encodeStart(nbtOps, defaults).getOrThrow());
        assertNull(PlayerData.CODEC.parse(nbtOps, minimalNbt).getOrThrow().getHomeVillageUUID());

        JsonObject full = JsonParser.parseString("""
                {"id":17,"player_uuid":"12345678-1234-5678-9876-123456789abc",
                 "home_village_id":"87654321-4321-8765-6789-cba987654321",
                 "coins":42,"border_show":true,"border_color":"red"}
                """).getAsJsonObject();
        PlayerData data = PlayerData.CODEC.parse(jsonOps, full).getOrThrow();
        assertEquals(PLAYER, data.getPlayerUUID());
        assertEquals(VILLAGE, data.getHomeVillageUUID());
        assertEquals(full, PlayerData.CODEC.encodeStart(jsonOps, data).getOrThrow());
        CompoundTag expected = new CompoundTag();
        expected.putInt("id", 17);
        expected.putString("player_uuid", PLAYER.toString());
        expected.putString("home_village_id", VILLAGE.toString());
        expected.putInt("coins", 42);
        expected.putBoolean("border_show", true);
        expected.putString("border_color", "red");
        assertEquals(expected, PlayerData.CODEC.encodeStart(nbtOps, data).getOrThrow());
        assertEquals(expected, PlayerData.CODEC.encodeStart(nbtOps,
                PlayerData.CODEC.parse(nbtOps, expected).getOrThrow()).getOrThrow());

        data.setHomeVillageId(null);
        assertNull(data.getHomeVillageUUID());
        assertFalse(PlayerData.CODEC.encodeStart(jsonOps, data).getOrThrow()
                .getAsJsonObject().has("home_village_id"));
        data.setHomeVillageId(new UUID(0, 0));
        assertNull(data.getHomeVillageUUID());
        assertTrue(PlayerData.CODEC.parse(jsonOps, new JsonObject()).error().isPresent());
        full.addProperty("coins", -9);
        assertEquals(0, PlayerData.CODEC.parse(jsonOps, full).getOrThrow().getCoins());
    }

    @Test
    void playerMutationsPreserveClampingRejectionAndIntegerArithmetic() {
        PlayerData player = new PlayerData(7, PLAYER, null, -5);
        assertEquals(0, player.getCoins());
        player.setPlayerUUID(VILLAGE);
        assertEquals(VILLAGE, player.getPlayerUUID());
        player.setHomeVillageId(VILLAGE);
        assertEquals(VILLAGE, player.getHomeVillageUUID());
        player.setBorderShow(true);
        player.setBorderColor("gold");
        assertTrue(player.isBorderShow());
        assertEquals("gold", player.getBorderColor());
        player.setCoins(20);
        player.addCoins(-1);
        player.addCoins(0);
        assertEquals(20, player.getCoins());
        player.addCoins(5);
        assertTrue(player.removeCoins(0));
        assertFalse(player.removeCoins(-1));
        assertFalse(player.removeCoins(26));
        assertEquals(25, player.getCoins());
        assertTrue(player.removeCoins(25));
        assertEquals(0, player.getCoins());
        player.setCoins(-20);
        assertEquals(0, player.getCoins());
        player.setCoins(Integer.MAX_VALUE);
        player.addCoins(1);
        assertEquals(Integer.MIN_VALUE, player.getCoins(), "Legacy addCoins does not saturate");
    }

    @Test
    void goodsAndMarketRetainEveryRequiredLegacyFieldInJsonAndNbt(MinecraftServer server) {
        var jsonOps = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        var nbtOps = server.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        JsonObject json = goodsJson();
        GoodsData goods = GoodsData.CODEC.parse(jsonOps, json).getOrThrow();
        assertEquals(json, GoodsData.CODEC.encodeStart(jsonOps, goods).getOrThrow());
        CompoundTag tag = goodsNbt();
        assertEquals(tag, GoodsData.CODEC.encodeStart(nbtOps, goods).getOrThrow());
        assertEquals(tag, GoodsData.CODEC.encodeStart(nbtOps,
                GoodsData.CODEC.parse(nbtOps, tag).getOrThrow()).getOrThrow());
        for (String field : json.keySet()) {
            JsonObject missing = json.deepCopy();
            missing.remove(field);
            assertTrue(GoodsData.CODEC.parse(jsonOps, missing).error().isPresent(), field);
        }
        JsonObject marketJson = new JsonObject();
        marketJson.add("goods", JsonParser.parseString("[" + json + "]"));
        MarketData market = MarketData.CODEC.parse(jsonOps, marketJson).getOrThrow();
        assertEquals(marketJson, MarketData.CODEC.encodeStart(jsonOps, market).getOrThrow());
        CompoundTag marketNbt = new CompoundTag();
        ListTag goodsList = new ListTag();
        goodsList.add(tag);
        marketNbt.put("goods", goodsList);
        assertEquals(marketNbt, MarketData.CODEC.encodeStart(nbtOps, market).getOrThrow());
        assertEquals(marketNbt, MarketData.CODEC.encodeStart(nbtOps,
                MarketData.CODEC.parse(nbtOps, marketNbt).getOrThrow()).getOrThrow());
        assertTrue(MarketData.CODEC.parse(jsonOps, new JsonObject()).error().isPresent());
        assertTrue(MarketData.CODEC.parse(jsonOps, JsonParser.parseString("{\"goods\":[]}")).isSuccess());
    }

    @Test
    void goodsMutationAndRealRegistryLookupsNeverSubstituteAir(MinecraftServer server) {
        GoodsData goods = new GoodsData("minecraft:wheat", -8, -4, "rare", false, "food", "");
        assertSame(Items.WHEAT, goods.getItem());
        assertEquals(-8, goods.getCost(), "The constructor intentionally does not clamp");
        goods.setCost(-10);
        assertEquals(0, goods.getCost());
        goods.setCost(14);
        goods.setAmountSold(-7);
        goods.setRarity("common");
        goods.setActive(true);
        goods.setMarketType("general");
        goods.setDateAddedToMarket("2026-10-28");
        assertEquals(14, goods.getCost());
        assertEquals(-7, goods.getAmountSold());
        assertEquals("common", goods.getRarity());
        assertTrue(goods.isActive());
        assertEquals("general", goods.getMarketType());
        assertEquals("2026-10-28", goods.getDateAddedToMarket());
        assertNull(new GoodsData("farmupcraft:missing_goods_test", 0, 0, "", false, "", "").getItem());
        assertSame(Items.AIR, new GoodsData("minecraft:air", 0, 0, "", false, "", "").getItem());
        assertThrows(RuntimeException.class,
                () -> new GoodsData("INVALID ITEM ID", 0, 0, "", false, "", "").getItem());

        List<GoodsData> supplied = new ArrayList<>();
        MarketData market = new MarketData(supplied);
        assertSame(supplied, market.getGoods());
        supplied.add(goods);
        assertSame(goods, market.getGoods().getFirst());
        market.getGoods().clear();
        assertTrue(supplied.isEmpty());
    }

    @Test
    void commandHelpersPreserveAliasesDatesCostsAndFeedbackWithoutAWorld(MinecraftServer server) {
        // No fake level: these command paths need only registries and a non-broadcasting source.
        assertNull(server.overworld());
        RecordingSource feedback = new RecordingSource();
        CommandSourceStack source = new CommandSourceStack(feedback, Vec3.ZERO, Vec2.ZERO, null,
                PermissionSet.NO_PERMISSIONS, "test", Component.literal("test"), server, null);
        try (DataBase<String, GoodsData> database = new DataBase<>("farmupcraft:goods_data_database_new",
                new StringDataSerializer(), new CodecDataSerializer<>(GoodsData.CODEC),
                false, 0, false, server.registryAccess())) {
            GoodsData goods = GoodsData.CODEC.parse(JsonOps.INSTANCE, goodsJson()).getOrThrow();
            database.putData(goods.getItemId(), goods);
            for (String active : List.of("true", "1", "YES", "Active")) {
                GoodsData.setActiveStatusOfItem(source, goods.getItemId(), active, database);
                assertTrue(goods.isActive());
                assertEquals(LocalDate.now().toString(), goods.getDateAddedToMarket());
                assertEquals("Set active status of item: minecraft:wheat to " + active, feedback.last());
            }
            for (String inactive : List.of("false", "0", "NO", "Inactive")) {
                GoodsData.setActiveStatusOfItem(source, goods.getItemId(), inactive, database);
                assertFalse(goods.isActive());
                assertEquals("", goods.getDateAddedToMarket());
                assertEquals("Set active status of item: minecraft:wheat to " + inactive, feedback.last());
            }
            GoodsData.setActiveStatusOfItem(source, goods.getItemId(), "perhaps", database);
            assertEquals("Invalid active status: 'perhaps'. Use true/false, active/inactive, yes/no, or 1/0.",
                    feedback.last());
            assertFalse(goods.isActive());
            assertEquals("", goods.getDateAddedToMarket());
            GoodsData.setActiveStatusOfItem(source, "missing", "true", database);
            assertEquals("GoodsData not found for item: missing", feedback.last());
            GoodsData.setCostOfItem(source, goods.getItemId(), -5, database);
            assertEquals(0, goods.getCost());
            assertEquals("Set cost of item: minecraft:wheat to -5", feedback.last());
            GoodsData.setCostOfItem(null, goods.getItemId(), 31, database);
            assertEquals(31, goods.getCost());
            GoodsData.setCostOfItem(source, "missing", 10, database);
            assertEquals("GoodsData not found for item: missing", feedback.last());
            GoodsData.setCostOfItem(null, "missing", 10, database);
            assertEquals(1, database.getSize());
            assertSame(goods, database.getData(goods.getItemId()));
        }
    }

    @Test
    void inactiveGenericMarketLoaderStillSupportsApplyReplaceAndClear() {
        MarketDataJsonManager loader = new MarketDataJsonManager("farmupcraft/market", new Gson());
        Identifier first = Identifier.parse("farmupcraft:first");
        MarketData market = new MarketData(new ArrayList<>());
        MarketDataJsonManager.getMarketEntries().put(Identifier.parse("farmupcraft:stale"), market);
        loader.apply(Map.of(first, market), null, null);
        assertEquals(Map.of(first, market), MarketDataJsonManager.getMarketEntries());
        loader.apply(Map.of(), null, null);
        assertTrue(MarketDataJsonManager.getMarketEntries().isEmpty());
        MarketDataJsonManager.getMarketEntries().put(first, market);
        MarketDataJsonManager.clearEntries();
        assertTrue(MarketDataJsonManager.getMarketEntries().isEmpty());
    }

    private static JsonObject goodsJson() {
        return JsonParser.parseString("""
                {"itemId":"minecraft:wheat","cost":9,"amountSold":12,"rarity":"common",
                 "active":true,"marketType":"food","dateAddedToMarket":"2026-10-28"}
                """).getAsJsonObject();
    }

    private static CompoundTag goodsNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putString("itemId", "minecraft:wheat");
        tag.putInt("cost", 9);
        tag.putInt("amountSold", 12);
        tag.putString("rarity", "common");
        tag.putBoolean("active", true);
        tag.putString("marketType", "food");
        tag.putString("dateAddedToMarket", "2026-10-28");
        return tag;
    }

    private static final class RecordingSource implements CommandSource {
        private final List<String> messages = new ArrayList<>();

        @Override
        public void sendSystemMessage(Component message) {
            messages.add(message.getString());
        }

        @Override
        public boolean acceptsSuccess() { return true; }

        @Override
        public boolean acceptsFailure() { return true; }

        @Override
        public boolean shouldInformAdmins() { return false; }

        String last() {
            return messages.getLast();
        }
    }
}
