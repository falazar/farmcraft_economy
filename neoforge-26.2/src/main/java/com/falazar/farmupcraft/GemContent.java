package com.falazar.farmupcraft;

import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public final class GemContent {
    public static final List<String> TYPES = List.of("ruby", "opal", "sapphire", "topaz", "peridot");
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, FarmUpCraftNeoForge.MOD_ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, FarmUpCraftNeoForge.MOD_ID);
    private static final Map<String, Supplier<? extends Block>> ORES = new LinkedHashMap<>();
    private static final Map<String, Supplier<Item>> GEMS = new LinkedHashMap<>();

    static {
        for (String gem : TYPES) {
            var gemId = Identifier.fromNamespaceAndPath(FarmUpCraftNeoForge.MOD_ID, gem);
            GEMS.put(gem, ITEMS.register(gem, () -> new Item(new Item.Properties()
                    .setId(ResourceKey.create(Registries.ITEM, gemId)))));
            registerOre(gem + "_ore", false);
            registerOre("deepslate_" + gem + "_ore", true);
        }
    }

    private GemContent() {}

    private static void registerOre(String name, boolean deepslate) {
        var id = Identifier.fromNamespaceAndPath(FarmUpCraftNeoForge.MOD_ID, name);
        var ore = BLOCKS.register(name, () -> new DropExperienceBlock(UniformInt.of(1, 5),
                BlockBehaviour.Properties.of().setId(ResourceKey.create(Registries.BLOCK, id))
                        .strength(deepslate ? 4.5F : 3F, 3F).requiresCorrectToolForDrops()
                        .sound(deepslate ? SoundType.DEEPSLATE : SoundType.STONE)));
        ORES.put(name, ore);
        ITEMS.register(name, () -> new BlockItem(ore.get(), new Item.Properties()
                .setId(ResourceKey.create(Registries.ITEM, id)).useBlockDescriptionPrefix()));
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        bus.addListener(GemContent::creativeTab);
    }

    public static Block ore(String gem, boolean deepslate) {
        var ore = ORES.get((deepslate ? "deepslate_" : "") + gem + "_ore");
        if (ore == null) throw new IllegalArgumentException("Unknown gem: " + gem);
        return ore.get();
    }

    public static Item gem(String gem) {
        var item = GEMS.get(gem);
        if (item == null) throw new IllegalArgumentException("Unknown gem: " + gem);
        return item.get();
    }

    private static void creativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(CreativeModeTabs.INGREDIENTS)) {
            GEMS.values().forEach(item -> event.accept(item.get()));
        } else if (event.getTabKey().equals(CreativeModeTabs.NATURAL_BLOCKS)) {
            ORES.values().forEach(block -> event.accept(block.get()));
        }
    }
}
