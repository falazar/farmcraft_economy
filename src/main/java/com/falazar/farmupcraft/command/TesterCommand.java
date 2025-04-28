package com.falazar.farmupcraft.command;

import com.falazar.farmupcraft.data.PlayerData;
import com.falazar.farmupcraft.data.VillageData;
import com.falazar.farmupcraft.database.DataBase;
import com.falazar.farmupcraft.events.ModEvents;
import com.falazar.farmupcraft.util.CustomLogger;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.*;

/**
 * TestCommand is a command class that provides various test commands for debugging and development purposes.
 * It includes commands to show plot information, player data, and other test functionalities.
 */
public class TesterCommand {
    public static final CustomLogger LOGGER = new CustomLogger(TesterCommand.class.getSimpleName());

    public static void register(CommandDispatcher<CommandSourceStack> pDispatcher) {
        // Define the base command "test"
        LiteralArgumentBuilder<CommandSourceStack> builder = Commands.literal("tester");

        // Some test commands looking for....

        // Find islands/lakes nearby
        // Show biomes for single chunk (can save to plot and farm)
        // Show all biomes in a village (move to that command)
        // Find all villagers ina village (or near area?)
        // -- See if we can get a villagers name.
        // Find all nearby Structures - ruins and such.
        // Find all nearby Open caves, at surface.
        // Find all below ground caves.

        // Define the "villagebiomes" sub-command
        LiteralArgumentBuilder<CommandSourceStack> villageBiomesBuilder = Commands.literal("villagebiomes")
                .executes(context -> {
                    return showVillageBiomes(context.getSource());
                });
        builder.then(villageBiomesBuilder);

        // Define the "islandsAndLakes" sub-command
        LiteralArgumentBuilder<CommandSourceStack> islandsBuilder = Commands.literal("islandsAndLakes")
                .executes(context -> {
                    // Default distance of 10 if no argument is provided
                    return showIslandsAndLakes(context.getSource(), 10);
                })
                .then(Commands.argument("distance", IntegerArgumentType.integer(1, 100)) // Add a distance argument with a range
                        .executes(context -> {
                            int distance = IntegerArgumentType.getInteger(context, "distance"); // Retrieve the distance value
                            return showIslandsAndLakes(context.getSource(), distance); // Pass the distance to the method
                        })
                );
        builder.then(islandsBuilder);

        // Register the main command with the dispatcher
        pDispatcher.register(builder);
    }

