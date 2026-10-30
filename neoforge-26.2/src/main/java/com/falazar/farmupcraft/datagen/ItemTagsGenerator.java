package com.falazar.farmupcraft.datagen;

import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import com.falazar.farmupcraft.util.FUCTags;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.TagsProvider;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.TagLoader;
import net.minecraft.util.Util;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.CropBlock;
import net.neoforged.neoforge.registries.DataPackRegistriesHooks;

import java.util.HashMap;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.Optional;
import java.util.stream.Stream;

public class ItemTagsGenerator extends TagsProvider<Item> {
    public ItemTagsGenerator(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup) {
        super(output, Registries.ITEM, lookup, FarmUpCraftNeoForge.MOD_ID);
    }

    public ItemTagsGenerator(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup, ResourceManager resources) {
        this(output, lookup.thenCompose(registries -> loadComponentRegistries(registries, resources)));
    }

    static CompletableFuture<HolderLookup.Provider> loadComponentRegistries(HolderLookup.Provider registries, ResourceManager resources) {
        // The datagen lookup contains vanilla and generated entries, not installed mods'
        // datapack entries referenced by delayed item components. Load those as on a server,
        // staging static tags without applying them to the live registries.
        var staticRegistries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        var pendingTags = TagLoader.loadTagsForExistingRegistries(resources, staticRegistries);
        var context = new HashMap<ResourceKey<? extends Registry<?>>, HolderLookup.RegistryLookup<?>>();
        registries.listRegistries().forEach(lookup -> context.put(lookup.key(), lookup));
        pendingTags.forEach(tags -> context.put(tags.key(), tags.lookup()));
        return RegistryDataLoader.load(resources, List.copyOf(context.values()),
                        DataPackRegistriesHooks.getDataPackRegistries(), Util.backgroundExecutor(), pendingTags)
                .thenApply(loaded -> {
                    loaded.listRegistries().forEach(lookup -> context.put(lookup.key(), lookup));
                    return HolderLookup.Provider.create(context.values().stream());
                });
    }

    @Override
    protected void addTags(HolderLookup.Provider registries) {
        var vanilla = tag(FUCTags.VANILLA_CROPS);
        for (var item : new Item[] {Items.POTATO, Items.WHEAT_SEEDS, Items.CARROT, Items.BEETROOT_SEEDS}) {
            vanilla.add(BuiltInRegistries.ITEM.getResourceKey(item).orElseThrow());
        }
        var seeds = tag(FUCTags.MODDED_SEEDS);
        var crops = tag(FUCTags.MODDED_CROPS);
        var foods = tag(FUCTags.MARKET_FOODS);
        BuiltInRegistries.ITEM.entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().identifier().toString())).forEach(entry -> {
            var id = entry.getKey().identifier();
            var item = entry.getValue();
            if (id.getNamespace().equals("pamhc2crops")) {
                if (id.getPath().contains("seed")) {
                    seeds.addOptional(entry.getKey());
                } else if (item instanceof BlockItem || isHarvestedCrop(id)) {
                    crops.addOptional(entry.getKey());
                }
            }
        });
        // Datagen has no bound item components; inspect initializer output without mutating registries.
        var componentContext = HolderLookup.Provider.create(registries.listRegistries().map(ItemTagsGenerator::componentLookup));
        var foodItems = new TreeSet<ResourceKey<Item>>(Comparator.comparing(key -> key.identifier().toString()));
        for (var pending : BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(componentContext)) {
            if (pending.key().equals(Registries.ITEM)) {
                pending.forEach((holder, components) -> {
                    var id = holder.key().identifier();
                    if ((id.getNamespace().equals("pamhc2foodcore") || id.getNamespace().equals("pamhc2foodextended"))
                            && components.has(DataComponents.FOOD)) {
                        foodItems.add(ResourceKey.create(Registries.ITEM, id));
                    }
                });
            }
        }
        foodItems.forEach(foods::addOptional);
        tag(FUCTags.VANILLA_AND_MODDED_CROPS)
                .addTag(FUCTags.VANILLA_CROPS).addOptionalTag(FUCTags.MODDED_SEEDS);
    }

    private static boolean isHarvestedCrop(Identifier itemId) {
        // HarvestCraft 26.2 separates harvested items from their planting items. Its
        // registrations pair <name>item with pam<name>crop; require the actual CropBlock,
        // excluding cooked foods while retaining non-food harvests such as cotton.
        var path = itemId.getPath();
        if (!path.endsWith("item")) {
            return false;
        }
        var blockId = Identifier.fromNamespaceAndPath(itemId.getNamespace(),
                "pam" + path.substring(0, path.length() - "item".length()) + "crop");
        return BuiltInRegistries.BLOCK.getValue(blockId) instanceof CropBlock;
    }

    private static <T> HolderLookup.RegistryLookup<T> componentLookup(HolderLookup.RegistryLookup<T> parent) {
        // RegistrySetBuilder's virtual tags are anonymous subclasses, rejected by NeoForge's
        // component validator. Intern unbound tag references in a private registry instead:
        // these are ordinary Named sets, without binding or changing any live registry.
        var tagReferences = new MappedRegistry<T>(
                ResourceKey.createRegistryKey(parent.key().identifier()), parent.registryLifecycle()).createRegistrationLookup();
        return new HolderLookup.RegistryLookup.Delegate<T>() {
            @Override
            public HolderLookup.RegistryLookup<T> parent() {
                return parent;
            }

            private HolderSet.Named<T> componentTag(HolderSet.Named<T> tag) {
                return tag.isBound() ? tag : tagReferences.getOrThrow(tag.key());
            }

            @Override
            public Optional<HolderSet.Named<T>> get(TagKey<T> key) {
                return parent.get(key).map(this::componentTag);
            }

            @Override
            public Stream<HolderSet.Named<T>> listTags() {
                return parent.listTags().map(this::componentTag);
            }
        };
    }
}
