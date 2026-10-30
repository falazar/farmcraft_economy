package com.falazar.farmupcraft.gametest;

import com.falazar.farmupcraft.command.BuildCommand;
import com.falazar.farmupcraft.data.ChunkData;
import com.falazar.farmupcraft.data.PlayerData;
import com.falazar.farmupcraft.events.ModEvents;
import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

@EventBusSubscriber(modid = FarmUpCraftNeoForge.MOD_ID)
public final class BuildMoveGameTests {
    private BuildMoveGameTests() {}

    @SubscribeEvent
    public static void registerFunctions(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, functions -> functions.register(
                FarmUpCraftNeoForge.prefix("build_moves"), BuildMoveGameTests::moves));
    }

    private static void moves(GameTestHelper helper) {
        try (var fixture = new ResourcePlotFixture(helper)) {
            var level = fixture.level;
            var messages = new ArrayList<String>();
            var components = new ArrayList<Component>();
            var particles = new ArrayList<ClientboundLevelParticlesPacket>();
            var player = new FakePlayer(level, fixture.player.getGameProfile()) {
                @Override
                public void sendSystemMessage(Component message) {
                    messages.add(message.getString());
                    components.add(message);
                }
            };
            player.connection = new ServerGamePacketListenerImpl(level.getServer(), new Connection(PacketFlow.SERVERBOUND),
                    player, CommonListenerCookie.createInitial(player.getGameProfile(), false)) {
                @Override
                public void send(Packet<?> packet) {
                    if (packet instanceof ClientboundLevelParticlesPacket particle) particles.add(particle);
                }
            };
            player.setGameMode(GameType.SURVIVAL);
            var wallets = ModEvents.getPlayerDatabase(level);
            var wallet = new PlayerData(player.getId(), player.getUUID(), null, 100000);
            wallets.putData(player.getUUID(), wallet);
            BlockPos p = fixture.pos;
            Map<BlockPos, BlockState> original = new LinkedHashMap<>();
            for (BlockPos pos : BlockPos.betweenClosed(p.offset(-1, -1, -11), p.offset(4, 15, 23))) {
                level.getChunkAt(pos);
                original.put(pos.immutable(), level.getBlockState(pos));
            }
            var plots = ModEvents.getChunkDataDatabase(level);
            Map<Long, ChunkData> originalPlots = new LinkedHashMap<>();
            for (BlockPos pos : original.keySet()) {
                long key = ChunkPos.pack(pos);
                if (!originalPlots.containsKey(key)) originalPlots.put(key, plots.getData(key));
            }
            plots.removeDataBatch(new ArrayList<>(originalPlots.keySet()));
            Runnable clear = () -> {
                original.keySet().forEach(pos -> {
                    level.removeBlockEntity(pos);
                    level.setBlock(pos, pos.getY() == p.getY() - 1
                            ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
                });
                player.setPos(p.getX() + 0.5, p.getY(), p.getZ() - 3);
            };
            try {
                clear.run();
                player.setPos(p.getX() - 3, p.getY(), p.getZ() + 0.5);
                level.setBlock(p, Blocks.COBBLESTONE.defaultBlockState(), 2);
                level.setBlock(p.south(19), Blocks.OAK_PLANKS.defaultBlockState(), 2);
                helper.assertTrue(move(player, p, p.south(19), true, 1) > 0,
                        "A twenty-block-wide building must be allowed");
                helper.assertTrue(level.getBlockState(p.east()).is(Blocks.COBBLESTONE)
                                && level.getBlockState(p.east().south(19)).is(Blocks.OAK_PLANKS),
                        "Both endpoints of a twenty-block-wide building must move");
                clear.run();
                level.setBlock(p, Blocks.COBBLESTONE.defaultBlockState(), 2);
                int beforeWidthRejection = wallet.getCoins();
                helper.assertValueEqual(move(player, p, p.south(20), true, 1), 0,
                        "A twenty-one-block-wide building must be rejected");
                helper.assertTrue(level.getBlockState(p).is(Blocks.COBBLESTONE),
                        "Overwide buildings must remain untouched");
                helper.assertValueEqual(wallet.getCoins(), beforeWidthRejection,
                        "Overwide buildings must not cost gold");
                player.setPos(p.getX() - 3, p.getY(), p.getZ() + 0.5);
                helper.assertValueEqual(move(player, p, p.south(20), false, 1), 0,
                        "A twenty-one-block-wide wall must be rejected");
                helper.assertTrue(level.getBlockState(p).is(Blocks.COBBLESTONE),
                        "Overwide walls must remain untouched");
                helper.assertValueEqual(wallet.getCoins(), beforeWidthRejection,
                        "Overwide walls must not cost gold");
                helper.assertTrue(move(player, p, p.south(19), false, 1) > 0,
                        "An exactly twenty-block-wide wall must still move");
                helper.assertTrue(level.getBlockState(p.west()).is(Blocks.COBBLESTONE),
                        "An allowed twenty-block-wide wall must move horizontally");
                wallet.setCoins(100000);
                clear.run();
                level.setBlock(p, Blocks.COBBLESTONE.defaultBlockState(), 2);
                level.setBlock(p.above(14), Blocks.GOLD_BLOCK.defaultBlockState(), 2);
                level.setBlock(p.above(15), Blocks.DIAMOND_BLOCK.defaultBlockState(), 2);
                BlockPos chestSource = p.east().above();
                level.setBlock(chestSource, Blocks.CHEST.defaultBlockState(), 2);
                ((ChestBlockEntity) level.getBlockEntity(chestSource)).setItem(0, new ItemStack(Items.DIAMOND, 7));
                level.setBlock(p.north(2), Blocks.STONE.defaultBlockState(), 2);
                var dispatcher = level.getServer().getCommands().getDispatcher();
                var source = player.createCommandSourceStack();
                helper.assertValueEqual(dispatcher.execute("build pull_wall 2", source), 1,
                        "A non-op must be able to enter selection mode");
                var left = new PlayerInteractEvent.LeftClickBlock(player, p, Direction.NORTH,
                        PlayerInteractEvent.LeftClickBlock.Action.START);
                NeoForge.EVENT_BUS.post(left);
                helper.assertTrue(left.isCanceled() && level.getBlockState(p).is(Blocks.COBBLESTONE),
                        "First selection must cancel actual block breaking");
                var right = new PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND, p.east(),
                        new BlockHitResult(Vec3.atCenterOf(p.east()), Direction.NORTH, p.east(), false));
                particles.clear();
                NeoForge.EVENT_BUS.post(right);
                helper.assertTrue(right.isCanceled(), "Second selection must consume the interaction");
                helper.assertTrue(level.getBlockState(p).is(Blocks.COBBLESTONE)
                                && level.getBlockState(p.north(2)).is(Blocks.STONE),
                        "Preview must not change source or destination");
                helper.assertValueEqual(wallet.getCoins(), 100000, "Preview must not charge gold");
                assertOutline(helper, particles, p, p.east().above(14), true);
                assertOutline(helper, particles, p.north(2), p.east().north(2).above(14), false);
                helper.assertTrue(components.stream().anyMatch(component ->
                                new ClickEvent.RunCommand("/build confirm").equals(component.getStyle().getClickEvent())),
                        "Preview must offer a clickable confirm command");
                helper.assertTrue(components.stream().flatMap(component -> component.getSiblings().stream())
                                .anyMatch(component -> new ClickEvent.RunCommand("/build cancel")
                                        .equals(component.getStyle().getClickEvent())),
                        "Preview must offer a clickable cancel command");
                helper.assertTrue(dispatcher.execute("build confirm", source) > 0,
                        "Explicit confirmation must execute the previewed move");
                helper.assertValueEqual(dispatcher.execute("build confirm", source), 0,
                        "A preview cannot be confirmed twice");
                helper.assertTrue(level.getBlockState(p).isAir(), "Source wall must be removed: " + messages);
                helper.assertTrue(level.getBlockState(p.north(2)).is(Blocks.COBBLESTONE),
                        "Pull must move toward the player and overwrite ordinary destination blocks");
                helper.assertTrue(level.getBlockState(p.north(2).above(14)).is(Blocks.GOLD_BLOCK),
                        "The fifteenth row must move");
                helper.assertTrue(level.getBlockState(p.above(15)).is(Blocks.DIAMOND_BLOCK),
                        "The sixteenth row must remain untouched");
                var chest = (ChestBlockEntity) level.getBlockEntity(chestSource.north(2));
                helper.assertTrue(chest != null && chest.getItem(0).is(Items.DIAMOND)
                        && chest.getItem(0).getCount() == 7, "Moved chest inventory must survive exactly");
                helper.assertTrue(level.getBlockEntity(chestSource) == null, "Chest must move, not duplicate");
                helper.assertValueEqual(wallets.getData(player.getUUID()).getCoins(), 99997,
                        "Only three non-air source blocks must be charged, excluding the sixteenth row and air");
                helper.assertTrue(messages.stream().anyMatch(text -> text.contains("Build move cost: 3 gp"))
                                && messages.stream().anyMatch(text -> text.contains("Charged 3 gp")),
                        "The exact cost and successful charge must be shown to the player");

                clear.run();
                level.setBlock(p, Blocks.COBBLESTONE.defaultBlockState(), 2);
                level.setBlock(p.east(), Blocks.OAK_PLANKS.defaultBlockState(), 2);
                wallet.setCoins(1);
                helper.assertValueEqual(move(player, p, p.east(), false, 2), 0,
                        "Insufficient gold must cancel a move");
                helper.assertTrue(level.getBlockState(p).is(Blocks.COBBLESTONE)
                                && level.getBlockState(p.north(2)).isAir(),
                        "Insufficient funds must not change source or destination");
                helper.assertValueEqual(wallet.getCoins(), 1, "Rejected moves must not charge gold");
                wallet.setCoins(2);
                helper.assertTrue(move(player, p, p.east(), false, 2) > 0,
                        "Exact balance must be sufficient");
                helper.assertValueEqual(wallets.getData(player.getUUID()).getCoins(), 0,
                        "Exact-balance payment must persist a zero balance");
                wallet.setCoins(100000);

                clear.run();
                level.setBlock(p, Blocks.COBBLESTONE.defaultBlockState(), 2);
                level.setBlock(p.north(2), Blocks.CHEST.defaultBlockState(), 2);
                var destinationChest = (ChestBlockEntity) level.getBlockEntity(p.north(2));
                destinationChest.setItem(0, new ItemStack(Items.EMERALD, 9));
                helper.assertValueEqual(move(player, p, p.east(), false, 2), 0,
                        "Destination block entities must cancel the whole move");
                helper.assertTrue(level.getBlockState(p).is(Blocks.COBBLESTONE),
                        "Rejected collision must not remove the source");
                helper.assertValueEqual(destinationChest.getItem(0).getCount(), 9,
                        "Rejected collision must preserve destination inventory");
                helper.assertValueEqual(wallet.getCoins(), 100000, "Collision failures must not charge gold");

                clear.run();
                for (int z = 0; z < 3; z++) {
                    level.setBlock(p.south(z), Blocks.COBBLESTONE.defaultBlockState(), 2);
                    level.setBlock(p.east().south(z), Blocks.OAK_PLANKS.defaultBlockState(), 2);
                }
                level.setBlock(p.south(3), Blocks.DIRT.defaultBlockState(), 2);
                level.setBlock(p.above(), Blocks.CHEST.defaultBlockState(), 2);
                ((ChestBlockEntity) level.getBlockEntity(p.above())).setItem(0, new ItemStack(Items.DIAMOND, 5));
                level.setBlock(p.above(2), Blocks.OAK_LOG.defaultBlockState(), 2);
                level.setBlock(p.east().above(2), Blocks.OAK_LEAVES.defaultBlockState(), 2);
                helper.assertTrue(move(player, p, p.east(), true, 1) > 0,
                        "Overlapping building push must succeed: " + messages);
                var overlapChest = (ChestBlockEntity) level.getBlockEntity(p.south().above());
                helper.assertTrue(overlapChest != null && overlapChest.getItem(0).getCount() == 5,
                        "Overlapping moves must preserve chest contents");
                helper.assertTrue(level.getBlockState(p.south().above(2)).is(Blocks.OAK_LOG)
                                && level.getBlockState(p.east().south().above(2)).is(Blocks.OAK_LEAVES),
                        "Moving logs/leaves must not run destructive harvesting rolls");
                helper.assertTrue(player.getInventory().isEmpty(), "Moves must not grant harvesting bonuses");
                helper.assertTrue(level.getBlockState(p).isAir(), "Front slice must be vacated");
                for (int z = 1; z <= 3; z++) {
                    helper.assertTrue(level.getBlockState(p.south(z)).is(Blocks.COBBLESTONE),
                            "All three building slices must move, without overlap corruption");
                }
                helper.assertTrue(level.getBlockState(p.south(4)).isAir(),
                        "Dirt boundary must not be included as a fourth source slice");

                clear.run();
                for (int z = 0; z < 21; z++) {
                    level.setBlock(p.south(z), Blocks.COBBLESTONE.defaultBlockState(), 2);
                    level.setBlock(p.east().south(z), Blocks.OAK_PLANKS.defaultBlockState(), 2);
                }
                helper.assertTrue(move(player, p, p.east(), true, 2) > 0,
                        "Solid building edges must stop at the twenty-block depth cap");
                helper.assertTrue(level.getBlockState(p.south(21)).is(Blocks.COBBLESTONE),
                        "Twentieth slice must reach depth 21 after a two-block push");
                helper.assertTrue(level.getBlockState(p.south(22)).isAir(),
                        "Twenty-first source slice must not move beyond the cap");

                clear.run();
                for (int z = 0; z < 3; z++) {
                    level.setBlock(p.south(z), Blocks.COBBLESTONE.defaultBlockState(), 2);
                    level.setBlock(p.east().south(z), Blocks.OAK_PLANKS.defaultBlockState(), 2);
                }
                level.setBlock(p.east().south(), Blocks.STONE.defaultBlockState(), 2);
                helper.assertTrue(move(player, p, p.east(), true, 1) > 0,
                        "Plain stone on the second edge must stop depth before that slice");
                helper.assertTrue(level.getBlockState(p.south(2)).is(Blocks.COBBLESTONE),
                        "A second-edge boundary must leave blocks beyond it in place");
                helper.assertTrue(level.getBlockState(p.east().south()).is(Blocks.OAK_PLANKS),
                        "Only the front slice must be pushed when the second edge stops immediately");

                clear.run();
                player.setPos(p.getX() - 3, p.getY(), p.getZ() + 0.5);
                level.setBlock(p, Blocks.COBBLESTONE.defaultBlockState(), 2);
                level.setBlock(p.south(), Blocks.OAK_PLANKS.defaultBlockState(), 2);
                helper.assertTrue(move(player, p.south(), p, false, 1) > 0,
                        "Reversed corners along Z must support pulling west");
                helper.assertTrue(level.getBlockState(p.west()).is(Blocks.COBBLESTONE)
                                && level.getBlockState(p.south().west()).is(Blocks.OAK_PLANKS),
                        "Rotated walls must move perpendicular to the selected front");

                clear.run();
                level.setBlock(p, Blocks.COBBLESTONE.defaultBlockState(), 2);
                var protectedPlot = new ChunkData("house", -1, UUID.randomUUID(), UUID.randomUUID());
                protectedPlot.setBoughtY(p.getY());
                ModEvents.getChunkDataDatabase(level).putData(fixture.key, protectedPlot);
                helper.assertValueEqual(move(player, p, p.east(), false, 2), 0,
                        "Village/house protection must reject nonmembers");
                helper.assertTrue(level.getBlockState(p).is(Blocks.COBBLESTONE),
                        "Protection failure must leave the entire region unchanged");
                int balanceBeforeVeto = wallet.getCoins();
                ModEvents.getChunkDataDatabase(level).removeDataBatch(java.util.List.of(fixture.key));
                Consumer<BreakBlockEvent> denyBreak = event -> {
                    if (event.getPlayer() == player && event.getPos().equals(p.above(14))) event.setCanceled(true);
                };
                NeoForge.EVENT_BUS.addListener(denyBreak);
                try {
                    helper.assertValueEqual(move(player, p, p.east(), false, 2), 0,
                            "A break veto anywhere in the region must reject the whole move");
                } finally {
                    NeoForge.EVENT_BUS.unregister(denyBreak);
                }
                Consumer<BlockEvent.EntityPlaceEvent> denyPlace = event -> {
                    if (event.getEntity() == player && event.getPos().equals(p.north(2))) event.setCanceled(true);
                };
                NeoForge.EVENT_BUS.addListener(denyPlace);
                try {
                    helper.assertValueEqual(move(player, p, p.east(), false, 2), 0,
                            "A placement veto must reject the whole move");
                    helper.assertTrue(level.getBlockState(p).is(Blocks.COBBLESTONE),
                            "Event vetoes must never partially move the source");
                } finally {
                    NeoForge.EVENT_BUS.unregister(denyPlace);
                }
                helper.assertValueEqual(wallets.getData(player.getUUID()).getCoins(), balanceBeforeVeto,
                        "Protection vetoes must never charge gold");
                helper.assertTrue(move(player, p, p.east(), false, 2) > 0,
                        "Removing temporary protection vetoes must allow a subsequent move");
                helper.assertValueEqual(move(player, p, p.east().above(), false, 2), 0,
                        "Unequal bottom heights must be rejected");
                helper.assertValueEqual(move(player, p, p.east().south(), false, 2), 0,
                        "Diagonal walls must be rejected");
                helper.assertValueEqual(dispatcher.execute("build pull_wall 2", source), 1, "Restart selection");
                helper.assertValueEqual(dispatcher.execute("build cancel", source), 1, "Cancel selection");
                var unselected = new PlayerInteractEvent.LeftClickBlock(player, p, Direction.NORTH,
                        PlayerInteractEvent.LeftClickBlock.Action.START);
                BuildCommand.leftClick(unselected);
                helper.assertTrue(!unselected.isCanceled(), "Canceled selections must leave ordinary clicks alone");

                clear.run();
                level.setBlock(p, Blocks.COBBLESTONE.defaultBlockState(), 2);
                int safetyBalance = wallet.getCoins();
                helper.assertValueEqual(BuildCommand.preview(player, p, p.east(), false, 2), 1, "Preview before cancel");
                dispatcher.execute("build cancel", source);
                helper.assertValueEqual(BuildCommand.confirm(player), 0, "Cancel must discard the preview");
                helper.assertTrue(level.getBlockState(p).is(Blocks.COBBLESTONE), "Cancel must preserve source");
                helper.assertValueEqual(wallet.getCoins(), safetyBalance, "Cancel must not charge gold");

                helper.assertValueEqual(BuildCommand.preview(player, p, p.east(), false, 2), 1, "Preview before edit");
                level.setBlock(p.north(2), Blocks.DIRT.defaultBlockState(), 2);
                helper.assertValueEqual(BuildCommand.confirm(player), 0, "Destination edits must invalidate the preview");
                helper.assertTrue(level.getBlockState(p).is(Blocks.COBBLESTONE)
                                && level.getBlockState(p.north(2)).is(Blocks.DIRT),
                        "Stale preview must not overwrite edits");
                helper.assertValueEqual(BuildCommand.confirm(player), 0, "Stale preview must require reselection");

                helper.assertValueEqual(BuildCommand.preview(player, p, p.east(), false, 2), 1, "Preview before source edit");
                level.setBlock(p.above(), Blocks.OAK_PLANKS.defaultBlockState(), 2);
                helper.assertValueEqual(BuildCommand.confirm(player), 0, "Adding source blocks must invalidate quoted cost");
                clear.run();
                level.setBlock(p, Blocks.COBBLESTONE.defaultBlockState(), 2);
                helper.assertValueEqual(BuildCommand.preview(player, p, p.east(), true, 2), 1, "Preview before boundary edit");
                level.setBlock(p.south(), Blocks.OAK_PLANKS.defaultBlockState(), 2);
                helper.assertValueEqual(BuildCommand.confirm(player), 0, "Traced depth boundary edits must invalidate preview");
                helper.assertValueEqual(wallet.getCoins(), safetyBalance, "Boundary edits must not charge gold");
                clear.run();
                level.setBlock(p, Blocks.COBBLESTONE.defaultBlockState(), 2);
                level.setBlock(p.east(), Blocks.CHEST.defaultBlockState(), 2);
                var changingChest = (ChestBlockEntity) level.getBlockEntity(p.east());
                changingChest.setItem(0, new ItemStack(Items.DIAMOND, 3));
                helper.assertValueEqual(BuildCommand.preview(player, p, p.east(), false, 2), 1, "Preview chest");
                changingChest.setItem(0, new ItemStack(Items.DIAMOND, 4));
                helper.assertValueEqual(BuildCommand.confirm(player), 0, "Inventory changes must invalidate the preview");
                helper.assertValueEqual(changingChest.getItem(0).getCount(), 4, "Changed chest contents must be untouched");

                clear.run();
                level.setBlock(p, Blocks.COBBLESTONE.defaultBlockState(), 2);
                helper.assertValueEqual(BuildCommand.preview(player, p, p.east(), false, 2), 1, "Preview before funds change");
                wallet.setCoins(0);
                helper.assertValueEqual(BuildCommand.confirm(player), 0, "Confirmation must recheck funds");
                helper.assertTrue(level.getBlockState(p).is(Blocks.COBBLESTONE), "Insufficient funds must not move blocks");
                wallet.setCoins(safetyBalance);

                helper.assertValueEqual(BuildCommand.preview(player, p, p.east(), false, 2), 1, "Preview before mode change");
                player.setGameMode(GameType.ADVENTURE);
                helper.assertValueEqual(BuildCommand.confirm(player), 0, "Confirmation must recheck build permission");
                player.setGameMode(GameType.SURVIVAL);

                helper.assertValueEqual(BuildCommand.preview(player, p, p.east(), false, 2), 1, "Preview before dimension change");
                var otherLevel = level.getServer().getLevel(net.minecraft.world.level.Level.NETHER);
                if (otherLevel == null) throw new IllegalStateException("Dimension regression needs a Nether level");
                try {
                    player.setServerLevel(otherLevel);
                    helper.assertValueEqual(BuildCommand.confirm(player), 0, "Dimension change must reject confirmation");
                } finally {
                    player.setServerLevel(level);
                }

                helper.assertValueEqual(BuildCommand.preview(player, p, p.east(), false, 2), 1, "Preview before claim change");
                plots.putData(fixture.key, protectedPlot);
                helper.assertValueEqual(BuildCommand.confirm(player), 0, "Confirmation must recheck village protection");
                plots.removeDataBatch(List.of(fixture.key));
                helper.assertValueEqual(wallet.getCoins(), safetyBalance, "Invalid confirmations must not charge gold");

                helper.assertValueEqual(BuildCommand.preview(player, p, p.east(), false, 2), 1, "Preview before event veto");
                NeoForge.EVENT_BUS.addListener(denyPlace);
                try {
                    helper.assertValueEqual(BuildCommand.confirm(player), 0, "Confirmation must repost placement protection");
                } finally {
                    NeoForge.EVENT_BUS.unregister(denyPlace);
                }

                helper.assertValueEqual(BuildCommand.preview(player, p, p.east(), false, 2), 1, "Preview before expiry");
                long originalTime = level.getGameTime();
                if (!(level.getLevelData() instanceof net.minecraft.world.level.storage.ServerLevelData timeData)) {
                    throw new IllegalStateException("Expiry regression needs writable server level data");
                }
                try {
                    timeData.setGameTime(originalTime + 10 - originalTime % 10);
                    particles.clear();
                    BuildCommand.tick(new PlayerTickEvent.Post(player));
                    helper.assertTrue(!particles.isEmpty(), "Tick must refresh pending preview outlines");
                    timeData.setGameTime(originalTime + 2400);
                    particles.clear();
                    BuildCommand.tick(new PlayerTickEvent.Post(player));
                    helper.assertTrue(particles.isEmpty(), "Expired previews must stop drawing");
                    helper.assertValueEqual(BuildCommand.confirm(player), 0, "Expired preview must not execute");
                } finally {
                    timeData.setGameTime(originalTime);
                }
                helper.assertValueEqual(BuildCommand.preview(player, p, p.east(), false, 2), 1, "Preview before logout");
                BuildCommand.logout(new PlayerEvent.PlayerLoggedOutEvent(player));
                helper.assertValueEqual(BuildCommand.confirm(player), 0, "Logout must discard the preview");
                helper.assertValueEqual(BuildCommand.preview(player, p, p.east(), false, 2), 1, "Preview before restarting");
                dispatcher.execute("build push_building 1", source);
                helper.assertValueEqual(BuildCommand.confirm(player), 0, "A new command must discard the old preview");
                dispatcher.execute("build cancel", source);

                helper.assertValueEqual(BuildCommand.preview(player, p, p.east(), false, 10), 1,
                        "Maximum ten-block pull must preview");
                var otherPlayer = new FakePlayer(level, new com.mojang.authlib.GameProfile(UUID.randomUUID(), "other-builder"));
                helper.assertValueEqual(BuildCommand.confirm(otherPlayer), 0, "Another player cannot confirm this preview");
                otherPlayer.discard();
                player.setPos(p.getX() + 0.5, p.getY(), p.getZ() + 3);
                helper.assertTrue(BuildCommand.confirm(player) > 0, "Moving to the other side must not flip the preview");
                helper.assertTrue(level.getBlockState(p.north(10)).is(Blocks.COBBLESTONE),
                        "Ten-block pull must keep its previewed destination");
                clear.run();
                level.setBlock(p, Blocks.COBBLESTONE.defaultBlockState(), 2);
                helper.assertTrue(move(player, p, p.east(), true, 10) > 0, "Maximum ten-block push must execute");
                helper.assertTrue(level.getBlockState(p.south(10)).is(Blocks.COBBLESTONE),
                        "Ten-block push must reach the previewed destination");
                helper.assertValueEqual(BuildCommand.preview(player, p, p.east(), true, 11), 0,
                        "Pushes beyond ten blocks must be rejected");
                helper.assertValueEqual(BuildCommand.preview(player, p, p.east(), false, 11), 0,
                        "Pulls beyond ten blocks must be rejected");

                clear.run();
                level.setBlock(p, Blocks.COBBLESTONE.defaultBlockState(), 2);
                player.setGameMode(GameType.CREATIVE);
                wallet.setCoins(0);
                helper.assertValueEqual(BuildCommand.preview(player, p, p.east(), false, 2), 1,
                        "Creative players with zero gold must be able to preview");
                helper.assertTrue(messages.stream().anyMatch(text -> text.contains("Build move cost: 0 gp")
                                && text.contains("1 non-air blocks") && text.contains("creative mode is free")),
                        "Creative preview must show zero cost without losing the actual block count");
                helper.assertTrue(BuildCommand.confirm(player) > 0, "Creative move must succeed without funds");
                helper.assertValueEqual(wallets.getData(player.getUUID()).getCoins(), 0,
                        "Creative move must not deduct gold");

                clear.run();
                level.setBlock(p, Blocks.COBBLESTONE.defaultBlockState(), 2);
                wallets.removeDataBatch(List.of(player.getUUID()));
                helper.assertTrue(move(player, p, p.east(), true, 2) > 0,
                        "Creative move must not require a wallet");
                helper.assertTrue(wallets.getData(player.getUUID()) == null,
                        "Free moves must not create or save a wallet");
                wallets.putData(player.getUUID(), wallet);

                clear.run();
                level.setBlock(p, Blocks.COBBLESTONE.defaultBlockState(), 2);
                wallet.setCoins(100);
                helper.assertValueEqual(BuildCommand.preview(player, p, p.east(), false, 2), 1, "Free creative preview");
                player.setGameMode(GameType.SURVIVAL);
                helper.assertValueEqual(BuildCommand.confirm(player), 0,
                        "Switching to survival must invalidate a free creative quote");
                helper.assertValueEqual(wallet.getCoins(), 100, "Invalidated quote must not charge");
                helper.assertValueEqual(BuildCommand.preview(player, p, p.east(), false, 2), 1, "Paid survival preview");
                player.setGameMode(GameType.CREATIVE);
                helper.assertValueEqual(BuildCommand.confirm(player), 0,
                        "Switching to creative must require a new free preview");
                helper.assertTrue(move(player, p, p.east(), false, 2) > 0, "New creative preview must execute free");
                helper.assertValueEqual(wallet.getCoins(), 100, "Creative move must leave positive balances unchanged");
                player.setGameMode(GameType.SURVIVAL);
            } finally {
                original.forEach((pos, state) -> {
                    level.removeBlockEntity(pos);
                    level.setBlock(pos, state, 2);
                });
                plots.removeDataBatch(new ArrayList<>(originalPlots.keySet()));
                originalPlots.forEach((key, data) -> {
                    if (data != null) plots.putData(key, data);
                });
                player.discard();
            }
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException failure) {
            throw new IllegalStateException("Build command registration failed", failure);
        }
        helper.succeed();
    }

    private static int move(net.minecraft.server.level.ServerPlayer player, BlockPos first, BlockPos second,
                            boolean building, int distance) {
        return BuildCommand.preview(player, first, second, building, distance) == 0 ? 0 : BuildCommand.confirm(player);
    }

    private static void assertOutline(GameTestHelper helper, List<ClientboundLevelParticlesPacket> particles,
                                      BlockPos min, BlockPos max, boolean red) {
        var colored = particles.stream().filter(packet -> packet.getParticle() instanceof DustParticleOptions dust
                && (dust.getColor().x > dust.getColor().y) == red).toList();
        helper.assertTrue(!colored.isEmpty(), "Both source and destination must have colored outlines");
        for (var packet : colored) {
            boolean xEdge = packet.getX() == min.getX() || packet.getX() == max.getX() + 1.0;
            boolean yEdge = packet.getY() == min.getY() || packet.getY() == max.getY() + 1.0;
            boolean zEdge = packet.getZ() == min.getZ() || packet.getZ() == max.getZ() + 1.0;
            helper.assertTrue((xEdge ? 1 : 0) + (yEdge ? 1 : 0) + (zEdge ? 1 : 0) >= 2
                            && packet.getX() >= min.getX() && packet.getX() <= max.getX() + 1.0
                            && packet.getY() >= min.getY() && packet.getY() <= max.getY() + 1.0
                            && packet.getZ() >= min.getZ() && packet.getZ() <= max.getZ() + 1.0,
                    "Particles must outline the exact inclusive selection bounds");
        }
        for (int x : new int[]{min.getX(), max.getX() + 1}) {
            for (int y : new int[]{min.getY(), max.getY() + 1}) {
                for (int z : new int[]{min.getZ(), max.getZ() + 1}) {
                    helper.assertTrue(colored.stream().anyMatch(packet ->
                                    packet.getX() == x && packet.getY() == y && packet.getZ() == z),
                            "Every bounding-box corner must be highlighted");
                }
            }
        }
    }
}
