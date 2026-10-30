package com.falazar.farmupcraft.gametest;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.falazar.farmupcraft.data.ChunkData;
import com.falazar.farmupcraft.data.PlayerData;
import com.falazar.farmupcraft.command.MarketCommand;
import com.falazar.farmupcraft.events.CauldronHopperHandler;
import com.falazar.farmupcraft.events.ModEvents;
import com.falazar.farmupcraft.database.fileformats.FileFormat;
import com.falazar.farmupcraft.database.serializers.CodecDataSerializer;
import com.falazar.farmupcraft.database.serializers.UUIDDataSerializer;
import net.minecraft.nbt.CompoundTag;
import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.io.IOException;
import java.util.Map;

@EventBusSubscriber(modid = FarmUpCraftNeoForge.MOD_ID)
public final class GameplayGameTests {
    private GameplayGameTests() {
    }

    @SubscribeEvent
    public static void registerFunctions(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, functions -> {
            functions.register(id("refinery_transfer"), helper -> refinery(helper, "refinery", true, false));
            functions.register(id("refinery_requires_plot"), helper -> refinery(helper, "kitchen", true, false));
            functions.register(id("refinery_requires_bucket"), helper -> refinery(helper, "refinery", false, false));
            functions.register(id("refinery_full_output"), helper -> refinery(helper, "refinery", true, true));
            functions.register(id("villager_fall_immunity"), GameplayGameTests::villagerFallImmunity);
            functions.register(id("database_dimension_routing"), GameplayGameTests::databaseDimensionRouting);
            functions.register(id("command_registration_permissions"), GameplayGameTests::commandRegistrationPermissions);
            functions.register(id("economy_save_exports"), GameplayGameTests::economySaveExports);
            functions.register(id("market_startup"), GameplayGameTests::marketStartup);
        });
    }

    private static void marketStartup(GameTestHelper helper) {
        var source = helper.getLevel().getServer().createCommandSourceStack();
        var database = ModEvents.getGoodsDataDatabase(helper.getLevel());
        var original = new java.util.ArrayList<>(database.getValues());
        try {
            database.clearDataBase(true);
            MarketCommand.initializeMarket(source);
            for (String type : List.of("food", "wood", "stone", "general")) {
                helper.assertTrue(database.getValues().stream()
                                .anyMatch(good -> good.getMarketType().equals(type) && good.isActive()),
                        "Empty market must import and activate " + type);
            }
            int rows = database.getSize();
            var good = database.getValues().stream()
                    .filter(item -> item.isActive() && item.getMarketType().equals("food"))
                    .findFirst().orElseThrow();
            good.setCost(37);
            good.setAmountSold(123);
            good.setDateAddedToMarket("2000-01-01");
            database.putData(good.getItemId(), good);
            var active = database.getValues().stream().filter(item -> item.isActive())
                    .map(item -> item.getItemId()).collect(java.util.stream.Collectors.toSet());
            MarketCommand.initializeMarket(source);
            helper.assertValueEqual(database.getSize(), rows, "restart row count");
            helper.assertValueEqual(good.getCost(), 37, "restart price");
            helper.assertValueEqual(good.getAmountSold(), 123, "restart sales count");
            helper.assertValueEqual(good.getDateAddedToMarket(), "2000-01-01", "restart activation date");
            helper.assertValueEqual(database.getValues().stream().filter(item -> item.isActive())
                    .map(item -> item.getItemId()).collect(java.util.stream.Collectors.toSet()), active,
                    "restart must not rotate active goods");

            var missing = database.getValues().stream().filter(item -> item.getMarketType().equals("stone"))
                    .map(item -> item.getItemId()).toList();
            database.removeDataBatch(missing);
            MarketCommand.initializeMarket(source);
            helper.assertTrue(database.getValues().stream()
                    .anyMatch(item -> item.getMarketType().equals("stone") && item.isActive()),
                    "Partially initialized market must repair its missing category");
            helper.assertTrue(MarketCommand.importAllMarketItems(source) == 1, "Manual import must succeed");
            helper.assertTrue(database.getData(good.getItemId()) == good,
                    "Import must not replace an existing goods record");
            helper.assertValueEqual(good.getCost(), 37, "manual import price");
            helper.assertValueEqual(good.getAmountSold(), 123, "manual import sales count");

            database.clearDataBase(true);
            var inactive = new com.falazar.farmupcraft.data.GoodsData(
                    "minecraft:stone", 41, 234, "common", false, "stone", "");
            database.putData(inactive.getItemId(), inactive);
            MarketCommand.initializeMarket(source);
            helper.assertTrue(inactive.isActive(), "Existing inactive category must be seeded");
            helper.assertValueEqual(inactive.getCost(), 41, "seeding must preserve existing prices");
            helper.assertValueEqual(inactive.getAmountSold(), 234, "seeding must preserve sales counts");
        } finally {
            database.clearDataBase(true);
            original.forEach(good -> database.putData(good.getItemId(), good));
        }
        helper.succeed();
    }

    private static void economySaveExports(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var players = ModEvents.getPlayerDatabase(helper.getLevel());
        UUID playerId = UUID.randomUUID();
        players.putData(playerId, new PlayerData(-1, playerId, UUID.randomUUID(), 137));
        try {
            int result = server.getCommands().getDispatcher().execute("save-all flush", server.createCommandSourceStack());
            helper.assertTrue(result > 0, "The real save-all command must succeed");
            var directory = server.getServerDirectory().resolve("farmupcraft-data");
            for (var entry : Map.of("players", "player_database", "villages", "village_database",
                    "chunks", "chunk_data_database").entrySet()) {
                var payload = FileFormat.JSON.getImportStrategy().importData(directory.resolve(entry.getKey() + ".json"));
                helper.assertValueEqual(payload.getString("database_name").orElseThrow(),
                        "farmupcraft:" + entry.getValue(), "exported database identity");
                helper.assertTrue(payload.getList("map_entry").isPresent(), "Exported rows must be present");
                if (entry.getKey().equals("players")) {
                    boolean found = false;
                    for (var item : payload.getList("map_entry").orElseThrow()) {
                        if (!(item instanceof CompoundTag row) || !(row.get("key") instanceof CompoundTag key)
                                || !(row.get("value") instanceof CompoundTag value)) {
                            throw new IllegalStateException("Invalid exported player row");
                        }
                        if (playerId.equals(new UUIDDataSerializer().deserialize(key, helper.getLevel().registryAccess()))) {
                            var restored = new CodecDataSerializer<>(PlayerData.CODEC)
                                    .deserialize(value, helper.getLevel().registryAccess());
                            helper.assertValueEqual(restored.getCoins(), 137, "exported player balance");
                            found = true;
                        }
                    }
                    helper.assertTrue(found, "The seeded player must survive real JSON export and decoding");
                }
            }
        } catch (CommandSyntaxException | IOException error) {
            throw new IllegalStateException("Real economy save/export failed", error);
        } finally {
            players.removeDataBatch(List.of(playerId));
        }
        helper.succeed();
    }

    private static void commandRegistrationPermissions(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var dispatcher = server.getCommands().getDispatcher();
        for (String command : List.of("show", "managers", "database", "player", "plot", "village",
                "market", "farmcraft", "tester", "npc", "structures", "zone", "ai", "scan",
                "farmupcraft-legacy")) {
            helper.assertTrue(dispatcher.getRoot().getChild(command) != null, "Missing /" + command);
        }
        var database = dispatcher.getRoot().getChild("database");
        var source = server.createCommandSourceStack();
        helper.assertTrue(database.canUse(source.withPermission(PermissionSet.ALL_PERMISSIONS)),
                "An administrator must be able to use database commands");
        helper.assertTrue(!database.canUse(source.withPermission(PermissionSet.NO_PERMISSIONS)),
                "An unprivileged source must not be able to use database administration");
        var legacy = dispatcher.getRoot().getChild("farmupcraft-legacy");
        helper.assertTrue(legacy.canUse(source.withPermission(PermissionSet.ALL_PERMISSIONS)),
                "An administrator must be able to inspect legacy data");
        helper.assertTrue(!legacy.canUse(source.withPermission(PermissionSet.NO_PERMISSIONS)),
                "An unprivileged source must not be able to inspect legacy data");
        helper.succeed();
    }

    private static void villagerFallImmunity(GameTestHelper helper) {
        var villager = helper.spawn(EntityTypes.VILLAGER, new BlockPos(1, 1, 1));
        float health = villager.getHealth();
        villager.hurtServer(helper.getLevel(), helper.getLevel().damageSources().fall(), 5);
        helper.assertValueEqual(villager.getHealth(), health, "villager health after fall damage");
        helper.succeed();
    }

    private static void databaseDimensionRouting(GameTestHelper helper) {
        var level = helper.getLevel();
        var nether = level.getServer().getLevel(Level.NETHER);
        helper.assertTrue(nether != null, "A real Nether level must exist");
        helper.assertTrue(ModEvents.getPlayerDatabase(level) == ModEvents.getPlayerDatabase(nether),
                "Player data in different dimensions must share overworld storage");
        helper.assertTrue(ModEvents.getChunkDataDatabase(level) == ModEvents.getChunkDataDatabase(nether),
                "Plot data must retain legacy overworld routing");
        helper.succeed();
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(FarmUpCraftNeoForge.MOD_ID, path);
    }

    private static void refinery(GameTestHelper helper, String plotType, boolean hasBucket, boolean fullOutput) {
        var level = helper.getLevel();
        BlockPos cauldron = helper.absolutePos(new BlockPos(1, 1, 1));
        long key = ChunkPos.pack(cauldron);
        var database = ModEvents.getChunkDataDatabase(level);
        ChunkData previous = database.getData(key);
        var plot = new ChunkData(plotType, -1, new UUID(0, 0));
        plot.setBoughtY(cauldron.getY());
        database.putData(key, plot);
        try {
            level.setBlock(cauldron.east(), Blocks.HOPPER.defaultBlockState()
                    .setValue(HopperBlock.FACING, Direction.WEST), 2);
            level.setBlock(cauldron.below(), Blocks.HOPPER.defaultBlockState(), 2);
            var input = (HopperBlockEntity) level.getBlockEntity(cauldron.east());
            var output = (HopperBlockEntity) level.getBlockEntity(cauldron.below());
            helper.assertTrue(input != null && output != null, "Both real hopper block entities must exist");
            if (hasBucket) {
                input.setItem(0, new ItemStack(Items.BUCKET, 2));
            }
            if (fullOutput) {
                for (int slot = 0; slot < output.getContainerSize(); slot++) {
                    output.setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
                }
            }
            level.setBlock(cauldron, Blocks.LAVA_CAULDRON.defaultBlockState(), 2);
            var notification = new BlockEvent.NeighborNotifyEvent(level, cauldron,
                    level.getBlockState(cauldron), EnumSet.of(Direction.DOWN), false);
            CauldronHopperHandler.onNeighborNotify(notification);
            boolean shouldTransfer = plotType.equals("refinery") && hasBucket;
            helper.assertTrue(level.getBlockState(cauldron).is(shouldTransfer ? Blocks.CAULDRON : Blocks.LAVA_CAULDRON),
                    "Only an eligible refinery consumes lava");
            helper.assertValueEqual(count(input, Items.BUCKET), hasBucket ? (shouldTransfer ? 1 : 2) : 0,
                    "remaining empty buckets");
            helper.assertValueEqual(count(output, Items.LAVA_BUCKET), shouldTransfer && !fullOutput ? 1 : 0,
                    "lava buckets in output");
            if (shouldTransfer && fullOutput) {
                var drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(cauldron.below()).inflate(1),
                        entity -> entity.getItem().is(Items.LAVA_BUCKET));
                helper.assertValueEqual(drops.stream().mapToInt(entity -> entity.getItem().getCount()).sum(), 1,
                        "dropped lava buckets");
            }
            // Replaying the original notification must not spend another bucket.
            CauldronHopperHandler.onNeighborNotify(notification);
            helper.assertValueEqual(count(input, Items.BUCKET), hasBucket ? (shouldTransfer ? 1 : 2) : 0,
                    "buckets after repeated notification");
            helper.succeed();
        } finally {
            if (previous == null) {
                database.removeDataBatch(List.of(key));
            } else {
                database.putData(key, previous);
            }
        }
    }

    private static int count(HopperBlockEntity hopper, Item item) {
        int count = 0;
        for (int slot = 0; slot < hopper.getContainerSize(); slot++) {
            ItemStack stack = hopper.getItem(slot);
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }
}
