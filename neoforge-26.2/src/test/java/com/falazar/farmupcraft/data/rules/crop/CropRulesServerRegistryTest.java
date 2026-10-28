package com.falazar.farmupcraft.data.rules.crop;

import com.falazar.farmupcraft.registry.CropRulesRegistry;
import com.falazar.farmupcraft.registry.FUCRegistries;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class CropRulesServerRegistryTest {
    // This fixture loads datapacks, not ServerLevels; world-dependent cache calls need a GameTest.
    @Test
    void datapackRegistriesAndLegacySerializerIdsCoexistWithBronzeCoin(MinecraftServer server) {
        var coins = server.registryAccess().lookupOrThrow(FUCRegistries.Keys.COIN);
        var bronze = coins.getValue(Identifier.parse("farmupcraft:bronze_coin"));
        assertNotNull(bronze);
        assertEquals(10, bronze.getBaseValue());
        assertEquals(Identifier.parse("farmupcraft:bronze_coin"), bronze.getId());

        assertNotNull(server.registryAccess().lookupOrThrow(FUCRegistries.Keys.CROP_RULES));
        Registry<CropRulesType<?>> serializers = FUCRegistries.CROP_RULES_TYPE_SERIALIZER_SUPP.get();
        assertSame(serializers, BuiltInRegistries.REGISTRY.getValue(Identifier.parse("farmupcraft:crop_rules_type")));
        assertSame(CropRulesRegistry.ITEM_LIST_CROP_RULE.get(),
                serializers.getValue(Identifier.parse("farmupcraft:item_list_crop_rule")));
        assertSame(CropRulesRegistry.TAG_BASED_RANDOM_CROP_RULE.get(),
                serializers.getValue(Identifier.parse("farmupcraft:tag_based_random_crop_rule")));

        var ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        var json = JsonParser.parseString("""
                {"type":"farmupcraft:item_list_crop_rule",
                 "allowed_crops":["minecraft:wheat","pamhc2crops:onionitem"],"random_crop_count":15}
                """);
        CropRules decoded = CropRules.DIRECT_CODEC.parse(ops, json).getOrThrow();
        assertInstanceOf(ItemListCropRule.class, decoded);
        assertEquals(2, decoded.getCropItems(null, 0).size());
        assertEquals(json, CropRules.DIRECT_CODEC.encodeStart(ops, decoded).getOrThrow());
    }

    @Test
    void loadedHarvestCraftCropAndSeedTagsResolveTheirRealCounterparts(MinecraftServer server) {
        Item onion = item("pamhc2crops:onionitem");
        Item onionSeed = item("pamhc2crops:onionseeditem");
        for (String tag : List.of("c:crops/onion", "c:seeds/onion")) {
            var json = JsonParser.parseString("""
                    {"type":"farmupcraft:tag_based_random_crop_rule","tag":"%s","random_crop_count":1}
                    """.formatted(tag));
            TagBasedRandomCropRule rule = assertInstanceOf(TagBasedRandomCropRule.class,
                    CropRules.DIRECT_CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
            var registry = server.registryAccess().lookupOrThrow(Registries.ITEM);
            List<Item> tagged = new ArrayList<>();
            registry.getTagOrEmpty(rule.getCropTag()).forEach(holder -> tagged.add(holder.value()));
            assertEquals(List.of(tag.equals("c:crops/onion") ? onion : onionSeed), tagged);

            List<Item> selected = TagBasedRandomCropRule.selectCrops(tagged, 0L, 17, rule.getRandomCropCount());
            assertEquals(tagged, selected);
            String pairedId = TagBasedRandomCropRule.pairedItemId(BuiltInRegistries.ITEM.getKey(selected.getFirst()).toString());
            assertSame(tag.equals("c:crops/onion") ? onionSeed : onion, item(pairedId));
            assertEquals(json, CropRules.DIRECT_CODEC.encodeStart(JsonOps.INSTANCE, rule).getOrThrow());
        }
    }

    @Test
    void actualTaggedItemsPreserveSelectionLimitsEmptyTagsAndNegativeCountErrors(MinecraftServer server) {
        var tag = TagKey.create(Registries.ITEM, Identifier.parse("c:crops"));
        List<Item> candidates = new ArrayList<>();
        server.registryAccess().lookupOrThrow(Registries.ITEM).getTagOrEmpty(tag)
                .forEach(holder -> candidates.add(holder.value()));
        assertFalse(candidates.isEmpty());
        assertTrue(candidates.contains(item("pamhc2crops:cornitem")));
        assertTrue(TagBasedRandomCropRule.selectCrops(candidates, 0L, 1, 0).isEmpty());
        assertThrows(IllegalArgumentException.class,
                () -> TagBasedRandomCropRule.selectCrops(candidates, 0L, 1, -1));
        var missing = TagKey.create(Registries.ITEM, Identifier.parse("farmupcraft:missing_crop_test"));
        assertFalse(server.registryAccess().lookupOrThrow(Registries.ITEM).getTagOrEmpty(missing).iterator().hasNext());
    }

    @Test
    void legacyHarvestCraftSpecialPairsExistInLoadedItemRegistry(MinecraftServer server) {
        var registry = server.registryAccess().lookupOrThrow(Registries.ITEM);
        for (String id : List.of(
                "onionitem", "onionseeditem",
                "mustardseedsitem", "mustardseedsseeditem",
                "sesameseedsitem", "sesameseedsseeditem")) {
            String fullId = "pamhc2crops:" + id;
            Item original = item(fullId);
            Item paired = item(TagBasedRandomCropRule.pairedItemId(fullId));
            assertNotSame(original, paired, fullId);
            assertSame(paired, registry.getValue(Identifier.parse(TagBasedRandomCropRule.pairedItemId(fullId))));
        }
    }

    private static Item item(String id) {
        var key = Identifier.parse(id);
        assertTrue(BuiltInRegistries.ITEM.containsKey(key), id);
        Item item = BuiltInRegistries.ITEM.getValue(key);
        assertNotNull(item, id);
        assertNotSame(Items.AIR, item, id);
        return item;
    }
}