    public static int showIslandsAndLakes(CommandSourceStack source, int distance) {
        // STEP 1: Get current location of the player.
        Entity nullableSummoner = source.getEntity();
        Player playerSource = nullableSummoner instanceof Player ? (Player) nullableSummoner : null;

        ChunkPos chunkPos = playerSource.chunkPosition();
        LOGGER.info("DEBUG ChunkPos for player is " + chunkPos);

        // Create a double array of ints variable size
        // This will be a grid of 1s and 0s for land and water.
        // The size of the grid is 2 * distance + 1
        int[][] grid = new int[2 * distance + 1][2 * distance + 1];

        // STEP 2: Loop in a square around the player X distance.
        for (int z = -distance; z <= distance; z++) {
            LOGGER.info("\nDEBUG z=" + z);
        for (int x = -distance; x <= distance; x++) {
                // Get the chunk position.
                ChunkPos chunk = new ChunkPos(chunkPos.x + x, chunkPos.z + z);
//                LOGGER.info("DEBUG ChunkPos for player is " + chunk);
                // Get the chunk data.
                ServerLevel level = (ServerLevel) source.getLevel();

                // STEP 3: Get the biome for each chunk.
                // Get the biome for the chunk center.
                BlockPos blockPos = chunk.getMiddleBlockPosition(64); // default height notice.

                // Get height at that position.
                int height = level.getHeight(Heightmap.Types.MOTION_BLOCKING, blockPos.getX(), blockPos.getZ()) - 1;
                blockPos = new BlockPos(blockPos.getX(), height, blockPos.getZ());

                Biome biome = level.getBiome(blockPos).value();
                ResourceLocation biomeName = level.registryAccess().registryOrThrow(Registries.BIOME).getKey(biome);
                if (biomeName == null) {
                    LOGGER.info("Error: Biome name is null for chunk " + chunk);
                    continue;
                }
                LOGGER.info("DEBUG Biome name for chunk " + chunk + " is " + biomeName);
                // TODO Can map show chunks???

                // STEP 4: Mark grid data 1 for land types and 0 for water types.
                boolean isWater = biomeName.getPath().contains("ocean") || biomeName.getPath().contains("river");

                // Check for actual water block also.
                if (!isWater) {
                    // If water or ice block, it is water.
                    final BlockState blockState = level.getBlockState(blockPos);
//                    LOGGER.info("DEBUG BlockState for blockPos " + blockPos.toShortString() + " is " + blockState);

                    if (blockState.is(Blocks.WATER) || blockState.is(Blocks.ICE)) {
//                        LOGGER.info("DEBUG BlockState for chunk " + chunk.toString() + " is WATER WE FOUND = " + blockState);
                        isWater = true;
                    }
                }

                if (isWater) {
                    grid[x + distance][z + distance] = 0; // set water
                } else {
                    // mark 2 for plains
                    // mark 3 for forest
                    // mark 1 for rest
                    if (biomeName.getPath().contains("plains")) {
                        grid[x + distance][z + distance] = 2; // plains
                    } else if (biomeName.getPath().contains("forest")) {
                        grid[x + distance][z + distance] = 3; // forest
                    } else {
                        // mark 1 for all other land types
                        grid[x + distance][z + distance] = 1; // land
                    }
                }

            }
        }

        // STEP 5: Then show the grid (for debugging)
        showGrid(grid, chunkPos, distance);


        // STEP 6: Algorithm to look for lakes and islands with this grid data.
        // floodfill fun!
        // Loop over the grid and find lakes and islands.
        // A lake is a 0 surrounded by 1s.
        // An island is a 1 surrounded by 0s.
        String answer = findLakesAndIslands(grid, chunkPos, distance);


        // STEP 7: Show the list of lakes and islands found.
        // TODO later save these into the DB and allow naming.
        MutableComponent response = Component.literal(answer);
        source.sendSuccess(() -> response, false);

        return 0;
    }

    private static void showGrid(int[][] grid, ChunkPos chunkPos, int distance) {

        LOGGER.info("DEBUG Grid data for islands and lakes:");
        // Print the grid with column numbers (x values)
        StringBuilder header = new StringBuilder("   "); // Padding for row numbers
        for (int j = 0; j < grid[0].length; j++) {
            header.append(j - distance + chunkPos.x).append(" "); // Add column number
        }
        LOGGER.info(header.toString());

        // Print the grid with row numbers (z values)
        for (int i = 0; i < grid.length; i++) {
            StringBuilder row = new StringBuilder();
            row.append(i - distance + chunkPos.z).append(": "); // Add row number at the start
            for (int j = 0; j < grid[i].length; j++) {
                row.append(grid[j][i]).append(" "); // Access transposed grid values
            }
            LOGGER.info(row.toString());
        }
    }


    // Helper class to store flood-fill results
    private static class FloodFillResult {
        boolean touchesEdge;
        int size;

        FloodFillResult(boolean touchesEdge, int size) {
            this.touchesEdge = touchesEdge;
            this.size = size;
        }
    }

    private static int[][] copyGrid(int[][] grid) {
        int rows = grid.length;
        int cols = grid[0].length;
        int[][] gridCopy = new int[rows][cols];
        for (int i = 0; i < rows; i++) {
            System.arraycopy(grid[i], 0, gridCopy[i], 0, cols);
        }
        return gridCopy;
    }

    // NOTICE may not work perfectly since a lot of "lakes" are water in an actual plains biome!!!  may need to check water blocks instead????

