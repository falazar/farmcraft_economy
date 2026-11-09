package com.falazar.farmupcraft.events;

import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import com.falazar.farmupcraft.saveddata.PlacedOreData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.structures.MineshaftStructure;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(modid = FarmUpCraftNeoForge.MOD_ID)
public final class MineshaftOreEvents {
    private record Mined(ServerLevel level, BlockPos pos, boolean eligible, Player player) {}
    private static final List<Mined> PENDING = new ArrayList<>();
    private static final java.util.Map<Player, Integer> NEXT_MESSAGE = new java.util.WeakHashMap<>();

    private MineshaftOreEvents() {}

    public static boolean isOre(BlockState state) {
        return state.getBlock().builtInRegistryHolder().tags().anyMatch(tag -> {
            var id = tag.location();
            return (id.getNamespace().equals("c") || id.getNamespace().equals("forge"))
                    && (id.getPath().equals("ores") || id.getPath().startsWith("ores/"));
        }) || state.is(Blocks.COAL_ORE) || state.is(Blocks.DEEPSLATE_COAL_ORE) || state.is(BlockTags.IRON_ORES)
                || state.is(BlockTags.COPPER_ORES) || state.is(BlockTags.GOLD_ORES)
                || state.is(Blocks.REDSTONE_ORE) || state.is(Blocks.DEEPSLATE_REDSTONE_ORE)
                || state.is(Blocks.LAPIS_ORE) || state.is(Blocks.DEEPSLATE_LAPIS_ORE)
                || state.is(Blocks.DIAMOND_ORE) || state.is(Blocks.DEEPSLATE_DIAMOND_ORE)
                || state.is(Blocks.EMERALD_ORE) || state.is(Blocks.DEEPSLATE_EMERALD_ORE)
                || state.is(Blocks.NETHER_QUARTZ_ORE);
    }

    public static boolean insideMineshaft(ServerLevel level, BlockPos pos) {
        var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) return false;
        for (var entry : chunk.getAllReferences().entrySet()) {
            if (!(entry.getKey() instanceof MineshaftStructure)) continue;
            for (long reference : entry.getValue()) {
                var startChunk = level.getChunkSource().getChunkNow(ChunkPos.getX(reference), ChunkPos.getZ(reference));
                if (startChunk == null) continue;
                var start = startChunk.getStartForStructure(entry.getKey());
                if (start != null && start.isValid() && start.getBoundingBox().isInside(pos)) return true;
            }
        }
        return false;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void placed(BlockEvent.EntityPlaceEvent event) {
        if (event.isCanceled() || !(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof Player)) return;
        if (event instanceof BlockEvent.EntityMultiPlaceEvent multi) {
            for (var snapshot : multi.getReplacedBlockSnapshots()) {
                if (tracked(level.getBlockState(snapshot.getPos()))) PlacedOreData.get(level).mark(snapshot.getPos());
            }
        } else if (tracked(event.getPlacedBlock())) {
            PlacedOreData.get(level).mark(event.getPos());
        }
    }

