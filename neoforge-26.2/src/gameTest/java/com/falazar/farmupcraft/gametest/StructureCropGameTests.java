package com.falazar.farmupcraft.gametest;

import com.falazar.farmupcraft.StructureManager;
import com.falazar.farmupcraft.ChunkManager;
import com.falazar.farmupcraft.command.StructureCommand;
import com.falazar.farmupcraft.data.BiomeRulesData;
import com.falazar.farmupcraft.data.ChunkData;
import com.falazar.farmupcraft.data.GameStructureData;
import com.falazar.farmupcraft.data.PlayerData;
import com.falazar.farmupcraft.events.ModEvents;
import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import com.falazar.farmupcraft.saveddata.BiomeRulesInstance;
import com.falazar.farmupcraft.saveddata.BiomeRulesManager;
import com.falazar.farmupcraft.util.FUCTags;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.neoforged.neoforge.event.entity.EntityEvent;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@EventBusSubscriber(modid = FarmUpCraftNeoForge.MOD_ID)
public final class StructureCropGameTests {
    private StructureCropGameTests() {
    }

    @SubscribeEvent
    public static void registerFunctions(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, functions -> {
            functions.register(FarmUpCraftNeoForge.prefix("startup_world_defaults"), helper -> {
                var server = helper.getLevel().getServer();
                var rules = server.overworld().getGameRules();
                var ticks = net.minecraft.world.level.gamerules.GameRules.RANDOM_TICK_SPEED;
                var sleep = net.minecraft.world.level.gamerules.GameRules.PLAYERS_SLEEPING_PERCENTAGE;
                helper.assertValueEqual(rules.get(ticks), 1, "startup tick speed");
                helper.assertValueEqual(rules.get(sleep), 50, "startup sleep percentage");
                rules.set(ticks, 7, server);
                rules.set(sleep, 25, server);
                try {
                    com.falazar.farmupcraft.events.ForgeEvents.initializeWorldDefaults(server);
                    helper.assertValueEqual(rules.get(ticks), 7, "admin tick edit survives another startup");
                    helper.assertValueEqual(rules.get(sleep), 25, "admin sleep edit survives another startup");
                } finally {
                    rules.set(ticks, 1, server);
                    rules.set(sleep, 50, server);
                }
                helper.succeed();
            });
            functions.register(FarmUpCraftNeoForge.prefix("structure_visit_claim_rules"),
                    StructureCropGameTests::visitClaimRules);
            functions.register(FarmUpCraftNeoForge.prefix("structure_cached_note_loot"),
                    StructureCropGameTests::cachedNoteLoot);
            functions.register(FarmUpCraftNeoForge.prefix("structure_visited_barrel_loot"),
                    StructureCropGameTests::visitedBarrelLoot);
            functions.register(FarmUpCraftNeoForge.prefix("crop_hoe_farm_enforcement"),
                    StructureCropGameTests::hoeFarmEnforcement);
            functions.register(FarmUpCraftNeoForge.prefix("crop_berries_farm_enforcement"),
                    StructureCropGameTests::berriesFarmEnforcement);
            functions.register(FarmUpCraftNeoForge.prefix("crop_biome_planting_enforcement"),
                    StructureCropGameTests::biomePlantingEnforcement);
        });
    }

    private static void visitClaimRules(GameTestHelper helper) {
        try (var plot = new ResourcePlotFixture(helper)) {
            var structures = ModEvents.getGameStructureDatabase(plot.level);
            var chunks = ModEvents.getChunkDataDatabase(plot.level);
            long id = plot.pos.asLong();
            var previous = structures.getData(id);
            var data = new GameStructureData(id, "Visit claim test", plot.pos, "gametest_ruin",
                    false, false, plot.pos, plot.pos);
            structures.putData(id, data);
            var village = UUID.randomUUID();
            var chunk = ChunkPos.containing(plot.pos);
            long section = SectionPos.of(plot.pos).asLong();
            long oldSection = SectionPos.of(plot.pos.below(16)).asLong();
            try {
                chunks.putData(plot.key, new ChunkData("village", 1, village));
                data.setWasVisited(true);
                var edgePos = plot.pos.east(16);
                long edgeKey = ChunkPos.pack(edgePos);
                var edgePrevious = chunks.getData(edgeKey);
                try {
                    data.setChunkPositions(List.of(plot.key, edgeKey));
                    chunks.putData(edgeKey, new ChunkData("farm", 1, village));
                    data.setOnClaimedPlot(true);
                    StructureCommand.refreshStructureClaims(structures, chunks);
                    helper.assertTrue(!data.isOnClaimedPlot(),
                            "Purchased edge overlap must not claim a structure whose center is ordinary territory");
                    helper.assertTrue(data.wasVisited(), "Repair preserves visit history");
                    chunks.putData(plot.key, new ChunkData("farm", 1, village));
                    StructureCommand.refreshStructureClaims(structures, chunks);
                    helper.assertTrue(data.isOnClaimedPlot(), "Purchased center claims a visited structure");
                } finally {
                    if (edgePrevious == null) chunks.removeDataBatch(List.of(edgeKey));
                    else chunks.putData(edgeKey, edgePrevious);
                    data.setChunkPositions(List.of(plot.key));
                    chunks.putData(plot.key, new ChunkData("village", 1, village));
                }
                StructureCommand.updateStructuresInChunk(chunk, true, plot.level);
                helper.assertTrue(!data.isOnClaimedPlot(),
                        "Founding/expanding village territory must not claim a visited structure");

                chunks.putData(plot.key, new ChunkData("village center", 1, village));
                StructureCommand.updateStructuresInChunk(chunk, true, plot.level);
                helper.assertTrue(data.isOnClaimedPlot(), "Visited city-center structure can be claimed");

                data.setWasVisited(false);
                data.setOnClaimedPlot(false);
                chunks.putData(plot.key, new ChunkData("farm", 1, village));
                StructureCommand.updateStructuresInChunk(chunk, true, plot.level);
                helper.assertTrue(!data.isOnClaimedPlot(), "Buying a plot must not claim unvisited structures");

                plot.player.setGameMode(GameType.CREATIVE);
                ChunkManager.onPlayerEnterChunk(new EntityEvent.EnteringSection(plot.player, oldSection, section));
                helper.assertTrue(!data.wasVisited() && !data.isOnClaimedPlot(),
                        "Creative exploration must not mark visited or claimed");
                plot.player.setGameMode(GameType.SURVIVAL);
                ChunkManager.onPlayerEnterChunk(new EntityEvent.EnteringSection(plot.player, oldSection, section));
                helper.assertTrue(data.wasVisited() && data.isOnClaimedPlot(),
                        "Survival visit after buying the plot must mark both visited and claimed");

                chunks.putData(plot.key, new ChunkData("village", 1, village));
                data.setWasVisited(false);
                data.setOnClaimedPlot(false);
                ChunkManager.onPlayerEnterChunk(new EntityEvent.EnteringSection(plot.player, oldSection, section));
                helper.assertTrue(data.wasVisited() && !data.isOnClaimedPlot(),
                        "Visiting ordinary village territory must mark visited only");
                chunks.putData(plot.key, new ChunkData("house", 1, village));
                StructureCommand.updateStructuresInChunk(chunk, true, plot.level);
                helper.assertTrue(data.isOnClaimedPlot(), "Buying after the visit must claim the structure");

                chunks.putData(plot.key, new ChunkData("village", 1, village));
                StructureCommand.listStructuresFromDatabase(plot.player.createCommandSourceStack(), 20, "all");
                helper.assertTrue(!data.isOnClaimedPlot() && data.wasVisited(),
                        "Listing must persistently repair stale claims without clearing visits");
                helper.assertTrue(!structures.getData(id).isOnClaimedPlot(), "Repaired database row");
            } finally {
                if (previous == null) {
                    structures.removeDataBatch(List.of(id));
                } else {
                    structures.putData(id, previous);
                }
            }
        }
        helper.succeed();
    }

    private static void cachedNoteLoot(GameTestHelper helper) {
        try (var fixture = new InteractionFixture(helper);
                var cache = new ThreatNoteCache();
                var structure = new StructureRow(fixture, false)) {
            var plot = fixture.plot;
            plot.level.setBlock(plot.pos, Blocks.CHEST.defaultBlockState(), 2);
            Container chest = (Container) plot.level.getBlockEntity(plot.pos);
            chest.setItem(0, new ItemStack(Items.ROTTEN_FLESH, 2));
            chest.setItem(1, new ItemStack(Items.DIAMOND, 17));
            chest.setItem(26, new ItemStack(Items.WHEAT, 9));

            structure.scan();
            helper.assertValueEqual(structure.data.getSpecialChestPos(), plot.pos, "selected real chest");
            helper.assertValueEqual(structure.data.getTotalChestCount(), 1, "scanned chest count");
            helper.assertTrue(!structure.data.wasVisited() && !structure.data.isSpecialChestOpened(),
                    "Preparing loot must not mark the structure visited or its trap opened");
            ItemStack note = chest.getItem(0);
            helper.assertTrue(note.is(Items.WRITTEN_BOOK) && note.getCount() == 1,
                    "The production scanner must replace slot zero with one written threat note");
            var content = note.get(DataComponents.WRITTEN_BOOK_CONTENT);
            helper.assertTrue(content != null, "The note must carry real written-book components");
            helper.assertValueEqual(content.title().raw(), "A Strange Note", "cached note title");
            helper.assertValueEqual(content.author(), "???", "cached note author");
            helper.assertValueEqual(content.pages().size(), 1, "cached note page count");
            helper.assertValueEqual(content.pages().getFirst().raw().getString(), ThreatNoteCache.NOTE,
                    "cached text written into the chest");
            assertLoot(helper, chest);
            helper.assertValueEqual(cache.remaining(), 1, "one cached note consumed");

            structure.scan();
            helper.assertTrue(chest.getItem(0) == note,
                    "A repeated production scan must not replace an already prepared book");
            assertLoot(helper, chest);
            helper.assertValueEqual(cache.remaining(), 1, "rescan must not consume a second cached note");
            helper.assertTrue(plot.level.getBlockEntity(plot.pos) == chest,
                    "Scanning must retain the original chest block entity");
            helper.assertValueEqual(plot.newDrops().size(), 0, "preparing loot must not spill items");
        }
        helper.succeed();
    }

    private static void visitedBarrelLoot(GameTestHelper helper) {
        try (var fixture = new InteractionFixture(helper);
                var structure = new StructureRow(fixture, true)) {
            var plot = fixture.plot;
            plot.level.setBlock(plot.pos, Blocks.BARREL.defaultBlockState(), 2);
            Container barrel = (Container) plot.level.getBlockEntity(plot.pos);
            barrel.setItem(1, new ItemStack(Items.DIAMOND, 17));
            barrel.setItem(26, new ItemStack(Items.WHEAT, 9));
            structure.scan();
            helper.assertValueEqual(structure.data.getTotalChestCount(), 1, "barrels counted as structure loot");
            helper.assertTrue(!structure.data.hasSpecialChest() && barrel.getItem(0).isEmpty(),
                    "Revisiting a looted structure must not insert a special note");
            assertLoot(helper, barrel);
            plot.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_AXE));

            helper.assertTrue(plot.player.gameMode.destroyBlock(plot.pos),
                    "A wilderness player must be able to break the visited structure's real barrel");
            helper.assertTrue(plot.level.getBlockState(plot.pos).isAir(), "The barrel must actually be removed");
            helper.assertValueEqual(plot.dropCount(Items.BARREL), 1, "barrel block loot");
            helper.assertValueEqual(plot.dropCount(Items.DIAMOND), 17, "all stored diamond loot");
            helper.assertValueEqual(plot.dropCount(Items.WHEAT), 9, "all stored crop loot");
            helper.assertValueEqual(plot.dropCount(Items.WRITTEN_BOOK), 0, "no synthetic note on visited loot");
            helper.assertTrue(structure.data.wasVisited() && !structure.data.hasSpecialChest()
                            && structure.data.getOpenedChestPositions().isEmpty(),
                    "The real pre-break trap handler must not arm or open a trap in an ordinary visited barrel");
        }
        helper.succeed();
    }

    private static void hoeFarmEnforcement(GameTestHelper helper) {
        try (var fixture = new InteractionFixture(helper)) {
            var plot = fixture.plot;
            var world = ModEvents.getWorldData(plot.level);
            world.setUseVillageFarms(true);
            plot.level.setBlock(plot.pos, Blocks.DIRT.defaultBlockState(), 2);
            plot.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_HOE));
            helper.assertTrue(fixture.use() == InteractionResult.FAIL,
                    "The real hoe interaction must fail outside a farm");
            helper.assertTrue(plot.level.getBlockState(plot.pos).is(Blocks.DIRT),
                    "A denied hoe action must leave dirt untilled");
            helper.assertValueEqual(plot.player.getMainHandItem().getDamageValue(), 0,
                    "denied hoe durability");

            fixture.farm();
            fixture.use();
            helper.assertTrue(plot.level.getBlockState(plot.pos).is(Blocks.FARMLAND),
                    "Buying a farm must allow the same hoe to till the real dirt");
            helper.assertValueEqual(plot.player.getMainHandItem().getDamageValue(), 1,
                    "successful hoe action durability");
            helper.assertTrue(plot.level.getBlockState(plot.pos.above()).isAir(),
                    "Hoeing must not place a crop or other block above the soil");
            helper.assertValueEqual(plot.newDrops().size(), 0, "hoe interaction item drops");
        }
        helper.succeed();
    }

    private static void berriesFarmEnforcement(GameTestHelper helper) {
        try (var fixture = new InteractionFixture(helper)) {
            var plot = fixture.plot;
            ModEvents.getWorldData(plot.level).setUseVillageFarms(true);
            plot.level.setBlock(plot.pos, Blocks.GRASS_BLOCK.defaultBlockState(), 2);
            plot.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SWEET_BERRIES, 3));
            helper.assertTrue(fixture.use() == InteractionResult.FAIL,
                    "The real berry planting interaction must fail outside a farm");
            helper.assertTrue(plot.level.getBlockState(plot.pos.above()).isAir(),
                    "Denied berry planting must not create a bush");
            helper.assertValueEqual(plot.player.getMainHandItem().getCount(), 3, "denied berry stack");

            fixture.farm();
            fixture.use();
            helper.assertValueEqual(plot.level.getBlockState(plot.pos.above()),
                    Blocks.SWEET_BERRY_BUSH.defaultBlockState(), "newly planted berry bush");
            helper.assertValueEqual(plot.player.getMainHandItem().getCount(), 2, "one berry consumed on farm");
            helper.assertTrue(plot.level.getBlockState(plot.pos).is(Blocks.GRASS_BLOCK),
                    "Planting berries must preserve their supporting grass block");
            helper.assertValueEqual(plot.newDrops().size(), 0, "berry interaction item drops");
        }
        helper.succeed();
    }

    private static void biomePlantingEnforcement(GameTestHelper helper) {
        try (var fixture = new InteractionFixture(helper)) {
            var plot = fixture.plot;
            var manager = BiomeRulesManager.get(plot.level);
            var biome = plot.level.getBiome(plot.pos);
            var previous = manager.getData().get(biome);
            boolean dirty = manager.isDirty();
            boolean hadReverseIndex = manager.getItems().contains(Items.WHEAT_SEEDS);
            List<Holder<Biome>> previousBiomes = hadReverseIndex
                    ? new ArrayList<>(manager.getBiomesForItem(Items.WHEAT_SEEDS)) : List.of();
            try {
                var world = ModEvents.getWorldData(plot.level);
                world.setUseVillageFarms(true);
                world.setUseBiomeCropRules(true);
                fixture.farm();
                plot.level.setBlock(plot.pos, Blocks.FARMLAND.defaultBlockState(), 2);
                plot.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WHEAT_SEEDS, 3));
                helper.assertTrue(plot.level.getBlockState(plot.pos).is(FUCTags.FARMLAND)
                                && plot.player.getMainHandItem().is(FUCTags.VANILLA_CROPS),
                        "The running datapack must classify the real soil and seeds used by the handler");
                manager.setBiomeRules(biome, new BiomeRulesInstance(BiomeRulesData.EMPTY, List.of(Items.CARROT)));
                helper.assertTrue(fixture.use() == InteractionResult.FAIL,
                        "Buying a level-one farm must not bypass a biome's disallowed crop");
                helper.assertTrue(plot.level.getBlockState(plot.pos.above()).isAir(),
                        "A biome-denied seed interaction must leave the planting space empty");
                helper.assertValueEqual(plot.player.getMainHandItem().getCount(), 3, "biome-denied seed stack");
                helper.assertTrue(plot.level.getBlockState(plot.pos).is(Blocks.FARMLAND),
                        "Biome denial must not alter the farmland");

                manager.setBiomeRules(biome,
                        new BiomeRulesInstance(BiomeRulesData.EMPTY, List.of(Items.WHEAT_SEEDS)));
                fixture.use();
                helper.assertValueEqual(plot.level.getBlockState(plot.pos.above()),
                        Blocks.WHEAT.defaultBlockState(), "allowed biome must produce a real age-zero wheat crop");
                helper.assertValueEqual(plot.player.getMainHandItem().getCount(), 2, "allowed biome seed consumption");

                plot.level.setBlock(plot.pos.above(), Blocks.AIR.defaultBlockState(), 2);
                manager.setBiomeRules(biome, new BiomeRulesInstance(BiomeRulesData.EMPTY, List.of(Items.CARROT)));
                world.setUseBiomeCropRules(false);
                fixture.use();
                helper.assertValueEqual(plot.level.getBlockState(plot.pos.above()),
                        Blocks.WHEAT.defaultBlockState(), "disabled biome enforcement must permit actual planting");
                helper.assertValueEqual(plot.player.getMainHandItem().getCount(), 1, "disabled-rule seed consumption");
                helper.assertValueEqual(plot.newDrops().size(), 0, "biome planting interaction drops");
            } finally {
                if (previous == null) {
                    manager.removeBiomeRule(biome);
                } else {
                    manager.setBiomeRules(biome, previous);
                }
                if (hadReverseIndex) {
                    var biomes = manager.getBiomesForItem(Items.WHEAT_SEEDS);
                    biomes.clear();
                    biomes.addAll(previousBiomes);
                } else {
                    manager.getItems().remove(Items.WHEAT_SEEDS);
                }
                manager.setDirty(dirty);
            }
        }
        helper.succeed();
    }

    private static void assertLoot(GameTestHelper helper, Container container) {
        helper.assertTrue(container.getItem(1).is(Items.DIAMOND) && container.getItem(1).getCount() == 17,
                "Structure preparation must preserve the diamond stack in slot one");
        helper.assertTrue(container.getItem(26).is(Items.WHEAT) && container.getItem(26).getCount() == 9,
                "Structure preparation must preserve the crop stack in the final slot");
    }

    private static final class InteractionFixture implements AutoCloseable {
        final ResourcePlotFixture plot;
        private final BlockState above;
        private final boolean useFarms;
        private final boolean useBiomes;

        InteractionFixture(GameTestHelper helper) {
            plot = new ResourcePlotFixture(helper);
            above = plot.level.getBlockState(plot.pos.above());
            var world = ModEvents.getWorldData(plot.level);
            useFarms = world.isUseVillageFarms();
            useBiomes = world.isUseBiomeCropRules();
            // These cells are from the empty test template; never replace an existing container fixture.
            if (plot.level.getBlockEntity(plot.pos) != null || plot.level.getBlockEntity(plot.pos.above()) != null) {
                plot.close();
                throw new IllegalStateException("Interaction test requires cells without block entities");
            }
            plot.level.setBlock(plot.pos.above(), Blocks.AIR.defaultBlockState(), 2);
        }

        void farm() {
            var village = plot.player.getUUID();
            var data = new ChunkData("farm", plot.player.getId(), village, plot.player.getUUID());
            data.setBoughtY(plot.pos.getY());
            data.setPlotLevel(1);
            ModEvents.getPlayerDatabase(plot.level).putData(plot.player.getUUID(),
                    new PlayerData(plot.player.getId(), plot.player.getUUID(), village, 0));
            ModEvents.getChunkDataDatabase(plot.level).putData(plot.key, data);
        }

        InteractionResult use() {
            return plot.player.gameMode.useItemOn(plot.player, plot.level, plot.player.getMainHandItem(),
                    InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(plot.pos), Direction.UP, plot.pos, false));
        }

        @Override
        public void close() {
            try {
                // Clear test loot before removing its container, avoiding cleanup-generated item entities.
                if (plot.level.getBlockEntity(plot.pos) instanceof Container container) {
                    container.clearContent();
                }
                plot.level.setBlock(plot.pos.above(), above, 2);
                var world = ModEvents.getWorldData(plot.level);
                world.setUseVillageFarms(useFarms);
                world.setUseBiomeCropRules(useBiomes);
            } finally {
                plot.close();
            }
        }
    }

    private static final class StructureRow implements AutoCloseable {
        private final InteractionFixture fixture;
        private final long key;
        private final GameStructureData previous;
        final GameStructureData data;

        StructureRow(InteractionFixture fixture, boolean visited) {
            this.fixture = fixture;
            BlockPos pos = fixture.plot.pos;
            key = pos.asLong();
            var db = ModEvents.getGameStructureDatabase(fixture.plot.level);
            previous = db.getData(key);
            data = new GameStructureData(key, "GameTest ruin", pos, "gametest_ruin",
                    false, visited, pos, pos);
            db.putData(key, data);
        }

        void scan() {
            var level = fixture.plot.level;
            StructureManager.scanAndSetupStructureChestsByIds(level.getServer().createCommandSourceStack(),
                    List.of(key), level);
        }

        @Override
        public void close() {
            var db = ModEvents.getGameStructureDatabase(fixture.plot.level);
            if (previous == null) {
                db.removeDataBatch(List.of(key));
            } else {
                db.putData(key, previous);
            }
        }
    }

    private static final class ThreatNoteCache implements AutoCloseable {
        static final String NOTE = "The abandoned granary still remembers its harvest.";
        private final Path directory = FMLPaths.GAMEDIR.get().resolve("npcData");
        private final Path file = directory.resolve("threat_notes_cache.json");
        private final boolean hadDirectory = Files.exists(directory);
        private final byte[] previous;

        ThreatNoteCache() {
            try {
                previous = Files.exists(file) ? Files.readAllBytes(file) : null;
                Files.createDirectories(directory);
                // The synchronous cached-note path must never contact an external AI service.
                Files.writeString(file, "{\"notes\":[\"" + NOTE + "\",\"Keep this second note untouched.\"]}");
            } catch (IOException error) {
                throw new UncheckedIOException(error);
            }
        }

        int remaining() {
            try {
                return JsonParser.parseString(Files.readString(file)).getAsJsonObject()
                        .getAsJsonArray("notes").size();
            } catch (IOException error) {
                throw new UncheckedIOException(error);
            }
        }

        @Override
        public void close() {
            try {
                if (previous == null) {
                    Files.deleteIfExists(file);
                } else {
                    Files.write(file, previous);
                }
                if (!hadDirectory) {
                    Files.delete(directory);
                }
            } catch (IOException error) {
                throw new UncheckedIOException(error);
            }
        }
    }
}