    // Helper method to perform flood-fill and return the result
    private static FloodFillResult floodFillWithEdgeAndSize(int[][] grid, boolean[][] visited, int x, int z, int targetValue, int distance) {
        // Create a copy of the grid
        int[][] gridCopy = copyGrid(grid);
//        LOGGER.info("DEBUG FloodFillWithEdgeAndSize starting at x=" + x + ", z=" + z + ", targetValue=" + targetValue);

        int rows = grid.length;
        int cols = grid[0].length;
        boolean touchesEdge = false;
        int size = 0;

        // Use a stack for iterative flood-fill
        Stack<int[]> stack = new Stack<>();
        stack.push(new int[]{x, z});

        while (!stack.isEmpty()) {
            int[] cell = stack.pop();
            int cx = cell[0];
            int cz = cell[1];
//            LOGGER.info("DEBUG FloodFillWithEdgeAndSize processing cell at cx=" + cx + ", cy=" + cy);

            // Skip if out of bounds, already visited, or not matching the target value
            if (cx < 0 || cz < 0 || cx >= rows || cz >= cols) {
                touchesEdge = true;
                continue;
            }

            // If we are looking for water and hit any land
            if (targetValue == 0 && gridCopy[cx][cz] != 0) {
                continue;
            }
            // If we are looking for land and hit any water
            if (targetValue != 0 && gridCopy[cx][cz] == 0) {
                continue;
            }

            // If already done
            if (gridCopy[cx][cz] == -1) {
                continue;
            }

            // Mark as visited.
            visited[cx][cz] = true;

            // Mark the cell as processed by changing its value (optional)
            gridCopy[cx][cz] = -1; // Mark as processed (use a special value)

            // Increment the size of the region
            size++;

            // Add neighbors to the stack
            stack.push(new int[]{cx + 1, cz});
            stack.push(new int[]{cx - 1, cz});
            stack.push(new int[]{cx, cz + 1});
            stack.push(new int[]{cx, cz - 1});
        }

        // DEBUG show grid!
        // Uncomment this to see the text output of the grid to debug easily.
//        if (!touchesEdge) {
//            ChunkPos centerChunk = new ChunkPos(x, z);
//            showGrid(gridCopy, centerChunk, distance);
//        }

        // TODO Save and return all chunks on the island.
        // TODO get and add center of island blockpos.
        // TODO get and add biome list strings.
        // Note: Diagonals not exactly perfect, thin lands.
        // probbly count diagonals as well.

        return new FloodFillResult(touchesEdge, size);
    }

    public static String findLakesAndIslands(int[][] grid, ChunkPos centerChunk, int distance) {
        int rows = grid.length;
        int cols = grid[0].length;

        boolean[][] visited = new boolean[rows][cols];
        int lakeCount = 0;
        int islandCount = 0;

        LOGGER.info("DEBUG starting flood fill: ");

        for (int j = 0; j < cols; j++) {
            // Loop through the grid
            LOGGER.info("DEBUG flood fill at j=" + j);

            for (int i = 0; i < rows; i++) {
                // If the cell is not visited, start a flood-fill
                if (visited[i][j]) {
                    continue;
                }

                int targetValue = grid[i][j];
                int waterOrLand = targetValue == 0 ? 0 : 1; // 1 for land, 0 for water
                FloodFillResult result = floodFillWithEdgeAndSize(grid, visited, i, j, waterOrLand, distance);
                // TODO record each object here for later use

                if (!result.touchesEdge) {
                    ChunkPos chunkPos = new ChunkPos(centerChunk.x + (i - distance), centerChunk.z + (j - distance));
                    if (targetValue == 0) {
                        lakeCount++;
                        LOGGER.info("Lake #"+lakeCount+" found at ChunkPos " + chunkPos + " at x,z = " + chunkPos.getMiddleBlockPosition(64).toShortString() +
                                " with size " + result.size);
                    } else {
                        islandCount++;
                        LOGGER.info("Island #"+islandCount+" found at ChunkPos " + chunkPos + " at x,z = " + chunkPos.getMiddleBlockPosition(64).toShortString() +
                                " with size " + result.size);
                    }
                }

            }
        }

        // Output the results
        LOGGER.info("Number of lakes: " + lakeCount);
        LOGGER.info("Number of islands: " + islandCount);

        // Return that text to show to player.
        return "Number of lakes: " + lakeCount + "\n" +
                "Number of islands: " + islandCount;
    }


