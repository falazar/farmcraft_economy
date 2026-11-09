package com.falazar.farmupcraft;

import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public final class GemContent {
    public static final List<String> TYPES = List.of("ruby", "opal", "sapphire", "topaz", "peridot");
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, FarmUpCraft.MODID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, FarmUpCraft.MODID);
    private static final Map<String, Supplier<? extends Block>> ORES = new LinkedHashMap<>();
    private static final Map<String, Supplier<Item>> GEMS = new LinkedHashMap<>();

    static {
        for (String gem : TYPES) {
            GEMS.put(gem, ITEMS.register(gem, () -> new Item(new Item.Properties())));
            registerOre(gem + "_ore", false);
            registerOre("deepslate_" + gem + "_ore", true);
        }
    }

    private GemContent() {}

    private static void registerOre(String name, boolean deepslate) {
        var ore = BLOCKS.register(name, () -> new DropExperienceBlock(BlockBehaviour.Properties.of()
                .strength(deepslate ? 4.5F : 3F, 3F).requiresCorrectToolForDrops()
                .sound(deepslate ? SoundType.DEEPSLATE : SoundType.STONE), UniformInt.of(1, 5)));
        ORES.put(name, ore);
        ITEMS.register(name, () -> new BlockItem(ore.get(), new Item.Properties()));
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
