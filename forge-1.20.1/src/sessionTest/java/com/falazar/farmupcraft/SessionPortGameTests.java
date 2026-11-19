package com.falazar.farmupcraft;

import com.falazar.farmupcraft.command.BuildCommand;
import com.falazar.farmupcraft.client.ItemUseRecipe;
import com.falazar.farmupcraft.command.MarketCommand;
import com.falazar.farmupcraft.data.GoodsData;
import com.falazar.farmupcraft.database.message.BuildPreviewPacket;
import com.falazar.farmupcraft.events.ModEvents;
import com.falazar.farmupcraft.util.BuildPreviewGeometry;
import com.falazar.farmupcraft.util.NpcDataLoader;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.core.NonNullList;

import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

@GameTestHolder("farmupcraft")
@PrefixGameTestTemplate(false)
@Mod.EventBusSubscriber(modid = FarmUpCraft.MODID)
public final class SessionPortGameTests {
    @SubscribeEvent
    public static void registerTemplate(LevelEvent.Load event) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if (event.getLevel() instanceof ServerLevel level) {
            var template = level.getStructureManager().getOrCreate(new ResourceLocation("farmupcraft", "empty"));
            template.load(level.registryAccess().lookupOrThrow(Registries.BLOCK),
                    net.minecraft.nbt.TagParser.parseTag(
                            "{size:[3,3,3],palette:[{Name:\"minecraft:air\"}],blocks:[],entities:[]}"));
        }
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void biomeCoverage(GameTestHelper helper) {
        var biomes = helper.getLevel().registryAccess().registryOrThrow(Registries.BIOME);
        Map<String, String> gardens = Map.of("tropics", "tropical_garden_placed_jungle",
                "lush_desert", "arid_garden_placed", "old_growth_woodland", "shaded_garden_placed",
                "origin_valley", "windy_garden_placed_plains", "snowblossom_grove", "frost_garden_placed");
        gardens.forEach((biome, feature) -> helper.assertTrue(features(biomes.getHolderOrThrow(
                        net.minecraft.resources.ResourceKey.create(Registries.BIOME,
                                new ResourceLocation("biomesoplenty", biome))).value()).contains(
                        new ResourceLocation("pamhc2crops", feature)),
                "New BOP garden biome is attached: " + biome));
        Set<String> allTrees = new HashSet<>();
        for (var biome : biomes.holders().toList()) {
            if (!biome.key().location().getNamespace().equals("biomesoplenty")) continue;
            Set<ResourceLocation> seen = new HashSet<>();
            for (var step : biome.value().getGenerationSettings().features()) {
                for (var feature : step) {
                    var id = feature.unwrapKey().orElseThrow().location();
                    if (!id.getNamespace().equals("pamhc2trees")) continue;
                    helper.assertTrue(seen.add(id), "No duplicate tree attempt: " + biome.key() + " / " + id);
                    allTrees.add(id.getPath());
                }
            }
        }
        helper.assertTrue(allTrees.size() == 50, "All fifty HarvestCraft species have BOP homes");
        for (String biome : List.of("rainforest", "rocky_rainforest", "fungal_jungle", "tropics")) {
            var actual = features(biomes.get(new ResourceLocation("biomesoplenty", biome)));
            for (String tree : List.of("banana", "coconut", "mango", "peppercorn", "vanillabean", "nutmeg")) {
                helper.assertTrue(actual.contains(new ResourceLocation("pamhc2trees", tree + "_placed")),
                        "Tropical species attached: " + tree + " in " + biome);
            }
        }
        for (String biome : List.of("coniferous_forest", "fir_clearing", "snowblossom_grove")) {
            var actual = features(biomes.get(new ResourceLocation("biomesoplenty", biome)));
            helper.assertTrue(actual.contains(new ResourceLocation("pamhc2trees", "maple_placed"))
                    && actual.contains(new ResourceLocation("pamhc2trees", "pinenut_placed")),
                    "Cool biome has maple and pinenut: " + biome);
        }
        helper.succeed();
    }

    private static Set<ResourceLocation> features(net.minecraft.world.level.biome.Biome biome) {
        Set<ResourceLocation> result = new HashSet<>();
        for (var step : biome.getGenerationSettings().features()) {
            for (var feature : step) result.add(feature.unwrapKey().orElseThrow().location());
        }
        return result;
    }

