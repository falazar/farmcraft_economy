package com.falazar.farmupcraft.gametest;

import com.falazar.farmupcraft.data.BiomeRulesData;
import com.falazar.farmupcraft.data.CropBlockData;
import com.falazar.farmupcraft.data.CropBlockDataJsonManager;
import com.falazar.farmupcraft.data.CropItemData;
import com.falazar.farmupcraft.data.CropItemDataJsonManager;
import com.falazar.farmupcraft.events.CropDataEvents;
import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import com.falazar.farmupcraft.saveddata.BiomeRulesInstance;
import com.falazar.farmupcraft.saveddata.BiomeRulesManager;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;

@EventBusSubscriber(modid = FarmUpCraftNeoForge.MOD_ID)
public final class CropLifecycleGameTests {
    private CropLifecycleGameTests() {
    }

    @SubscribeEvent
    public static void registerFunctions(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, functions -> {
            functions.register(id("crop_cache_startup"), CropLifecycleGameTests::startup);
            functions.register(id("crop_cache_tag_refresh"), CropLifecycleGameTests::tagRefresh);
            functions.register(id("biome_rules_dimension_storage"), CropLifecycleGameTests::dimensionStorage);
        });
    }

    private static void startup(GameTestHelper helper) {
        // Do not populate here: this checks what the actual datapack/startup listeners left behind.
        var blocks = CropBlockDataJsonManager.getCropDataEntries().get(id("test_crops"));
        var items = CropItemDataJsonManager.getCropDataEntries().get(id("test_crops"));
        helper.assertTrue(blocks != null && items != null, "Shipped crop JSON must load before GameTests");
        helper.assertTrue(CropBlockDataJsonManager.getCropBlockDataEntries().get(Blocks.WHEAT) == blocks,
                "Startup must expand the real vanilla crop block tag");
        helper.assertTrue(CropItemDataJsonManager.getCropItemDataEntries().get(Items.WHEAT_SEEDS) == items,
                "Startup must expand the real vanilla crop item tag");
        var desert = helper.getLevel().registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.DESERT);
        helper.assertTrue(items.containsBiome(desert),
                "Startup must resolve allowed biome tags to holders from the running world");
        var manager = BiomeRulesManager.get(helper.getLevel());
        helper.assertTrue(manager.hasRules() && manager.hasItems(),
                "Startup must initialize world-owned biome rules and their crop reverse index");
        helper.succeed();
    }

    private static void tagRefresh(GameTestHelper helper) {
        var level = helper.getLevel();
        var server = level.getServer();
        var manager = BiomeRulesManager.get(level);
        helper.assertTrue(manager.hasRules() && manager.hasItems(), "World rules must already be initialized");
        var rulesBefore = new HashMap<>(manager.getData());
        var cropsBefore = new IdentityHashMap<BiomeRulesInstance, List<Item>>();
        rulesBefore.values().forEach(rule -> cropsBefore.put(rule, new ArrayList<>(rule.getCrops(level))));
        var blockDefinitions = new HashMap<>(CropBlockDataJsonManager.getCropDataEntries());
        var itemDefinitions = new HashMap<>(CropItemDataJsonManager.getCropDataEntries());
        var blockCache = new HashMap<>(CropBlockDataJsonManager.getCropBlockDataEntries());
        var itemCache = new HashMap<>(CropItemDataJsonManager.getCropItemDataEntries());
        var allowedBiomes = new IdentityHashMap<CropItemData, List<Holder<Biome>>>();
        itemDefinitions.values().forEach(data -> allowedBiomes.put(data, data.getAllowedBiomesList()));
        boolean dirty = manager.isDirty();
        var key = id("gametest_crop_tag_refresh");
        var woolBlocks = TagKey.create(Registries.BLOCK, Identifier.withDefaultNamespace("wool"));
        var woolItems = TagKey.create(Registries.ITEM, Identifier.withDefaultNamespace("wool"));
        var biomeTag = TagKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath("c", "is_desert"));
        var blockDefinition = new CropBlockData(woolBlocks, 0, true);
        var itemDefinition = new CropItemData(woolItems, biomeTag, true);
        helper.assertTrue(!blockDefinitions.containsKey(key) && !itemDefinitions.containsKey(key),
                "The fixture must not replace a datapack definition");
        helper.assertTrue(!blockCache.containsKey(Blocks.STRUCTURE_VOID) && !itemCache.containsKey(Items.STRUCTURE_VOID),
                "The stale cache sentinel must not be a configured crop");
        try {
            // Synchronous, without yielding a tick: keep all real definitions and never reload/clear global data.
            CropBlockDataJsonManager.getCropDataEntries().put(key, blockDefinition);
            CropItemDataJsonManager.getCropDataEntries().put(key, itemDefinition);
            CropBlockDataJsonManager.getCropBlockDataEntries().put(Blocks.STRUCTURE_VOID, blockDefinition);
            CropItemDataJsonManager.getCropItemDataEntries().put(Items.STRUCTURE_VOID, itemDefinition);
            refreshTags(helper);
            helper.assertTrue(!CropBlockDataJsonManager.getCropBlockDataEntries().containsKey(Blocks.STRUCTURE_VOID)
                            && !CropItemDataJsonManager.getCropItemDataEntries().containsKey(Items.STRUCTURE_VOID),
                    "The production crop tag-refresh listener must replace, not append to, derived caches");
            var actualBlocks = server.registryAccess().lookupOrThrow(Registries.BLOCK).getTagOrEmpty(woolBlocks);
            var actualItems = server.registryAccess().lookupOrThrow(Registries.ITEM).getTagOrEmpty(woolItems);
            int blockCount = 0;
            for (var holder : actualBlocks) {
                helper.assertTrue(CropBlockDataJsonManager.getCropBlockDataEntries().get(holder.value()) == blockDefinition,
                        "Tag refresh must expand every live wool block holder");
                blockCount++;
            }
            int itemCount = 0;
            for (var holder : actualItems) {
                helper.assertTrue(CropItemDataJsonManager.getCropItemDataEntries().get(holder.value()) == itemDefinition,
                        "Tag refresh must expand every live wool item holder");
                itemCount++;
            }
            helper.assertTrue(blockCount > 0 && itemCount > 0, "Real wool tags must not be empty");
            var desert = server.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.DESERT);
            helper.assertTrue(itemDefinition.containsBiome(desert), "Refresh must resolve live biome tags too");

            CropBlockDataJsonManager.getCropDataEntries().remove(key);
            CropItemDataJsonManager.getCropDataEntries().remove(key);
            refreshTags(helper);
            helper.assertValueEqual(CropBlockDataJsonManager.getCropBlockDataEntries(), blockCache,
                    "block cache after definition removal and another tag refresh");
            helper.assertValueEqual(CropItemDataJsonManager.getCropItemDataEntries(), itemCache,
                    "item cache after definition removal and another tag refresh");
            helper.assertValueEqual(manager.getData(), rulesBefore, "existing saved biome rule identities after refresh");
            cropsBefore.forEach((rule, crops) -> helper.assertValueEqual(rule.getCrops(level), crops,
                    "persisted crop selection after tag refresh"));
        } finally {
            CropBlockDataJsonManager.getCropDataEntries().clear();
            CropBlockDataJsonManager.getCropDataEntries().putAll(blockDefinitions);
            CropItemDataJsonManager.getCropDataEntries().clear();
            CropItemDataJsonManager.getCropDataEntries().putAll(itemDefinitions);
            CropBlockDataJsonManager.getCropBlockDataEntries().clear();
            CropBlockDataJsonManager.getCropBlockDataEntries().putAll(blockCache);
            CropItemDataJsonManager.getCropItemDataEntries().clear();
            CropItemDataJsonManager.getCropItemDataEntries().putAll(itemCache);
            allowedBiomes.forEach(CropItemData::setAllowedBiomesList);
            manager.setDirty(dirty);
        }
        helper.succeed();
    }

    private static void refreshTags(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        // Exercise the production listener with live world registries, not a full reload or event-bus dispatch.
        // Unrelated reload listeners require staging state that this focused fixture does not create.
        CropDataEvents.tagsUpdated(new TagsUpdatedEvent.ServerDataLoad(
                server.getServerResources().managers(), server.registryAccess()));
    }

    private static void dimensionStorage(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var overworld = server.overworld();
        var nether = server.getLevel(Level.NETHER);
        var end = server.getLevel(Level.END);
        helper.assertTrue(nether != null && end != null, "Real Nether and End server levels must exist");
        var worldRules = BiomeRulesManager.get(overworld);
        var netherRules = nether.getDataStorage().computeIfAbsent(BiomeRulesManager.TYPE);
        var endRules = end.getDataStorage().computeIfAbsent(BiomeRulesManager.TYPE);
        helper.assertTrue(netherRules != worldRules && endRules != worldRules && netherRules != endRules,
                "Each real ServerLevel data storage must own independent saved-data instances");
        helper.assertTrue(BiomeRulesManager.get(nether) == worldRules && BiomeRulesManager.get(end) == worldRules,
                "Public crop-rule access must route all dimensions to this server's overworld");
        var biome = server.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS);
        var previousNether = netherRules.getData().get(biome);
        var previousEnd = endRules.getData().get(biome);
        var previousWorld = worldRules.getData().get(biome);
        boolean netherDirty = netherRules.isDirty();
        boolean endDirty = endRules.isDirty();
        var netherRule = new BiomeRulesInstance(BiomeRulesData.EMPTY, List.of(Items.CARROT));
        var endRule = new BiomeRulesInstance(BiomeRulesData.EMPTY, List.of(Items.POTATO));
        try {
            netherRules.setBiomeRules(biome, netherRule);
            endRules.setBiomeRules(biome, endRule);
            helper.assertTrue(nether.getDataStorage().computeIfAbsent(BiomeRulesManager.TYPE).getBiomeRules(biome) == netherRule,
                    "Nether storage must retain its own rule");
            helper.assertTrue(end.getDataStorage().computeIfAbsent(BiomeRulesManager.TYPE).getBiomeRules(biome) == endRule,
                    "End storage must not reuse or overwrite the Nether rule");
            helper.assertTrue(BiomeRulesManager.get(nether).getData().get(biome) == previousWorld
                            && BiomeRulesManager.get(end).getData().get(biome) == previousWorld,
                    "Dimension-local storage mutations must not leak into routed overworld crop rules");
        } finally {
            restoreRule(netherRules, biome, previousNether, netherDirty);
            restoreRule(endRules, biome, previousEnd, endDirty);
        }
        helper.succeed();
    }

    private static void restoreRule(BiomeRulesManager manager, Holder<Biome> biome,
                                    BiomeRulesInstance previous, boolean dirty) {
        if (previous == null) {
            manager.removeBiomeRule(biome);
        } else {
            manager.setBiomeRules(biome, previous);
        }
        manager.setDirty(dirty);
    }

    private static Identifier id(String path) {
        return FarmUpCraftNeoForge.prefix(path);
    }
}
