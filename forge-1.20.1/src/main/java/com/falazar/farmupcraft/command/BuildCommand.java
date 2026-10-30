package com.falazar.farmupcraft.command;

import com.falazar.farmupcraft.ChunkManager;
import com.falazar.farmupcraft.data.PlayerData;
import com.falazar.farmupcraft.FarmUpCraft;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.server.commands.CloneCommands;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.GameMasterBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.event.TickEvent;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = FarmUpCraft.MODID)
public final class BuildCommand {
    private static final Map<UUID, Selection> SELECTIONS = new HashMap<>();
    private static final Map<UUID, Preview> PREVIEWS = new HashMap<>();
    private static final int HEIGHT = 15;
    private static final int MAX_WIDTH = 20;
    private static final int MAX_DEPTH = 20;

    private BuildCommand() {}

    private record Selection(ServerLevel level, boolean building, int distance, long expires, BlockPos first) {}
    private record Snapshot(BlockState state, CompoundTag data) {}
    private record Plan(ServerLevel level, BlockPos first, BlockPos second, boolean building, int distance,
                        Direction toward, int width, int depth, BlockPos min, BlockPos max, BlockPos offset,
                        int blocks, int cost, Map<BlockPos, BlockState> changes, Map<BlockPos, Snapshot> before) {}
    private record Preview(Plan plan, long expires) {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var root = Commands.literal("build");
        for (String action : new String[]{"pull_wall", "push_building"}) {
            root.then(Commands.literal(action)
                    .then(Commands.argument("distance", IntegerArgumentType.integer(1, 10))
                            .executes(ctx -> begin(ctx.getSource(), action.equals("push_building"),
                                    IntegerArgumentType.getInteger(ctx, "distance")))));
        }
        root.then(Commands.literal("confirm").executes(ctx -> {
            if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
                ctx.getSource().sendFailure(Component.literal("Only players can use /build."));
                return 0;
            }
            return confirm(player);
        }));
        dispatcher.register(root.then(Commands.literal("cancel").executes(ctx -> {
            if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
                ctx.getSource().sendFailure(Component.literal("Only players can use /build."));
                return 0;
            }
            SELECTIONS.remove(player.getUUID());
            PREVIEWS.remove(player.getUUID());
            ctx.getSource().sendSuccess(() -> Component.literal("Build selection/preview canceled. No gold charged."), false);
            return 1;
        })));
    }

    private static int begin(CommandSourceStack source, boolean building, int distance) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Only players can use /build."));
            return 0;
        }
        if (!player.mayBuild()) {
            source.sendFailure(Component.literal("You must be able to build to use /build."));
            return 0;
        }
        PREVIEWS.remove(player.getUUID());
        SELECTIONS.put(player.getUUID(),
                new Selection(source.getLevel(), building, distance, source.getLevel().getGameTime() + 2400, null));
        source.sendSuccess(() -> Component.literal(
                "Left-click the bottom-left block, then right-click the bottom-right block on the same straight wall. "
                        + "Costs 1 gp per non-air source block. The move overwrites destination blocks, "
                        + "but not block entities. The second click previews only; /build confirm to move, /build cancel to cancel."), false);
        return 1;
    }

    private static Selection pending(ServerPlayer player) {
        Selection selection = SELECTIONS.get(player.getUUID());
        if (selection != null && (selection.level != player.level()
                || selection.level.getGameTime() > selection.expires)) {
            SELECTIONS.remove(player.getUUID());
            player.sendSystemMessage(Component.literal("Build selection expired or changed dimension. Run /build again."));
            return null;
        }
        return selection;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void leftClick(PlayerInteractEvent.LeftClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Selection selection = pending(player);
        if (selection == null) return;
        event.setCanceled(true);
        player.connection.send(new ClientboundBlockUpdatePacket(selection.level, event.getPos()));
        if (player.distanceToSqr(Vec3.atCenterOf(event.getPos())) > 64 * 64) {
            player.sendSystemMessage(Component.literal("Select a block within 64 blocks."));
            return;
        }
        SELECTIONS.put(player.getUUID(), new Selection(selection.level, selection.building,
                selection.distance, selection.expires, event.getPos().immutable()));
        player.sendSystemMessage(Component.literal("First corner selected. Right-click the other bottom corner."));
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void rightClick(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getHand() != InteractionHand.MAIN_HAND) return;
        Selection selection = pending(player);
        if (selection == null) return;
        event.setCanceled(true);
        event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
        if (selection.first == null) {
            player.sendSystemMessage(Component.literal("Left-click the first bottom corner first."));
            return;
        }
        SELECTIONS.remove(player.getUUID());
        preview(player, selection.first, event.getPos(), selection.building, selection.distance);
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        SELECTIONS.remove(event.getEntity().getUUID());
        PREVIEWS.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void stopping(ServerStoppingEvent event) {
        SELECTIONS.clear();
        PREVIEWS.clear();
    }

    public static int preview(ServerPlayer player, BlockPos first, BlockPos second, boolean building, int distance) {
        PREVIEWS.remove(player.getUUID());
        try {
            Plan plan = prepare(player, first, second, building, distance, null);
            if (plan.cost > 0) requireFunds(player, plan.cost);
            PREVIEWS.put(player.getUUID(), new Preview(plan, plan.level.getGameTime() + 2400));
            player.sendSystemMessage(Component.literal("Preview " + plan.width + " x " + HEIGHT + " x " + plan.depth
                    + ". RED = source, GREEN = destination. Build move cost: " + plan.cost
                    + " gp (" + plan.blocks + " non-air blocks"
                    + (player.isCreative() ? "; creative mode is free" : " at 1 gp each")
                    + "). Air also overwrites destination blocks."
                    + " Expires in 2 minutes. Nothing moved or charged."));
            player.sendSystemMessage(Component.literal("[Confirm]").withStyle(style -> style
                    .withColor(ChatFormatting.GREEN).withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/build confirm")))
                    .append(Component.literal("  "))
                    .append(Component.literal("[Cancel]").withStyle(style -> style
                            .withColor(ChatFormatting.RED).withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/build cancel")))));
            highlight(player, plan);
            return 1;
        } catch (IllegalArgumentException failure) {
            player.createCommandSourceStack().sendFailure(Component.literal("Build move canceled: " + failure.getMessage()));
            return 0;
        }
    }

    public static int confirm(ServerPlayer player) {
        Preview preview = PREVIEWS.remove(player.getUUID());
        if (preview == null) {
            player.createCommandSourceStack().sendFailure(Component.literal("No build preview. Select the corners first."));
            return 0;
        }
        try {
            Plan old = preview.plan;
            if (old.level != player.level() || old.level.getGameTime() >= preview.expires) {
                throw new IllegalArgumentException("Preview expired or changed dimension. Select again.");
            }
            requireUnchanged(old);
            Plan plan = prepare(player, old.first, old.second, old.building, old.distance, old.toward);
            if (!old.equals(plan)) throw new IllegalArgumentException("Area changed. Select again for a new preview.");
            requireUnchanged(old);
            return execute(player, plan);
        } catch (IllegalArgumentException | CommandSyntaxException failure) {
            player.createCommandSourceStack().sendFailure(Component.literal("Build move canceled: " + failure.getMessage()));
            return 0;
        }
    }

    private static Plan prepare(ServerPlayer player, BlockPos first, BlockPos second,
                                boolean building, int distance, Direction fixedToward) {
        if (!player.mayBuild()) throw new IllegalArgumentException("You do not have build permission.");
        if (distance < 1 || distance > 10) throw new IllegalArgumentException("Distance must be 1 to 10.");
        if (first.getY() != second.getY() || first.equals(second)
                || (first.getX() != second.getX() && first.getZ() != second.getZ())) {
            throw new IllegalArgumentException("Select two different bottom corners at the same height, along X or Z.");
        }
        int width = Math.abs(first.getX() - second.getX()) + Math.abs(first.getZ() - second.getZ()) + 1;
        if (width > MAX_WIDTH) throw new IllegalArgumentException("Selection width may not exceed " + MAX_WIDTH + " blocks.");
        if (player.distanceToSqr(Vec3.atCenterOf(first)) > 64 * 64
                || player.distanceToSqr(Vec3.atCenterOf(second)) > 64 * 64) {
            throw new IllegalArgumentException("Both corners must be within 64 blocks of you.");
        }
        Direction toward = fixedToward;
        double side = first.getX() == second.getX() ? player.getX() - first.getX() - 0.5
                : player.getZ() - first.getZ() - 0.5;
        if (toward == null) {
            if (Math.abs(side) < 0.01) throw new IllegalArgumentException("Stand in front of the wall, not in its plane.");
            if (first.getX() == second.getX()) toward = side > 0 ? Direction.EAST : Direction.WEST;
            else toward = side > 0 ? Direction.SOUTH : Direction.NORTH;
        }
        ServerLevel level = (ServerLevel) player.level();
        Map<BlockPos, Snapshot> before = new LinkedHashMap<>();
        Direction back = toward.getOpposite();
        int depth = 1;
        if (building) {
            while (depth < MAX_DEPTH) {
                BlockPos left = first.relative(back, depth);
                BlockPos right = second.relative(back, depth);
                requireLoaded(level, left);
                requireLoaded(level, right);
                before.put(left, snapshot(level, left));
                before.put(right, snapshot(level, right));
                if (endsBuilding(level.getBlockState(left)) || endsBuilding(level.getBlockState(right))) break;
                depth++;
            }
        }
        BlockPos far = second.relative(back, depth - 1).above(HEIGHT - 1);
        BlockPos min = new BlockPos(Math.min(first.getX(), far.getX()), first.getY(),
                Math.min(first.getZ(), far.getZ()));
        BlockPos max = new BlockPos(Math.max(first.getX(), far.getX()), far.getY(),
                Math.max(first.getZ(), far.getZ()));
        BlockPos offset = BlockPos.ZERO.relative(building ? back : toward, distance);
        Map<BlockPos, BlockState> source = new LinkedHashMap<>();
        Map<BlockPos, BlockState> changes = new LinkedHashMap<>();
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            requireLoaded(level, pos);
            requireLoaded(level, pos.offset(offset));
            source.put(pos.immutable(), level.getBlockState(pos));
            changes.put(pos.immutable(), Blocks.AIR.defaultBlockState());
        }
        if (source.values().stream().allMatch(BlockState::isAir)) {
            throw new IllegalArgumentException("The selected region is empty.");
        }
        for (var entry : source.entrySet()) {
            BlockPos destination = entry.getKey().offset(offset);
            if (!source.containsKey(destination) && level.getBlockEntity(destination) != null) {
                throw new IllegalArgumentException("A destination block entity would be overwritten at " + destination.toShortString());
            }
            changes.put(destination, entry.getValue());
        }
        for (BlockPos pos : changes.keySet()) before.put(pos, snapshot(level, pos));
        for (var entry : changes.entrySet()) {
            checkPermission(player, level, entry.getKey(), entry.getValue(), changes);
        }
        int blocks = (int) source.values().stream().filter(state -> !state.isAir()).count();
        int cost = player.isCreative() ? 0 : blocks;
        return new Plan(level, first.immutable(), second.immutable(), building, distance, toward,
                width, depth, min, max, offset, blocks, cost, changes, before);
    }

    private static PlayerData requireFunds(ServerPlayer player, int cost) {
        var wallet = PlayerCommand.getPlayer(player.createCommandSourceStack());
        if (wallet == null) throw new IllegalArgumentException("Player wallet is unavailable; no blocks moved.");
        if (wallet.getCoins() < cost) {
            throw new IllegalArgumentException("Not enough gold: need " + cost + " gp, have "
                    + wallet.getCoins() + " gp.");
        }
        return wallet;
    }

    private static int execute(ServerPlayer player, Plan plan) throws CommandSyntaxException {
        var wallet = plan.cost == 0 ? null : requireFunds(player, plan.cost);
        ServerLevel level = plan.level;
        // Vanilla clone move snapshots block entities and handles overlap, multipart blocks, and scheduled ticks.
        // Only this fully preflighted, coordinate-only operation gets an elevated internal command source.
        var dispatcher = new CommandDispatcher<CommandSourceStack>();
        CloneCommands.register(dispatcher, net.minecraft.commands.CommandBuildContext.simple(
                level.registryAccess(), level.enabledFeatures()));
        String command = "clone " + coordinates(plan.min) + " " + coordinates(plan.max) + " "
                + coordinates(plan.min.offset(plan.offset)) + " replace move";
        int result = dispatcher.execute(command, player.createCommandSourceStack()
                .withPermission(2).withSuppressedOutput());
        if (result <= 0) throw new IllegalArgumentException("No blocks moved; no gold charged.");
        if (wallet != null) {
            wallet.setCoins(wallet.getCoins() - plan.cost);
            PlayerCommand.savePlayer(player.getUUID(), wallet);
        }
        player.sendSystemMessage(Component.literal("Moved " + plan.width + " x " + HEIGHT + " x " + plan.depth
                + " blocks " + plan.distance + " blocks in the previewed direction."
                + (wallet == null ? " Creative mode: no gold charged."
                : " Charged " + plan.cost + " gp. Balance: " + wallet.getCoins() + " gp.")));
        return result;
    }

    private static Snapshot snapshot(ServerLevel level, BlockPos pos) {
        var entity = level.getBlockEntity(pos);
        return new Snapshot(level.getBlockState(pos),
                entity == null ? null : entity.saveWithFullMetadata());
    }

    private static void requireUnchanged(Plan plan) {
        for (var entry : plan.before.entrySet()) {
            requireLoaded(plan.level, entry.getKey());
            if (!entry.getValue().equals(snapshot(plan.level, entry.getKey()))) {
                throw new IllegalArgumentException("Blocks or block entity data changed. Select again for a new preview.");
            }
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        pending(player);
        Preview preview = PREVIEWS.get(player.getUUID());
        if (preview == null) return;
        if (preview.plan.level != player.level() || preview.plan.level.getGameTime() >= preview.expires) {
            PREVIEWS.remove(player.getUUID());
            player.sendSystemMessage(Component.literal("Build preview expired or changed dimension. No gold charged."));
        } else if (preview.plan.level.getGameTime() % 10 == 0) {
            highlight(player, preview.plan);
        }
    }

    private static void highlight(ServerPlayer player, Plan plan) {
        outline(player, plan.level, plan.min, plan.max, 0xFF5555);
        outline(player, plan.level, plan.min.offset(plan.offset), plan.max.offset(plan.offset), 0x55FF55);
    }

    private static void outline(ServerPlayer player, ServerLevel level, BlockPos min, BlockPos max, int color) {
        var dust = new DustParticleOptions(new org.joml.Vector3f(
                ((color >> 16) & 255) / 255F, ((color >> 8) & 255) / 255F, (color & 255) / 255F), 1.2F);
        double[] low = {min.getX(), min.getY(), min.getZ()};
        double[] high = {max.getX() + 1.0, max.getY() + 1.0, max.getZ() + 1.0};
        for (int axis = 0; axis < 3; axis++) {
            int b = (axis + 1) % 3, c = (axis + 2) % 3;
            for (int sideB = 0; sideB < 2; sideB++) {
                for (int sideC = 0; sideC < 2; sideC++) {
                    double[] point = low.clone();
                    point[b] = sideB == 0 ? low[b] : high[b];
                    point[c] = sideC == 0 ? low[c] : high[c];
                    for (double step = low[axis]; step <= high[axis]; step++) {
                        point[axis] = step;
                        level.sendParticles(player, dust, true, point[0], point[1], point[2],
                                1, 0, 0, 0, 0);
                    }
                }
            }
        }
    }

    static boolean endsBuilding(BlockState state) {
        return state.isAir() || state.is(Blocks.DIRT) || state.is(Blocks.STONE);
    }

    private static String coordinates(BlockPos pos) {
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    private static void requireLoaded(ServerLevel level, BlockPos pos) {
        if (pos.getY() < level.getMinBuildHeight() || pos.getY() >= level.getMaxBuildHeight()
                || !level.getWorldBorder().isWithinBounds(pos) || !level.hasChunkAt(pos)) {
            throw new IllegalArgumentException("Area must be loaded, within world height and inside the world border.");
        }
    }

    private static void checkPermission(ServerPlayer player, ServerLevel level, BlockPos pos,
                                        BlockState planned, Map<BlockPos, BlockState> changes) {
        BlockState existing = level.getBlockState(pos);
        if (!level.mayInteract(player, pos) || !player.mayUseItemAt(pos, Direction.UP, player.getMainHandItem())
                || (existing.getBlock() instanceof GameMasterBlock && !player.canUseGameMasterBlocks())
                || (!player.isCreative() && existing.getDestroySpeed(level, pos) < 0)) {
            throw new IllegalArgumentException("You cannot edit " + pos.toShortString());
        }
        if (MinecraftForge.EVENT_BUS.post(new MoveBreakEvent(level, pos, existing, player))) {
            throw new IllegalArgumentException("Breaking is protected at " + pos.toShortString());
        }
        if (!planned.isAir()) {
            BlockState below = changes.getOrDefault(pos.below(), level.getBlockState(pos.below()));
            if (!player.isCreative() && ChunkManager.isRockPathBlock(planned) && ChunkManager.isRockPathBlock(below)) {
                throw new IllegalArgumentException("Cannot stack rock paths.");
            }
            if (!player.isCreative() && planned.is(BlockTags.LOGS)
                    && ChunkManager.getPlotType(pos, level).equals("nursery")) {
                throw new IllegalArgumentException("Cannot move logs into a nursery plot.");
            }
            var snapshot = BlockSnapshot.create(level.dimension(), level, pos);
            var placement = new BlockEvent.EntityPlaceEvent(snapshot, level.getBlockState(pos.below()), player) {
                @Override public BlockState getPlacedBlock() { return planned; }
                @Override public BlockState getState() { return planned; }
            };
            if (MinecraftForge.EVENT_BUS.post(placement)) {
                throw new IllegalArgumentException("Placement is protected at " + pos.toShortString());
            }
        }
    }

    // Permission probes must not trigger harvesting rolls, bonus drops, or traps.
    public static final class MoveBreakEvent extends BlockEvent.BreakEvent {
        private MoveBreakEvent(ServerLevel level, BlockPos pos, BlockState state, ServerPlayer player) {
            super(level, pos, state, player);
        }
    }
}