    private static boolean tracked(BlockState state) {
        return isOre(state) || state.is(Blocks.STONE) || state.is(Blocks.DEEPSLATE);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void piston(net.neoforged.neoforge.event.level.PistonEvent.Pre event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        var resolver = event.getStructureHelper();
        if (resolver == null || !resolver.resolve()) return;
        var data = PlacedOreData.get(level);
        var direction = event.getPistonMoveType() == net.neoforged.neoforge.event.level.PistonEvent.PistonMoveType.EXTEND
                ? event.getDirection() : event.getDirection().getOpposite();
        var moved = resolver.getToPush().stream().filter(data::contains).map(BlockPos::immutable).toList();
        // Retain original marks conservatively if another handler cancels movement.
        for (var pos : moved) data.mark(pos.relative(direction));
    }

    @SubscribeEvent
    public static void stopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
        PENDING.removeIf(mined -> mined.level.getServer() == event.getServer());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void mined(BreakBlockEvent event) {
        if (event.isCanceled() || !(event.getLevel() instanceof ServerLevel level) || !tracked(event.getState())) return;
        var player = event.getPlayer();
        var pos = event.getPos().immutable();
        boolean eligible = isOre(event.getState()) && !player.isCreative() && !player.isSpectator()
                && !PlacedOreData.get(level).contains(pos) && insideMineshaft(level, pos);
        PENDING.add(new Mined(level, pos, eligible, player));
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        var completed = PENDING.stream().filter(mined -> mined.level.getServer() == event.getServer()).toList();
        PENDING.removeAll(completed);
        for (var mined : completed) {
            if (!mined.level.hasChunkAt(mined.pos) || !mined.level.getBlockState(mined.pos).isAir()) continue;
            PlacedOreData.get(mined.level).remove(mined.pos);
            if (mined.eligible && winsRoll(mined.level.getRandom().nextInt(10))
                    && replenish(mined.level, mined.pos)) {
                var messages = com.falazar.farmupcraft.util.LuckyMineMessages.MESSAGES;
                int index = NEXT_MESSAGE.getOrDefault(mined.player, 0);
                mined.player.sendSystemMessage(net.minecraft.network.chat.Component.literal(messages.get(index)));
                NEXT_MESSAGE.put(mined.player, (index + 1) % messages.size());
            }
        }
    }

    public static boolean winsRoll(int roll) {
        if (roll < 0 || roll >= 10) throw new IllegalArgumentException("Expected a roll from 0 to 9");
        return roll == 0;
    }

    public static List<Block> orePool() {
        return BuiltInRegistries.BLOCK.stream().filter(block -> isOre(block.defaultBlockState())
                && com.falazar.farmupcraft.GemContent.TYPES.stream().noneMatch(gem ->
                    block == com.falazar.farmupcraft.GemContent.ore(gem, false)
                    || block == com.falazar.farmupcraft.GemContent.ore(gem, true))
                && !block.defaultBlockState().hasBlockEntity()
                && block.defaultBlockState().getFluidState().isEmpty()).toList();
    }

    public static boolean replenish(ServerLevel level, BlockPos origin) {
        return replenish(level, origin, level.getRandom().nextBoolean());
    }

    public static boolean replenish(ServerLevel level, BlockPos origin, boolean exposed) {
        var ores = orePool();
        if (ores.isEmpty()) throw new IllegalStateException("No tagged ores available for mineshaft replenishment");
        Block ore = ores.get(level.getRandom().nextInt(ores.size()));
        for (int attempt = 0; attempt < 128; attempt++) {
            BlockPos pos = origin.offset(level.getRandom().nextInt(97) - 48, level.getRandom().nextInt(13) - 6,
                    level.getRandom().nextInt(97) - 48);
            if (!validDestination(level, origin, pos) || (exposed && !touchesAir(level, pos))) continue;
            return level.setBlockAndUpdate(pos, ore.defaultBlockState());
        }
        return false;
    }

    public static boolean touchesAir(ServerLevel level, BlockPos pos) {
        for (var direction : net.minecraft.core.Direction.values()) {
            var neighbor = pos.relative(direction);
            if (level.hasChunkAt(neighbor) && level.getBlockState(neighbor).isAir()) return true;
        }
        return false;
    }

    public static boolean validDestination(ServerLevel level, BlockPos origin, BlockPos pos) {
        long dx = (long) pos.getX() - origin.getX(), dz = (long) pos.getZ() - origin.getZ();
        if (dx * dx + dz * dz > 48L * 48 || Math.abs((long) pos.getY() - origin.getY()) > 6
                || !level.hasChunkAt(pos) || PlacedOreData.get(level).contains(pos)) return false;
        var state = level.getBlockState(pos);
        return !state.hasBlockEntity() && level.getBlockEntity(pos) == null
                && (state.is(Blocks.STONE) || state.is(Blocks.DEEPSLATE));
    }
}