    @GameTest(template = "empty")
    public static void marketImport(GameTestHelper helper) {
        var goods = ModEvents.getGoodsDataDatabase();
        Map<String, GoodsData> before = new HashMap<>();
        for (String id : goods.getKeys()) before.put(id, goods.getData(id));
        var additions = Map.of("pamhc2foodcore:appledonutitem", "food", "minecraft:bamboo_planks", "wood",
                "minecraft:cobblestone", "stone", "minecraft:dandelion", "general");
        try {
            goods.removeDataBatch(additions.keySet());
            var sentinel = new GoodsData("minecraft:oak_log", 37, 81, "common", true, "wood", "");
            goods.putData(sentinel.getItemId(), sentinel);
            MarketCommand.updateCatalogsOnLogin(helper.getLevel().getServer());
            additions.forEach((id, type) -> {
                var entry = goods.getData(id);
                helper.assertTrue(entry != null && entry.getMarketType().equals(type) && !entry.isActive(),
                        "Missing item imported inactive into correct category: " + id);
            });
            helper.assertTrue(goods.getData(sentinel.getItemId()) == sentinel && sentinel.getCost() == 37
                    && sentinel.getAmountSold() == 81 && sentinel.isActive(), "Existing market goods unchanged");
            goods.removeDataBatch(List.of("minecraft:bamboo_planks"));
            MarketCommand.updateCatalogsOnLogin(helper.getLevel().getServer());
            helper.assertTrue(goods.getData("minecraft:bamboo_planks") == null, "Successful login check runs once");
        } finally {
            goods.removeDataBatch(new ArrayList<>(goods.getKeys()));
            before.forEach(goods::putData);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void previewPacket(GameTestHelper helper) {
        var state = Blocks.OAK_STAIRS.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST);
        var source = List.of(new BuildPreviewGeometry.Entry(1, 2, 3, 0x123456));
        var destination = List.of(new BuildPreviewGeometry.Entry(4, 2, 3, 0x123456));
        var packet = new BuildPreviewPacket("minecraft:overworld", source, destination, List.of(state), 100);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            packet.toBytes(buffer);
            var decoded = new BuildPreviewPacket(buffer);
            helper.assertTrue(decoded.states().equals(List.of(state)) && decoded.source().equals(source)
                    && decoded.destination().equals(destination) && decoded.expiresGameTime() == 100,
                    "Preview roundtrip preserves actual block state and facing");
        } finally { buffer.release(); }
        boolean rejected = false;
        try { new BuildPreviewPacket("minecraft:overworld", source, destination, List.of(), 100); }
        catch (IllegalArgumentException expected) { rejected = true; }
        helper.assertTrue(rejected, "Mismatched state count rejected");
        FriendlyByteBuf invalid = new FriendlyByteBuf(Unpooled.buffer());
        try {
            invalid.writeUtf("minecraft:overworld");
            for (var entry : List.of(source.get(0), destination.get(0))) {
                invalid.writeVarInt(1);
                invalid.writeVarInt(entry.x()).writeVarInt(entry.y()).writeVarInt(entry.z()).writeInt(entry.rgb());
            }
            invalid.writeVarInt(1).writeVarInt(Integer.MAX_VALUE);
            boolean invalidStateRejected = false;
            try { new BuildPreviewPacket(invalid); }
            catch (io.netty.handler.codec.DecoderException expected) { invalidStateRejected = true; }
            helper.assertTrue(invalidStateRejected, "Unknown network block state rejected");
        } finally { invalid.release(); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void npcProfile(GameTestHelper helper) throws IOException {
        UUID id = UUID.randomUUID();
        String name = "PortTest";
        var directory = FMLPaths.GAMEDIR.get().resolve("npcData");
        Files.createDirectories(directory);
        var file = directory.resolve(name + "-" + id + ".json");
        try {
            Files.writeString(file, "{\"uuid\":\"" + id + "\",\"name\":\"PortTest\","
                    + "\"description\":\"A farmer.\",\"personality\":\"Patient and curious.\",\"village_name\":\"Testville\"}");
            var profile = NpcDataLoader.findProfile(name, id);
            helper.assertTrue(profile != null && profile.getDescription().equals("A farmer.")
                    && profile.getPersonality().equals("Patient and curious.")
                    && profile.getVillageName().equals("Testville"), "Profile fields stay separate and complete");
        } finally { Files.deleteIfExists(file); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void exactIngredients(GameTestHelper helper) {
        var target = new ItemStack(Items.CARROT);
        var grouped = Ingredient.fromJson(com.google.gson.JsonParser.parseString("{\"tag\":\"forge:crops/carrot\"}"));
        helper.assertTrue(grouped.test(target), "Installed carrot tag accepts carrots");
        NonNullList<Ingredient> inputs = NonNullList.create();
        inputs.add(grouped);
        var recipe = new ShapelessRecipe(new ResourceLocation("farmupcraft", "test_group"), "",
                CraftingBookCategory.MISC, new ItemStack(Items.GOLDEN_CARROT), inputs);
        helper.assertTrue(!ItemUseRecipe.hasExactInput(recipe, target), "Tag-only carrot use is grouped");
        inputs.add(Ingredient.of(Items.CARROT));
        helper.assertTrue(ItemUseRecipe.hasExactInput(recipe, target),
                "A plain carrot requirement is exact even alongside a carrot tag");
        inputs.clear();
        inputs.add(Ingredient.of(Items.CARROT, Items.POTATO));
        helper.assertTrue(!ItemUseRecipe.hasExactInput(recipe, target), "Alternative items remain grouped");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void moveSafety(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "port-build-test"));
        player.setGameMode(GameType.CREATIVE);
        BlockPos origin = helper.absolutePos(new BlockPos(0, 40, 0));
        player.setPos(origin.getX() + 0.5, origin.getY(), origin.getZ() - 3);
        for (var target : List.of(Blocks.OAK_SIGN, Blocks.BARREL, Blocks.HOPPER, Blocks.FURNACE, Blocks.SHULKER_BOX)) {
            for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-1, -1, -1), origin.offset(2, 15, 3))) {
                level.setBlock(pos, pos.getY() == origin.getY() - 1
                        ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
            }
            for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-1, -1, -3), origin.offset(2, 15, 3))) {
                level.setBlock(pos, pos.getY() == origin.getY() - 1
                        ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
            }
            level.setBlock(origin, Blocks.TORCH.defaultBlockState(), 2);
            level.setBlock(origin.east(), Blocks.COBBLESTONE.defaultBlockState(), 2);
            level.setBlock(origin.south(2).below(), Blocks.AIR.defaultBlockState(), 2);
            helper.assertTrue(BuildCommand.preview(player, origin, origin.east(), true, 2) == 1,
                    "Building bottom layer can move without destination ground");
            helper.assertTrue(level.getBlockState(origin).is(Blocks.TORCH)
                            && level.getBlockState(origin.south(2)).isAir(),
                    "Preview neither moves blocks nor fabricates ground");
            level.setBlock(origin.north(2).below(), Blocks.AIR.defaultBlockState(), 2);
            helper.assertTrue(BuildCommand.preview(player, origin, origin.east(), false, 2) == 0,
                    "Wall pulls retain ground support checks");
            level.setBlock(origin.south(), Blocks.STONE.defaultBlockState(), 2);
            level.setBlock(origin, Blocks.WALL_TORCH.defaultBlockState()
                    .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH), 2);
            helper.assertTrue(BuildCommand.preview(player, origin, origin.east(), true, 2) == 0,
                    "Building bottom layer retains side support checks");
            level.setBlock(origin, Blocks.COBBLESTONE.defaultBlockState(), 2);
            level.setBlock(origin.north(), target.defaultBlockState(), 2);
            boolean allowed = BuildCommand.preview(player, origin, origin.east(), false, 1) == 1;
            helper.assertTrue(allowed == (target == Blocks.OAK_SIGN),
                    "Signs may be overwritten, empty storage must be protected: " + target);
            if (allowed) {
                helper.assertTrue(BuildCommand.confirm(player) > 0 && level.getBlockState(origin.north())
                        .is(Blocks.COBBLESTONE), "Confirmed move overwrites destination sign");
            }
        }
        BuildCommand.stopping(new net.minecraftforge.event.server.ServerStoppingEvent(level.getServer()));
        helper.succeed();
    }
}
