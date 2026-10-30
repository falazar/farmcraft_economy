package com.falazar.farmupcraft.currency;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class CurrencyTest {
    private static final String BRONZE_JSON = """
            {
              "id": "farmupcraft:bronze_coin",
              "rarity": "common",
              "translation_key": "bronze_coin",
              "value": 10
            }
            """;

    @Test
    void bronzeCoinResourceMatchesLegacyShapeAndRoundTrips() throws Exception {
        JsonElement expected = JsonParser.parseString(BRONZE_JSON);
        try (var stream = CurrencyTest.class.getResourceAsStream(
                "/data/farmupcraft/farmupcraft/coin/bronze_coin.json")) {
            assertNotNull(stream);
            JsonElement resource = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            assertEquals(expected, resource);
            Coin coin = Coin.DIRECT_CODEC.parse(JsonOps.INSTANCE, resource).getOrThrow();
            assertEquals(Identifier.fromNamespaceAndPath("farmupcraft", "bronze_coin"), coin.getId());
            assertEquals("bronze_coin", coin.getDisplayName());
            assertEquals(10, coin.getBaseValue());
            assertSame(Rarity.COMMON, coin.getRarity());
            assertEquals(expected, Coin.DIRECT_CODEC.encodeStart(JsonOps.INSTANCE, coin).getOrThrow());
        }
    }

    @Test
    void coinStackUsesInlineCoinAndExactLegacyFieldNames() {
        JsonElement expected = JsonParser.parseString("{\"coin\":" + BRONZE_JSON + ",\"amount\":7}");
        CoinStack stack = CoinStack.CODEC.parse(JsonOps.INSTANCE, expected).getOrThrow();
        assertEquals(7, stack.getAmount());
        assertEquals(10, stack.getCoin().getBaseValue());
        assertEquals(stack.getCoin().getId(), stack.getId());
        assertEquals(expected, CoinStack.CODEC.encodeStart(JsonOps.INSTANCE, stack).getOrThrow());
    }

    @Test
    void coinAndStackCodecsPreserveUnrestrictedLegacyIntegers() {
        for (int value : new int[]{Integer.MIN_VALUE, -1, 0, Integer.MAX_VALUE}) {
            Coin coin = new Coin(Identifier.fromNamespaceAndPath("farmupcraft", "test"), "", value, Rarity.EPIC);
            Coin decoded = Coin.DIRECT_CODEC.parse(JsonOps.INSTANCE,
                    Coin.DIRECT_CODEC.encodeStart(JsonOps.INSTANCE, coin).getOrThrow()).getOrThrow();
            assertEquals(value, decoded.getBaseValue());
            assertEquals("", decoded.getDisplayName());
            assertSame(Rarity.EPIC, decoded.getRarity());
            CoinStack stack = new CoinStack(coin, value);
            CoinStack decodedStack = CoinStack.CODEC.parse(JsonOps.INSTANCE,
                    CoinStack.CODEC.encodeStart(JsonOps.INSTANCE, stack).getOrThrow()).getOrThrow();
            assertEquals(value, decodedStack.getAmount());
            assertEquals(value, decodedStack.getCoin().getBaseValue());
        }
    }

    @Test
    void rarityTiersNamesAndCodecMatchLegacyValues() {
        String[] names = {"Common", "Uncommon", "Rare", "Epic", "Legendary"};
        assertEquals(names.length, Rarity.values().length);
        for (int i = 0; i < names.length; i++) {
            Rarity rarity = Rarity.values()[i];
            String serialized = names[i].toLowerCase(Locale.ROOT);
            assertEquals(i + 1, rarity.getTier());
            assertEquals(names[i], rarity.getDisplayName());
            assertEquals(serialized, rarity.getSerializedName());
            assertSame(rarity, Rarity.fromTier(i + 1));
            assertSame(rarity, Rarity.byName(serialized));
            assertSame(rarity, Rarity.byName(names[i].toUpperCase(Locale.ROOT)));
            JsonElement json = JsonParser.parseString("\"" + serialized + "\"");
            assertEquals(json, Rarity.CODEC.encodeStart(JsonOps.INSTANCE, rarity).getOrThrow());
            assertSame(rarity, Rarity.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
        }
        for (int tier : new int[]{Integer.MIN_VALUE, -1, 0, 6, Integer.MAX_VALUE}) {
            assertSame(Rarity.COMMON, Rarity.fromTier(tier));
        }
        assertThrows(IllegalArgumentException.class, () -> Rarity.byName("unknown"));
        assertTrue(Rarity.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("\"unknown\"")).error().isPresent());
        assertTrue(Rarity.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("\"COMMON\"")).error().isPresent());
    }

    @Test
    void coinStackMutationsKeepLegacyEmptyAndOverflowBehavior() {
        Coin coin = Coin.DIRECT_CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(BRONZE_JSON)).getOrThrow();
        CoinStack stack = new CoinStack(coin, 2);
        assertSame(coin, stack.getCoin());
        assertFalse(stack.isEmpty());
        stack.increment(3);
        assertEquals(5, stack.getAmount());
        stack.increment(-5);
        assertEquals(0, stack.getAmount());
        assertTrue(stack.isEmpty());
        stack.setAmount(-4);
        assertEquals(-4, stack.getAmount());
        assertTrue(stack.isEmpty());
        stack.increment(5);
        assertEquals(1, stack.getAmount());
        assertFalse(stack.isEmpty());
        stack.setAmount(Integer.MAX_VALUE);
        stack.increment(1);
        assertEquals(Integer.MIN_VALUE, stack.getAmount());
        assertTrue(stack.isEmpty());
        assertEquals(10, coin.getBaseValue());
        assertSame(coin, stack.getCoin());
    }
}