    // Method to loop over all chunks in a village, and get the center spot biome there.
    // Then unique and sort count the list.
    // Then show the list of biomes.
    // Two methods, one to get one to show, later can store on an object.
    // Uses current player village.
    public static int showVillageBiomes(CommandSourceStack source) {
        try {
            Entity nullableSummoner = source.getEntity();
            Player playerSource = nullableSummoner instanceof Player ? (Player) nullableSummoner : null;

            // Get the village data for the player.
            DataBase<UUID, PlayerData> playerDataDB = ModEvents.getPlayerDatabase();
            PlayerData playerData = playerDataDB.getData(playerSource.getUUID());
            if (playerData.getHomeVillageUUID() == null) {
                source.sendFailure(Component.literal("Player is not in a village right now."));
                return 0;
            }

            // Get the village data for the player.
            DataBase<UUID, VillageData> villageDataDB = ModEvents.getVillageDatabase(source.getLevel());
            VillageData villageData = villageDataDB.getData(playerData.getHomeVillageUUID());

            // Get the list of biomes in the village.
            Map<String, Integer> biomes = getVillageBiomes(villageData);

            // Show the list of biomes.
            MutableComponent response = Component.literal("Biomes in village: ");
            for (Map.Entry<String, Integer> entry : biomes.entrySet()) {
                String biome = entry.getKey();
                int count = entry.getValue();
                response.append(Component.literal(biome + " (" + count + "), \n"));
            }
            MutableComponent finalResponse = response;
            source.sendSuccess(() -> finalResponse, false);
        } catch (Exception ex) {
            source.sendFailure(Component.literal("Exception thrown - see log"));
            ex.printStackTrace();
        }
        return 0;
    }

    public static Map<String, Integer> getVillageBiomes(VillageData villageData) {
        Level level = Minecraft.getInstance().level;

        // Loop over each chunk in territory.
        // Get the biome for each chunk.
        List<ChunkPos> chunks = villageData.getClaimedChunks();
        // Count of each biome here.
        Map<String, Integer> biomeCounts = new HashMap<>();

        for (ChunkPos chunk : chunks) {
            // Get the biome for the chunk center.
            BlockPos blockPos = chunk.getMiddleBlockPosition(64); // default height notice.
            // Get height at that position.
            int height = level.getHeight(Heightmap.Types.MOTION_BLOCKING, blockPos.getX(), blockPos.getZ());
            blockPos = new BlockPos(blockPos.getX(), height, blockPos.getZ());

            // Update blockPos
            assert level != null;
            Biome biome = level.getBiome(blockPos).value();
            ResourceLocation biomeName = level.registryAccess().registryOrThrow(Registries.BIOME).getKey(biome);
            if (biomeName == null) {
                LOGGER.info("Biome name is null for chunk " + chunk);
                continue;
            }

            // Add to a count of biomes hash.
            String biomeString = biomeName.toString();
            LOGGER.info("DEBUG Biome name for chunk " + chunk + " is " + biomeString);
            if (biomeCounts.containsKey(biomeString)) {
                // Increment the count for this biome
                biomeCounts.put(biomeString, biomeCounts.get(biomeString) + 1);
            } else {
                // Add this biome to the map with an initial count of 1
                biomeCounts.put(biomeString, 1);
            }
        }

        // Sort the map by count in descending order
        Map<String, Integer> sortedBiomeCounts = biomeCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .collect(LinkedHashMap::new, (map, entry) -> map.put(entry.getKey(), entry.getValue()), LinkedHashMap::putAll);

        return sortedBiomeCounts;
    }
}
