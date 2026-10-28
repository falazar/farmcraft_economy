package com.falazar.farmupcraft.data.rules.crop;

import com.falazar.farmupcraft.registry.CropRulesRegistry;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class CropRulesTest {
    @Test
    void itemListCodecKeepsLegacyShapeDuplicatesAndUnrestrictedCounts() {
        for (int count : new int[]{Integer.MIN_VALUE, -1, 0, 1, Integer.MAX_VALUE}) {
            JsonElement json = json("""
                    {"allowed_crops":["minecraft:wheat","minecraft:carrot","minecraft:wheat"],"random_crop_count":%d}
                    """.formatted(count));
            ItemListCropRule rule = ItemListCropRule.CODEC.codec().parse(JsonOps.INSTANCE, json).getOrThrow();
            assertEquals(List.of(Items.WHEAT, Items.CARROT, Items.WHEAT), rule.getAllowedCrops());
            assertEquals(count, rule.getRandomCropCount());
            assertSame(rule.getAllowedCrops(), rule.getCropItems(null, 50));
            assertSame(CropRulesRegistry.ITEM_LIST_CROP_RULE.get(), rule.type());
            assertSame(ItemListCropRule.CODEC, ItemListCropRule.TYPE.codec());
            assertEquals(json, ItemListCropRule.CODEC.codec().encodeStart(JsonOps.INSTANCE, rule).getOrThrow());
        }
        assertEquals(15, new ItemListCropRule(new ArrayList<>()).getRandomCropCount());
        assertEquals(0, ItemListCropRule.EMPTY.getRandomCropCount());
        assertTrue(ItemListCropRule.EMPTY.getCropItems(null, 0).isEmpty());
    }

    @Test
    void itemListKeepsMutableListIdentityAndIgnoresSelectionCount() {
        var crops = new ArrayList<>(List.of(Items.WHEAT, Items.CARROT));
        ItemListCropRule rule = new ItemListCropRule(crops, 0);
        assertSame(crops, rule.getAllowedCrops());
        assertSame(crops, rule.getCropItems(null, Integer.MIN_VALUE));
        crops.add(Items.POTATO);
        assertEquals(3, rule.getCropItems(null, Integer.MAX_VALUE).size());
    }

    @Test
    void dispatchRoundTripsExactLegacySerializerIdsAndFields() {
        for (String legacy : List.of(
                """
                {"type":"farmupcraft:item_list_crop_rule","allowed_crops":["minecraft:carrot"],"random_crop_count":-2}
                """,
                """
                {"type":"farmupcraft:tag_based_random_crop_rule","tag":"c:crops","random_crop_count":3}
                """)) {
            JsonElement json = json(legacy);
            CropRules rule = CropRules.DIRECT_CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
            assertEquals(json, CropRules.DIRECT_CODEC.encodeStart(JsonOps.INSTANCE, rule).getOrThrow());
        }
    }

    @Test
    void dispatchAndItemListRejectUnknownItemsSerializersAndMissingFields() {
        for (String invalid : List.of(
                "{}",
                "{\"type\":\"farmupcraft:unknown\"}",
                "{\"type\":\"farmupcraft:item_list_crop_rule\",\"allowed_crops\":[]}",
                "{\"type\":\"farmupcraft:item_list_crop_rule\",\"random_crop_count\":1}",
                "{\"type\":\"farmupcraft:item_list_crop_rule\",\"allowed_crops\":[\"missing:crop\"],\"random_crop_count\":1}",
                "{\"type\":\"farmupcraft:tag_based_random_crop_rule\",\"tag\":\"#c:crops\",\"random_crop_count\":1}",
                "{\"type\":\"farmupcraft:tag_based_random_crop_rule\",\"random_crop_count\":1}")) {
            assertTrue(CropRules.DIRECT_CODEC.parse(JsonOps.INSTANCE, json(invalid)).error().isPresent(), invalid);
        }
    }

    @Test
    void harvestCraftItemIdsDecodeWithoutOldItemRegistrationClass() {
        JsonElement json = json("""
                {"type":"farmupcraft:item_list_crop_rule",
                 "allowed_crops":["pamhc2crops:onionitem","pamhc2crops:onionseeditem"],
                 "random_crop_count":1}
                """);
        ItemListCropRule rule = assertInstanceOf(ItemListCropRule.class,
                CropRules.DIRECT_CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
        assertEquals(2, rule.getCropItems(null, 0).size());
        for (var item : rule.getAllowedCrops()) {
            assertNotSame(Items.AIR, item);
            assertEquals("pamhc2crops", BuiltInRegistries.ITEM.getKey(item).getNamespace());
        }
        assertEquals(json, CropRules.DIRECT_CODEC.encodeStart(JsonOps.INSTANCE, rule).getOrThrow());
    }

    @Test
    void tagCodecKeepsLegacyShapeAndUnrestrictedCounts() {
        for (int count : new int[]{Integer.MIN_VALUE, -1, 0, 15, Integer.MAX_VALUE}) {
            JsonElement json = json("{\"tag\":\"c:crops\",\"random_crop_count\":" + count + "}");
            TagBasedRandomCropRule rule = TagBasedRandomCropRule.CODEC.codec().parse(JsonOps.INSTANCE, json).getOrThrow();
            assertEquals(TagKey.create(Registries.ITEM, Identifier.parse("c:crops")), rule.getCropTag());
            assertEquals(count, rule.getRandomCropCount());
            assertSame(TagBasedRandomCropRule.CODEC, TagBasedRandomCropRule.TYPE.codec());
            assertEquals(json, TagBasedRandomCropRule.CODEC.codec().encodeStart(JsonOps.INSTANCE, rule).getOrThrow());
        }
    }

    @Test
    void tagCodecRejectsMissingFieldsAndInvalidTagShapes() {
        for (String invalid : List.of(
                "{}",
                "{\"tag\":\"c:crops\"}",
                "{\"random_crop_count\":1}",
                "{\"tag\":\"#c:crops\",\"random_crop_count\":1}",
                "{\"tag\":\"INVALID:crop\",\"random_crop_count\":1}",
                "{\"tag\":\"c:crops\",\"random_crop_count\":\"one\"}")) {
            assertTrue(TagBasedRandomCropRule.CODEC.codec().parse(JsonOps.INSTANCE, json(invalid)).error().isPresent(), invalid);
        }
    }

    @Test
    void randomSelectionUsesJavaShuffleSeedPlusIdAndLeavesSourceUnchanged() {
        List<String> crops = List.of("wheat", "carrot", "potato", "beetroot", "melon");
        for (long seed : new long[]{0, -50, Long.MAX_VALUE}) {
            for (int id : new int[]{0, 1, -3, Integer.MAX_VALUE}) {
                List<String> expected = new ArrayList<>(crops);
                Collections.shuffle(expected, new Random(seed + id));
                List<String> actual = TagBasedRandomCropRule.selectCrops(crops, seed, id, 3);
                assertEquals(expected.subList(0, 3), actual);
                assertEquals(actual, TagBasedRandomCropRule.selectCrops(crops, seed, id, 3));
                actual.clear();
                assertEquals(5, crops.size());
            }
        }
        assertEquals(5, TagBasedRandomCropRule.selectCrops(crops, 0, 0, 100).size());
        assertTrue(TagBasedRandomCropRule.selectCrops(crops, 0, 0, 0).isEmpty());
        assertTrue(TagBasedRandomCropRule.selectCrops(List.of(), 0, 0, 1).isEmpty());
        assertThrows(IllegalArgumentException.class,
                () -> TagBasedRandomCropRule.selectCrops(crops, 0, 0, -1));
        assertThrows(IllegalArgumentException.class,
                () -> TagBasedRandomCropRule.selectCrops(List.of(), 0, 0, -1));
    }

    @Test
    void harvestCraftPairingKeepsLegacyExceptionsAndStringSplitSemantics() {
        assertPair("onionitem", "onionseeditem");
        assertPair("onionseeditem", "onionitem");
        assertPair("mustardseedsitem", "mustardseedsseeditem");
        assertPair("mustardseedsseeditem", "mustardseedsitem");
        assertPair("sesameseedsitem", "sesameseedsseeditem");
        assertPair("sesameseedsseeditem", "sesameseedsitem");
        assertPair("flaxitem", "flaxseeditem");
        assertPair("seed", "seed");
        assertPair("seedseedseeditem", "seedseedseeditem");
    }

    private static void assertPair(String input, String expected) {
        assertEquals("pamhc2crops:" + expected, TagBasedRandomCropRule.pairedItemId("pamhc2crops:" + input));
    }

    private static JsonElement json(String text) {
        return JsonParser.parseString(text);
    }
}
