package com.falazar.farmupcraft.datagen;

import com.falazar.farmupcraft.datagen.custom.BiomeRulesDataDataGenerator;
import com.falazar.farmupcraft.datagen.custom.CodecDataProvider;
import com.falazar.farmupcraft.datagen.custom.CropBlockDataDataGenerator;
import com.falazar.farmupcraft.datagen.custom.CropItemDataDataGenerator;
import com.falazar.farmupcraft.datagen.custom.MarketDataDataGenerator;
import com.falazar.farmupcraft.datagen.custom.MarketDataProvider;
import com.falazar.farmupcraft.data.MarketData;
import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import com.falazar.farmupcraft.util.FUCTags;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistrySetBuilder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.PackOutput;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.CropBlock;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class DataGeneratorsTest {
    private static final String MODID = FarmUpCraftNeoForge.MOD_ID;
    private static final Path OUTPUT = Path.of("build", "datagen-tests");
    private final PackOutput pack = new PackOutput(OUTPUT);
    private final Map<Path, JsonElement> written = new ConcurrentHashMap<>();
    private final CachedOutput cache = (path, bytes, hash) -> {
        assertTrue(path.startsWith(OUTPUT));
        assertNull(written.put(path, JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8))),
                "Providers must not overwrite each other's output");
    };

    @Test
    void cropBlocksRetainLegacyFieldSpellingAndValues() {
        new CropBlockDataDataGenerator(pack, MODID).run(cache).join();
        assertEquals(2, written.size());
        assertEquals(json("""
                {"crop":{"tag":"farmupcraft:vanilla_crops"},"growth_succes_rate":10.0,"populate_blocks":true}
                """), data("crop_block_data", "test_crops"));
        assertEquals(json("""
                {"crop":{"tag":"farmupcraft:modded_crops"},"growth_succes_rate":10.0,"populate_blocks":true}
                """), data("crop_block_data", "test_crops_modded"));
    }

    @Test
    void cropItemsUseModernCommonBiomeTag() {
        new CropItemDataDataGenerator(pack, MODID).run(cache).join();
        assertEquals(2, written.size());
        assertEquals(json("""
                {"crop":{"tag":"farmupcraft:vanilla_crops"},"allowed_biomes":"c:is_desert","populate_items":true}
                """), data("crop_item_data", "test_crops"));
        assertEquals(json("""
                {"crop":{"tag":"farmupcraft:modded_crops"},"allowed_biomes":"c:is_desert","populate_items":true}
                """), data("crop_item_data", "test_modded_crops"));
    }

    @Test
    void biomeRulesRetainOnlyActiveTemplatesAndOceanExclusion() {
        new BiomeRulesDataDataGenerator(pack, MODID).run(cache).join();
        assertEquals(2, written.size());
        assertEquals(json("""
                {"biome":"minecraft:is_overworld","biome_filter":"minecraft:is_ocean",
                 "crop_rules":{"type":"farmupcraft:tag_based_random_crop_rule",
                               "random_crop_count":15,"tag":"farmupcraft:vanilla_and_modded_crops"}}
                """), data("biome_rules", "overworld_rules"));
        assertEquals(json("""
                {"biome":"farmupcraft:biomes_o_plenty_overworld_biomes",
                 "crop_rules":{"type":"farmupcraft:tag_based_random_crop_rule",
                               "random_crop_count":15,"tag":"farmupcraft:vanilla_and_modded_crops"}}
                """), data("biome_rules", "bop_rules"));
    }

    @Test
    void marketTemplateWaitsForRegistriesWithoutInventingAnEmptyMarket(MinecraftServer server) {
        var lookup = new CompletableFuture<HolderLookup.Provider>();
        var result = new MarketDataDataGenerator(pack, MODID, lookup).run(cache);
        assertFalse(result.isDone());
        lookup.complete(server.registryAccess());
        result.join();
        assertTrue(written.isEmpty());
    }

    @Test
    void failedMarketLookupPropagates() {
        var failure = new IllegalStateException("registry bootstrap failed");
        var lookup = CompletableFuture.<HolderLookup.Provider>failedFuture(failure);
        var result = new MarketDataDataGenerator(pack, MODID, lookup).run(cache);
        assertSame(failure, assertThrows(CompletionException.class, result::join).getCause());
        assertTrue(written.isEmpty());
    }

    @Test
    void marketProviderStillSupportsExplicitEntries() {
        var provider = new MarketDataProvider(pack, MODID) {
            @Override
            protected void buildMarketData(java.util.function.Consumer<MarketDataConsumer> writer) {
                writer.accept(new MarketDataConsumer(id("explicit_market"), new MarketData(List.of())));
            }
        };
        provider.run(cache).join();
        assertEquals(json("{\"goods\":[]}"), data("market", "explicit_market"));
        assertEquals(1, written.size());
    }

    @Test
    void duplicateIdentifiersFailBeforeAnyFilesAreWritten() {
        var provider = new CodecDataProvider<>(pack, MODID, "duplicate_test", "Duplicate Test", Codec.STRING) {
            @Override
            protected void buildEntries(BiConsumer<Identifier, String> writer) {
                writer.accept(id("duplicate"), "first");
                writer.accept(id("duplicate"), "second");
            }
        };
        assertTrue(assertThrows(IllegalStateException.class, () -> provider.run(cache))
                .getMessage().contains("farmupcraft:duplicate"));
        assertTrue(written.isEmpty());
    }

    @Test
    void codecErrorsFailInsteadOfSilentlyDroppingOutput() {
        var provider = new CodecDataProvider<>(pack, MODID, "codec_test", "Codec Test", Codec.intRange(0, 1)) {
            @Override
            protected void buildEntries(BiConsumer<Identifier, Integer> writer) {
                writer.accept(id("valid"), 1);
                writer.accept(id("invalid"), 2);
            }
        };
        assertThrows(IllegalStateException.class, () -> provider.run(cache));
        assertTrue(written.isEmpty());
    }

    @Test
    void blockTagsUseModernPathsAndInstalledCropBlocks(MinecraftServer server) {
        new BlockTagsGenerator(pack, lookup(server)).run(cache).join();
        assertEquals(Set.of("minecraft:farmland"), values(tag("block", "farmland")));
        assertEquals(Set.of("minecraft:wheat", "minecraft:carrots", "minecraft:beetroots", "minecraft:potatoes"),
                values(tag("block", "vanilla_crops")));
        var expected = BuiltInRegistries.BLOCK.entrySet().stream()
                .filter(entry -> entry.getKey().identifier().getNamespace().equals("pamhc2crops")
                        && entry.getValue() instanceof CropBlock)
                .map(entry -> entry.getKey().identifier().toString()).collect(Collectors.toSet());
        assertEquals(expected, values(tag("block", "modded_crops")));
        assertEquals(3, written.size());
    }

    @Test
    void itemTagsPreserveOptionalModEntriesAndFoodComponentSelection(MinecraftServer server) {
        assertItemTags(server.registryAccess());
    }

    @Test
    void itemTagsInitializeWithDatagenVirtualTagsWithoutRebindingComponents(MinecraftServer server) {
        var registries = new RegistrySetBuilder().build(server.registryAccess());
        var virtualTag = registries.lookupOrThrow(net.minecraft.core.registries.Registries.ITEM)
                .getOrThrow(ItemTags.PLANKS);
        assertFalse(virtualTag.isBound());
        assertThrows(UnsupportedOperationException.class, virtualTag::contents);
        var originalComponents = Items.APPLE.components();
        assertItemTags(registries);
        assertSame(originalComponents, Items.APPLE.components());
        assertFalse(virtualTag.isBound());
        assertThrows(UnsupportedOperationException.class, virtualTag::contents);
    }

    private void assertItemTags(HolderLookup.Provider registries) {
        new ItemTagsGenerator(pack, CompletableFuture.completedFuture(registries)).run(cache).join();
        assertEquals(Set.of("minecraft:potato", "minecraft:wheat_seeds", "minecraft:carrot", "minecraft:beetroot_seeds"),
                values(tag("item", "vanilla_crops")));
        assertEquals(Set.of("#farmupcraft:vanilla_crops", "#farmupcraft:modded_seeds"),
                values(tag("item", "vanilla_and_modded_crops")));
        assertOptional(tag("item", "vanilla_and_modded_crops").getAsJsonObject()
                .getAsJsonArray("values").get(1));
        var seeds = new java.util.HashSet<String>();
        var crops = new java.util.HashSet<String>();
        var foods = new java.util.HashSet<String>();
        BuiltInRegistries.ITEM.entrySet().forEach(entry -> {
            var identifier = entry.getKey().identifier();
            if (identifier.getNamespace().equals("pamhc2crops")) {
                if (identifier.getPath().contains("seed")) {
                    seeds.add(identifier.toString());
                } else if (entry.getValue() instanceof BlockItem) {
                    crops.add(identifier.toString());
                }
            }
            if (Set.of("pamhc2foodcore", "pamhc2foodextended").contains(identifier.getNamespace())
                    && entry.getValue().components().has(DataComponents.FOOD)) {
                foods.add(identifier.toString());
            }
        });
        BuiltInRegistries.BLOCK.entrySet().stream()
                .filter(entry -> entry.getKey().identifier().getNamespace().equals("pamhc2crops")
                        && entry.getValue() instanceof CropBlock)
                .map(entry -> entry.getKey().identifier().getPath())
                .filter(path -> path.startsWith("pam") && path.endsWith("crop") && !path.contains("seed"))
                .map(path -> "pamhc2crops:" + path.substring(3, path.length() - 4) + "item")
                .forEach(crops::add);
        assertEquals(seeds, values(tag("item", "modded_seeds")));
        assertEquals(crops, values(tag("item", "modded_crops")));
        assertEquals(foods, values(tag("item", "market_foods")));
        assertTrue(foods.contains("pamhc2foodcore:applejellyitem"));
        assertFalse(foods.contains("minecraft:apple"));
        assertFalse(foods.contains("minecraft:wheat"));
        for (String name : List.of("modded_seeds", "modded_crops", "market_foods")) {
            tag("item", name).getAsJsonObject().getAsJsonArray("values").forEach(DataGeneratorsTest::assertOptional);
            var ordered = StreamSupport.stream(
                            tag("item", name).getAsJsonObject().getAsJsonArray("values").spliterator(), false)
                    .map(entry -> entry.getAsJsonObject().get("id").getAsString()).toList();
            assertEquals(ordered.size(), ordered.stream().distinct().count());
            assertEquals(ordered.stream().sorted().toList(), ordered);
        }
        assertEquals(5, written.size());
    }

    @Test
    void itemCropTagsRestoreHarvestedItemsWithoutDroppingGardens(MinecraftServer server) {
        new ItemTagsGenerator(pack, lookup(server)).run(cache).join();
        var crops = values(tag("item", "modded_crops"));
        assertEquals(101, crops.size());
        assertEquals(95, crops.stream().filter(id -> id.endsWith("item")).count());
        assertTrue(crops.containsAll(Set.of("pamhc2crops:amaranthitem", "pamhc2crops:cottonitem",
                "pamhc2crops:calabashitem", "pamhc2crops:guaranaitem", "pamhc2crops:papyrusitem",
                "pamhc2crops:aridgarden", "pamhc2crops:frostgarden", "pamhc2crops:shadedgarden",
                "pamhc2crops:soggygarden", "pamhc2crops:tropicalgarden", "pamhc2crops:windygarden")));
        assertFalse(crops.contains("pamhc2crops:amaranthseeditem"));
        assertFalse(crops.contains("pamhc2crops:bakedarrowrootitem"));
        assertFalse(crops.contains("pamhc2crops:mustardseedsitem"));
    }

    @Test
    void itemTagsLoadInstalledDatapackEntriesMissingFromVanillaDatagenLookup(MinecraftServer server) {
        var vanilla = VanillaRegistries.createLookup();
        var trimKey = net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.TRIM_MATERIAL,
                Identifier.fromNamespaceAndPath("biomesoplenty", "glowworm_silk"));
        assertTrue(vanilla.lookupOrThrow(net.minecraft.core.registries.Registries.TRIM_MATERIAL).get(trimKey).isEmpty());
        var originalComponents = Items.APPLE.components();
        var loaded = ItemTagsGenerator.loadComponentRegistries(vanilla, server.getResourceManager()).join();
        assertTrue(loaded.lookupOrThrow(net.minecraft.core.registries.Registries.TRIM_MATERIAL).get(trimKey).isPresent());
        assertItemTags(loaded);
        assertSame(originalComponents, Items.APPLE.components());
        assertTrue(vanilla.lookupOrThrow(net.minecraft.core.registries.Registries.TRIM_MATERIAL).get(trimKey).isEmpty());
    }

    @Test
    void biomeTagsUseOptionalNestedTagAndExplicitOceanRemoval(MinecraftServer server) {
        new BiomeTagsProvider(pack, lookup(server)).run(cache).join();
        assertTrue(values(tag("worldgen/biome", "empty_biome_tag")).isEmpty());
        var bop = tag("worldgen/biome", "biomes_o_plenty_overworld_biomes");
        assertEquals(server.registryAccess().lookupOrThrow(Registries.BIOME)
                        .getOrThrow(BiomeTags.IS_OVERWORLD).stream()
                        .map(holder -> holder.unwrapKey().orElseThrow().identifier())
                        .filter(id -> id.getNamespace().equals("biomesoplenty"))
                        .map(Identifier::toString).collect(Collectors.toSet()),
                values(bop));
        bop.getAsJsonObject().getAsJsonArray("values").forEach(DataGeneratorsTest::assertOptional);
        var aggregate = tag("worldgen/biome", "biomes_o_plenty_biomes");
        assertEquals(Set.of("#" + FUCTags.BIOMES_O_PLENTY_OVERWORLD_TAG.location()), values(aggregate));
        aggregate.getAsJsonObject().getAsJsonArray("values").forEach(DataGeneratorsTest::assertOptional);
        var noOcean = tag("worldgen/biome", "overworld_no_ocean").getAsJsonObject();
        assertEquals(Set.of("#minecraft:is_overworld"), values(noOcean));
        assertEquals(json("[\"#minecraft:is_ocean\"]"), noOcean.get("remove"));
        assertEquals(4, written.size());
    }

    @Test
    void coinBootstrapProducesReviewedBuiltinEntry(MinecraftServer server) {
        new FUCDatapackBuiltinEntriesProvider(pack, lookup(server)).run(cache).join();
        assertEquals(json("""
                {"translation_key":"bronze_coin","value":10,"rarity":"common","id":"farmupcraft:bronze_coin"}
                """), data("coin", "bronze_coin"));
        assertEquals(1, written.size());
    }

    @Test
    void serverDatagenListenerIsAutomaticallySubscribed() throws ReflectiveOperationException {
        var subscriber = DataGenerators.class.getAnnotation(EventBusSubscriber.class);
        assertNotNull(subscriber);
        assertEquals(MODID, subscriber.modid());
        assertNotNull(DataGenerators.class.getMethod("gatherData", GatherDataEvent.Server.class)
                .getAnnotation(SubscribeEvent.class));
    }

    private JsonElement data(String directory, String name) {
        return written.get(OUTPUT.resolve("data").resolve(MODID).resolve("farmupcraft")
                .resolve(directory).resolve(name + ".json"));
    }

    private JsonElement tag(String type, String name) {
        return written.get(OUTPUT.resolve("data").resolve(MODID).resolve("tags")
                .resolve(type).resolve(name + ".json"));
    }

    private static Set<String> values(JsonElement tag) {
        assertNotNull(tag);
        return StreamSupport.stream(tag.getAsJsonObject().getAsJsonArray("values").spliterator(), false)
                .map(entry -> entry.isJsonPrimitive() ? entry.getAsString() : entry.getAsJsonObject().get("id").getAsString())
                .collect(Collectors.toSet());
    }

    private static void assertOptional(JsonElement entry) {
        assertFalse(entry.getAsJsonObject().get("required").getAsBoolean());
    }

    private static CompletableFuture<HolderLookup.Provider> lookup(MinecraftServer server) {
        return CompletableFuture.completedFuture(server.registryAccess());
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }

    private static JsonElement json(String value) {
        return JsonParser.parseString(value);
    }
}
