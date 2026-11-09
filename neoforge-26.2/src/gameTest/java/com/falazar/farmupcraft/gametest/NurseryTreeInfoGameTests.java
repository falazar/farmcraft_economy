package com.falazar.farmupcraft.gametest;

import com.falazar.farmupcraft.TreeManager;
import com.falazar.farmupcraft.command.PlotCommand;
import com.falazar.farmupcraft.data.ChunkData;
import com.falazar.farmupcraft.data.VillageData;
import com.falazar.farmupcraft.events.ModEvents;
import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import com.falazar.farmupcraft.util.VillageView;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = FarmUpCraftNeoForge.MOD_ID)
public final class NurseryTreeInfoGameTests {
    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, functions -> functions.register(
                FarmUpCraftNeoForge.prefix("nursery_tree_info"), NurseryTreeInfoGameTests::verify));
    }

    private static void verify(GameTestHelper helper) {
        try (var f = new ResourcePlotFixture(helper)) {
            var previous = new HashMap<BlockPos, BlockState>();
            var messages = new ArrayList<String>();
            var player = new FakePlayer(f.level, new GameProfile(UUID.randomUUID(), "nursery_info")) {
                @Override
                public void sendSystemMessage(Component message) { messages.add(message.getString()); }
                @Override
                public void sendSystemMessage(Component message, boolean overlay) { messages.add(message.getString()); }
            };
            UUID villageId = UUID.randomUUID();
            var villages = ModEvents.getVillageDatabase(f.level);
            villages.putData(villageId, new VillageData(villageId, "Nursery test", f.player.chunkPosition(), 8,
                    List.of(f.player.chunkPosition()), true, 250, "", "", "",
                    player.getName().getString(), List.of(player.getUUID())));
            var nursery = new ChunkData("nursery", player.getId(), villageId);
            nursery.setPlotLevel(2);
            ModEvents.getChunkDataDatabase(f.level).putData(f.key, nursery);
            try {
                var apple = BuiltInRegistries.BLOCK.getValue(Identifier.parse("pamhc2trees:pamapple"));
                var almond = BuiltInRegistries.BLOCK.getValue(Identifier.parse("pamhc2trees:pamalmond"));
                helper.assertTrue(apple != Blocks.AIR && almond != Blocks.AIR, "Actual fruit and nut blocks loaded");
                var origin = new BlockPos(f.player.chunkPosition().getMinBlockX() + 3, f.pos.getY() + 40,
                        f.player.chunkPosition().getMinBlockZ() + 3);
                var roots = List.of(origin, origin.offset(6, 0, 0), origin.offset(0, 0, 6), origin.offset(6, 0, 6));
                for (BlockPos root : roots) {
                    for (int y = 0; y < 5; y++) put(f, previous, root.above(y), Blocks.OAK_LOG.defaultBlockState());
                }
                var appleFruit = origin.offset(1, 3, 0);
                put(f, previous, appleFruit, apple.defaultBlockState());
                put(f, previous, origin.offset(-1, 4, 1), apple.defaultBlockState());
                put(f, previous, roots.get(1).offset(1, 4, 0), almond.defaultBlockState());
                put(f, previous, roots.get(2).offset(1, 2, 0), almond.defaultBlockState());
                put(f, previous, origin.offset(3, 0, 3), apple.defaultBlockState());
                for (int x = 1; x < 6; x++) {
                    put(f, previous, origin.offset(x, 5, 0), Blocks.OAK_LEAVES.defaultBlockState());
                }
                var counts = TreeManager.countFruitTrees(f.level, f.pos);
                helper.assertValueEqual(counts.total(), 3, "Fruit blocks count trees, not fruit or timber trunks");
                helper.assertValueEqual(counts.byType(),
                        Map.of(apple.getName().getString(), 1, almond.getName().getString(), 2),
                        "Unripe fruit and nuts identify separate trees; canopy and loose fruit do not merge/add trees");
                player.setPos(f.pos.getX() + 0.5, f.pos.getY(), f.pos.getZ() + 0.5);
                var source = player.createCommandSourceStack();
                var view = PlotCommand.plotSnapshot(source, player).details();
                helper.assertValueEqual(value(view, "Fruit / nut trees"), "3", "GUI shows total");
                helper.assertTrue(value(view, "Tree breakdown").contains(almond.getName().getString() + ": 2"),
                        "GUI shows per-type count");
                f.level.getServer().getCommands().getDispatcher().execute("plot info", source);
                helper.assertTrue(String.join("\n", messages).contains(value(view, "Tree breakdown")),
                        "Chat and GUI use identical nursery breakdown");
                player.setPos(player.getX(), origin.getY() + 20, player.getZ());
                helper.assertValueEqual(value(PlotCommand.plotSnapshot(player.createCommandSourceStack(), player).details(),
                        "Fruit / nut trees"), "3", "Viewer height does not affect counts");
                put(f, previous, appleFruit, Blocks.AIR.defaultBlockState());
                helper.assertValueEqual(TreeManager.countFruitTrees(f.level, f.pos).total(), 3,
                        "One remaining diagonal fruit still identifies tree");
                put(f, previous, origin.offset(-1, 4, 1), Blocks.AIR.defaultBlockState());
                helper.assertValueEqual(TreeManager.countFruitTrees(f.level, f.pos).total(), 2,
                        "Removing all attached fruit removes tree from census");
                put(f, previous, roots.get(3).offset(1, 2, 0), Blocks.COCOA.defaultBlockState());
                helper.assertValueEqual(TreeManager.countFruitTrees(f.level, f.pos).total(), 3,
                        "Vanilla cocoa also identifies a tree without a HarvestCraft dependency");
            } catch (com.mojang.brigadier.exceptions.CommandSyntaxException error) {
                throw new IllegalStateException("Nursery plot command failed", error);
            } finally {
                previous.forEach((pos, state) -> f.level.setBlock(pos, state, 2));
                villages.removeDataBatch(List.of(villageId));
                player.discard();
            }
        }
        helper.succeed();
    }

    private static void put(ResourcePlotFixture f, Map<BlockPos, BlockState> previous, BlockPos pos, BlockState state) {
        previous.putIfAbsent(pos, f.level.getBlockState(pos));
        f.level.setBlock(pos, state, 2);
    }

    private static String value(VillageView view, String label) {
        return view.entries().stream().filter(e -> e.label().equals(label)).findFirst().orElseThrow().value();
    }
}
