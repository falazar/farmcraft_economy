package com.falazar.farmupcraft.command;

import com.falazar.farmupcraft.VillagerManager;
import com.falazar.farmupcraft.data.GameZone;
import com.falazar.farmupcraft.database.DataBase;
import com.falazar.farmupcraft.events.ModEvents;
import com.falazar.farmupcraft.util.CustomLogger;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.AABB;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Development tools for structures, container books, villagers and geographic zones. */
public class TesterCommand {
    public static final CustomLogger LOGGER = new CustomLogger(TesterCommand.class.getSimpleName());

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("tester")
                .then(Commands.literal("islandsAndLakes")
                        .executes(context -> scanForIslandsAndLakes(context.getSource(), 10))
                        .then(Commands.argument("distance", IntegerArgumentType.integer(1, 100))
                                .executes(context -> scanForIslandsAndLakes(context.getSource(),
                                        IntegerArgumentType.getInteger(context, "distance")))))
                .then(Commands.literal("findvillagers").executes(context -> findNearVillagers(context.getSource())))
                .then(Commands.literal("renamevillager")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("oldName", StringArgumentType.string())
                                .then(Commands.argument("newName", StringArgumentType.string())
                                        .executes(context -> renameVillager(context.getSource(),
                                                StringArgumentType.getString(context, "oldName"),
                                                StringArgumentType.getString(context, "newName"))))))
                .then(Commands.literal("findneareststructure")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(context -> findNearestStructureCom(context.getSource())))
                .then(Commands.literal("testcloseststructure")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(context -> testClosestStructure(context.getSource())))
                .then(Commands.literal("findchests")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(context -> findChestsInStructure(context.getSource(), "structory:graveyard", 270582939571L))));
    }

    private static ServerPlayer player(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) source.sendFailure(Component.literal("This command can only be used by a player."));
        return player;
    }

    public static int findNearestStructureCom(CommandSourceStack source) {
        ServerPlayer player = player(source);
        if (player == null) return 0;
        Map.Entry<String, Long> nearest = findNearestStructure(player.blockPosition(), source.getLevel(), source);
        if (nearest == null) {
            source.sendFailure(Component.literal("No structures found nearby."));
            return 0;
        }
        source.sendSystemMessage(Component.literal("Nearest structure: " + nearest.getKey() + " ID: " + nearest.getValue())
                .withStyle(ChatFormatting.GOLD));
        return 1;
    }

    public static Map.Entry<String, Long> findNearestStructure(BlockPos position, ServerLevel level,
                                                               CommandSourceStack source) {
        Map<Structure, LongSet> structures = new HashMap<>();
        ChunkPos start = ChunkPos.containing(position);
        for (int x = -5; x < 5; x++) {
            for (int z = -5; z < 5; z++) {
                level.structureManager().getAllStructuresAt(new BlockPos((start.x() + x) << 4, 0,
                                (start.z() + z) << 4))
                        .forEach((type, references) -> structures.computeIfAbsent(type, ignored -> new LongOpenHashSet())
                                .addAll(references));
            }
        }
        Map.Entry<String, Long> nearest = null;
        double nearestDistance = Double.POSITIVE_INFINITY;
        var registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        for (var entry : structures.entrySet()) {
            String type = Objects.requireNonNull(registry.getKey(entry.getKey()), "Unregistered structure").toString();
            for (long id : entry.getValue()) {
                // A reference identifies a specific start, not every start of the same structure type.
                List<BoundingBox> boxes = new ArrayList<>();
                level.structureManager().fillStartsForStructure(entry.getKey(), new LongOpenHashSet(new long[]{id}),
                        structureStart -> boxes.add(structureStart.getBoundingBox()));
                for (BoundingBox box : boxes) {
                    double distance = box.getCenter().distSqr(position);
                    if (distance < nearestDistance) {
                        nearestDistance = distance;
                        nearest = Map.entry(type, id);
                    }
                }
            }
        }
        return nearest;
    }

    public static BoundingBox loadStructure(CommandSourceStack source, String type, Long id) {
        var registry = source.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        Structure structure = registry.getValue(Identifier.parse(type));
        if (structure == null) {
            source.sendFailure(Component.literal("Structure not found: " + type));
            return null;
        }
        List<BoundingBox> boxes = new ArrayList<>();
        source.getLevel().structureManager().fillStartsForStructure(structure, new LongOpenHashSet(new long[]{id}),
                start -> boxes.add(start.getBoundingBox()));
        if (boxes.isEmpty()) {
            source.sendFailure(Component.literal("Structure start not found: " + type + " ID: " + id));
            return null;
        }
        return boxes.getFirst();
    }

    static Iterable<BlockPos> blocksInBounds(BoundingBox bounds) {
        return BlockPos.betweenClosed(bounds.minX(), bounds.minY(), bounds.minZ(),
                bounds.maxX(), bounds.maxY(), bounds.maxZ());
    }

    private static List<BlockPos> containersInBounds(ServerLevel level, BoundingBox bounds) {
        List<BlockPos> containers = new ArrayList<>();
        for (BlockPos pos : blocksInBounds(bounds)) {
            var state = level.getBlockState(pos);
            if (state.is(Blocks.CHEST) || state.is(Blocks.BARREL)) containers.add(pos.immutable());
        }
        return containers;
    }

    public static int findChestsInStructure(CommandSourceStack source, String type, Long id) {
        BoundingBox bounds = loadStructure(source, type, id);
        if (bounds == null) return 0;
        List<BlockPos> containers = containersInBounds(source.getLevel(), bounds);
        for (BlockPos pos : containers) {
            LOGGER.info("Found chest at " + pos);
            source.sendSystemMessage(Component.literal("Found chest at " + pos.toShortString()).withStyle(ChatFormatting.GOLD));
        }
        if (containers.isEmpty()) source.sendFailure(Component.literal("No chests found in the structure."));
        return containers.size();
    }

    public static BlockPos findNearestChestInStructure(CommandSourceStack source, String type, Long id) {
        ServerPlayer player = player(source);
        if (player == null) return null;
        BoundingBox bounds = loadStructure(source, type, id);
        if (bounds == null) return null;
        return containersInBounds(source.getLevel(), bounds).stream()
                .min(Comparator.comparingDouble(pos -> pos.distSqr(player.blockPosition()))).orElse(null);
    }

    static ItemStack familyHistoryBook() {
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        List<Filterable<Component>> pages = List.of(
                Filterable.passThrough(Component.literal("We, the Eldrin Family, carved this life from the jade cliffs, yet the stone remembers the sacrifices made to appease its hunger.")),
                Filterable.passThrough(Component.literal("The mystic lake offers solace, but its depths hold reflections of horrors we dared not speak, only to pass them down in our blood.")),
                Filterable.passThrough(Component.literal("This book is a testament not of glory, but of the burden carried by each Eldrin, a legacy entwined with the shadowed fate of this mountain.")));
        book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(
                Filterable.passThrough("Family History"), "Corbin Eldrin", 0, pages, false));
        return book;
    }

    public static int testClosestStructure(CommandSourceStack source) {
        ServerPlayer player = player(source);
        if (player == null) return 0;
        var nearest = findNearestStructure(player.blockPosition(), source.getLevel(), source);
        if (nearest == null) {
            source.sendFailure(Component.literal("No structures found nearby."));
            return 0;
        }
        BlockPos position = findNearestChestInStructure(source, nearest.getKey(), nearest.getValue());
        if (position == null) {
            source.sendFailure(Component.literal("No chests found in the structure."));
            return 0;
        }
        var entity = source.getLevel().getBlockEntity(position);
        if (!(entity instanceof ChestBlockEntity || entity instanceof BarrelBlockEntity)) {
            source.sendFailure(Component.literal("Block entity at " + position.toShortString() + " is not a chest or barrel."));
            return 0;
        }
        var container = (RandomizableContainerBlockEntity) entity;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            if (container.getItem(slot).isEmpty()) {
                container.setItem(slot, familyHistoryBook());
                container.setChanged();
                source.sendSystemMessage(Component.literal("Added book to chest at " + position.toShortString())
                        .withStyle(ChatFormatting.GOLD));
                return 1;
            }
        }
        source.sendFailure(Component.literal("Chest at " + position.toShortString() + " is full."));
        return 0;
    }

    private static List<Villager> villagers(ServerLevel level, ServerPlayer player, int radius) {
        AABB bounds = new AABB(player.blockPosition()).inflate(radius);
        return new ArrayList<>(level.getEntities(EntityTypes.VILLAGER, villager -> villager.isAlive()
                && !villager.isSpectator() && bounds.intersects(villager.getBoundingBox())));
    }

    public static int findNearVillagers(CommandSourceStack source) {
        ServerPlayer player = player(source);
        if (player == null) return 0;
        List<Villager> villagers = villagers(source.getLevel(), player, 160);
        villagers.sort(Comparator.comparingDouble(villager -> villager.distanceToSqr(player)));
        source.sendSuccess(() -> Component.literal("Found " + villagers.size() + " villagers nearby."), false);
        for (Villager villager : villagers) {
            BlockPos pos = villager.blockPosition();
            BlockPos origin = player.blockPosition();
            int distance = Math.abs(pos.getX() - origin.getX()) + Math.abs(pos.getY() - origin.getY())
                    + Math.abs(pos.getZ() - origin.getZ());
            String profession = villager.getVillagerData().profession().unwrapKey().orElseThrow().identifier().toString();
            String plot = VillagerManager.getVillagerPlotContextStatic(villager);
            source.sendSuccess(() -> Component.literal(" - " + villager.getName().getString() + " (" + profession
                    + ") [" + plot + "] d=" + distance), false);
            LOGGER.info("Villager name: " + villager.getName().getString() + ", UUID: " + villager.getUUID() + ", position: " + pos);
            if (villager.getName().getString().trim().equals("Alaina Fae")) {
                source.sendSystemMessage(Component.literal("DEBUG Found exact string Alaina Fae, Success!"));
            }
        }
        return villagers.size();
    }

    public static int renameVillager(CommandSourceStack source, String oldName, String newName) {
        ServerPlayer player = player(source);
        if (player == null) return 0;
        List<Villager> villagers = villagers(source.getLevel(), player, 320);
        source.sendSuccess(() -> Component.literal("Found " + villagers.size() + " villagers nearby."), false);
        int renamed = 0;
        for (Villager villager : villagers) {
            if (villager.getName().getString().trim().equals(oldName)) {
                villager.setCustomName(Component.literal(newName));
                source.sendSuccess(() -> Component.literal("Found villager " + oldName + ", renamed them to " + newName), false);
                renamed++;
            }
        }
        if (renamed == 0) source.sendSuccess(() -> Component.literal("Villager " + oldName + " not found."), false);
        return renamed;
    }

    public static int scanForIslandsAndLakes(CommandSourceStack source, int radius) {
        validateScan(radius, 8);
        ServerPlayer player = player(source);
        if (player == null) return 0;
        BlockPos start = player.chunkPosition().getMiddleBlockPosition(64).offset(-4, 0, -4);
        int[][] grid = buildLandWaterGrid(start, radius, source.getLevel(), 8);
        showGrid(grid, start, radius, 8);
        source.sendSystemMessage(Component.literal(findLakesAndIslandsInGrid(grid, start, radius, source, 8))
                .withStyle(ChatFormatting.GREEN));
        return 1;
    }

    static void validateScan(int radius, int stepSize) {
        if (radius < 1 || radius > 100) throw new IllegalArgumentException("Chunk radius must be between 1 and 100");
        if (stepSize < 1 || stepSize > radius * 16) {
            throw new IllegalArgumentException("Step size must be between 1 and the scan radius in blocks");
        }
    }

    private static int[][] buildLandWaterGrid(BlockPos start, int radius, ServerLevel level, int stepSize) {
        validateScan(radius, stepSize);
        int steps = radius * 16 / stepSize;
        int[][] grid = new int[2 * steps + 1][2 * steps + 1];
        var biomes = level.registryAccess().lookupOrThrow(Registries.BIOME);
        for (int z = -steps; z <= steps; z++) {
            for (int x = -steps; x <= steps; x++) {
                int blockX = start.getX() + x * stepSize;
                int blockZ = start.getZ() + z * stepSize;
                BlockPos pos = new BlockPos(blockX, level.getHeight(Heightmap.Types.MOTION_BLOCKING, blockX, blockZ) - 1, blockZ);
                String biome = Objects.requireNonNull(biomes.getKey(level.getBiome(pos).value()), "Unregistered biome").getPath();
                var surface = level.getBlockState(pos);
                var sea = level.getBlockState(new BlockPos(blockX, 62, blockZ));
                boolean water = surface.is(Blocks.WATER) || surface.is(Blocks.ICE) || sea.is(Blocks.WATER) || sea.is(Blocks.ICE);
                grid[z + steps][x + steps] = water ? 0 : biome.contains("plains") ? 2 : biome.contains("forest") ? 3 : 1;
            }
        }
        return grid;
    }

    private static void showGrid(int[][] grid, BlockPos start, int radius, int stepSize) {
        int steps = radius * 16 / stepSize;
        LOGGER.info("Grid data for islands and lakes with radius " + radius + " around " + start);
        StringBuilder header = new StringBuilder("    ");
        for (int x = 0; x < grid[0].length; x++) header.append(Math.floorDiv(start.getX() + (x - steps) * stepSize, 16)).append(' ');
        LOGGER.info(header.toString());
        for (int z = 0; z < grid.length; z++) {
            StringBuilder row = new StringBuilder(String.format("%3d ", Math.floorDiv(start.getZ() + (z - steps) * stepSize, 16)));
            for (int value : grid[z]) row.append(switch (value) { case 0 -> " W "; case 1 -> " L "; case 2 -> " P "; case 3 -> " F "; default -> " ? "; });
            LOGGER.info(row.toString());
        }
    }

    record Region(boolean touchesEdge, int size, boolean water, List<ChunkPos> chunks) {
        boolean reportable() { return !touchesEdge && chunks.size() >= 2; }
        boolean persistable() { return reportable() && (!water || chunks.size() > 2); }
    }

    /** Four-way connectivity groups all land colors together; edge regions are open, not enclosed. */
    static List<Region> regions(int[][] grid, BlockPos start, int radius, int stepSize) {
        validateScan(radius, stepSize);
        if (grid.length == 0 || grid[0] == null || grid[0].length == 0) {
            throw new IllegalArgumentException("Grid must not be empty");
        }
        int rows = grid.length;
        int cols = grid[0].length;
        for (int[] row : grid) {
            if (row == null || row.length != cols) throw new IllegalArgumentException("Grid must be rectangular");
            for (int value : row) if (value < 0 || value > 3) throw new IllegalArgumentException("Unknown grid value: " + value);
        }
        int steps = radius * 16 / stepSize;
        boolean[][] visited = new boolean[rows][cols];
        List<Region> regions = new ArrayList<>();
        for (int z = 0; z < rows; z++) {
            for (int x = 0; x < cols; x++) {
                if (visited[z][x]) continue;
                boolean water = grid[z][x] == 0;
                boolean edge = false;
                int size = 0;
                LinkedHashSet<ChunkPos> chunks = new LinkedHashSet<>();
                ArrayDeque<int[]> stack = new ArrayDeque<>();
                stack.push(new int[]{x, z});
                while (!stack.isEmpty()) {
                    int[] cell = stack.pop();
                    int cx = cell[0], cz = cell[1];
                    if (cx < 0 || cz < 0 || cx >= cols || cz >= rows) continue;
                    if (visited[cz][cx] || (grid[cz][cx] == 0) != water) continue;
                    visited[cz][cx] = true;
                    edge |= cx == 0 || cz == 0 || cx == cols - 1 || cz == rows - 1;
                    size++;
                    chunks.add(ChunkPos.containing(new BlockPos(start.getX() + (cx - steps) * stepSize, 64,
                            start.getZ() + (cz - steps) * stepSize)));
                    stack.push(new int[]{cx + 1, cz});
                    stack.push(new int[]{cx - 1, cz});
                    stack.push(new int[]{cx, cz + 1});
                    stack.push(new int[]{cx, cz - 1});
                }
                regions.add(new Region(edge, size, water, List.copyOf(chunks)));
            }
        }
        return List.copyOf(regions);
    }

    static boolean duplicateZone(Collection<GameZone> existing, GameZone candidate) {
        return existing.stream().anyMatch(zone -> zone.getCenter().equals(candidate.getCenter()));
    }

    public static String findLakesAndIslandsInGrid(int[][] grid, BlockPos start, int radius,
                                                 CommandSourceStack source, int stepSize) {
        List<Region> regions = regions(grid, start, radius, stepSize);
        int lakes = 0, islands = 0;
        for (Region region : regions) {
            if (!region.reportable()) continue;
            if (region.persistable()) {
                GameZone zone = GameZone.fromChunks(region.chunks(), region.water() ? GameZone.ZoneType.LAKE : GameZone.ZoneType.ISLAND);
                DataBase<UUID, GameZone> database = ModEvents.getGameZoneDatabase(source.getLevel());
                if (!duplicateZone(database.getValues(), zone)) database.putData(zone.getUuid(), zone);
            }
            int number = region.water() ? ++lakes : ++islands;
            BlockPos center = region.chunks().getFirst().getMiddleBlockPosition(64);
            source.sendSystemMessage(Component.literal((region.water() ? "Lake #" : "Island #") + number + " found at "
                            + center.toShortString() + " (size: " + region.size() + " chunks: " + region.chunks().size() + ")")
                    .withStyle(region.water() ? ChatFormatting.AQUA : ChatFormatting.GREEN)
                    .withStyle(style -> style.withClickEvent(new ClickEvent.RunCommand(
                            "/tp " + center.getX() + " " + center.getY() + " " + center.getZ()))));
            StringBuilder chunks = new StringBuilder();
            for (ChunkPos chunk : region.chunks()) chunks.append('(').append(chunk.x()).append(',').append(chunk.z()).append(") ");
            source.sendSystemMessage(Component.literal("  Chunks: " + chunks).withStyle(ChatFormatting.GRAY));
        }
        LOGGER.info("Number of lakes: " + lakes + "\nNumber of islands: " + islands);
        return "Number of lakes: " + lakes + "\nNumber of islands: " + islands;
    }
}
