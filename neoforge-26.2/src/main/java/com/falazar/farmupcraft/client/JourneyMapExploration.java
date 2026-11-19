package com.falazar.farmupcraft.client;

import com.falazar.farmupcraft.data.ExplorationStats.Area;
import com.falazar.farmupcraft.database.message.ExplorationRequestPacket;
import com.falazar.farmupcraft.database.message.ExplorationResultPacket;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.regex.Pattern;

public final class JourneyMapExploration {
    private static final Pattern TILE = Pattern.compile("(-?\\d+),(-?\\d+)\\.png");

    private JourneyMapExploration() {}

    public static void handle(ExplorationRequestPacket request, IPayloadContext context) {
        if (!ModList.get().isLoaded("journeymap")) {
            context.reply(new ExplorationResultPacket(request.requestId(), false, 0));
            return;
        }
        var minecraft = Minecraft.getInstance();
        var connection = minecraft.getConnection();
        final Path directory;
        try {
            if (JourneyMapIntegration.getApi() == null) {
                throw new IllegalStateException("JourneyMap API is not ready");
            }
            // The public API only exposes addon storage, not terrain tiles.
            Class<?> files = Class.forName("journeymap.client.io.FileHandler");
            File world = (File) files.getMethod("getJMWorldDir", Minecraft.class).invoke(null, minecraft);
            Path dimension = (Path) files.getMethod("getDimPath", File.class, ResourceKey.class)
                    .invoke(null, world, Level.OVERWORLD);
            directory = dimension.resolve("day");
        } catch (ReflectiveOperationException | IllegalStateException | LinkageError error) {
            LogUtils.getLogger().warn("Cannot locate JourneyMap terrain for exploration reporting", error);
            context.reply(new ExplorationResultPacket(request.requestId(), false, 0));
            return;
        }
        CompletableFuture.supplyAsync(() -> {
            try {
                return countChunks(directory, request.area());
            } catch (IOException error) {
                throw new CompletionException(error);
            }
        }).whenComplete((count, error) -> minecraft.execute(() -> {
            if (error != null) {
                LogUtils.getLogger().warn("Failed to count JourneyMap exploration", error);
            }
            // Never report another world's map after a disconnect/reconnect.
            if (connection != null && minecraft.getConnection() == connection) {
                context.reply(new ExplorationResultPacket(request.requestId(), error == null,
                        error == null ? count : 0));
            }
        }));
    }

    static long countChunks(Path directory, Area area) throws IOException {
        long count = 0;
        try (var tiles = Files.newDirectoryStream(directory, "*.png")) {
            for (Path tile : tiles) {
                var match = TILE.matcher(tile.getFileName().toString());
                if (!match.matches()) continue;
                final long originX;
                final long originZ;
                try {
                    originX = (long) Integer.parseInt(match.group(1)) * 512;
                    originZ = (long) Integer.parseInt(match.group(2)) * 512;
                } catch (NumberFormatException error) {
                    throw new IOException("Invalid JourneyMap tile coordinates: " + tile, error);
                }
                if (originX >= area.maxX() || originZ >= area.maxZ()
                        || originX + 512 <= area.minX() || originZ + 512 <= area.minZ()) continue;
                var image = ImageIO.read(tile.toFile());
                if (image == null || image.getWidth() != 512 || image.getHeight() != 512) {
                    throw new IOException("Invalid JourneyMap surface tile: " + tile);
                }
                int minX = (int) Math.max(0, area.minX() - originX);
                int minZ = (int) Math.max(0, area.minZ() - originZ);
                int maxX = (int) Math.min(512, area.maxX() - originX);
                int maxZ = (int) Math.min(512, area.maxZ() - originZ);
                for (int z = minZ / 16 * 16; z < maxZ; z += 16) {
                    for (int x = minX / 16 * 16; x < maxX; x += 16) {
                        boolean mapped = false;
                        for (int pz = Math.max(z, minZ); pz < Math.min(z + 16, maxZ) && !mapped; pz++) {
                            for (int px = Math.max(x, minX); px < Math.min(x + 16, maxX); px++) {
                                if ((image.getRGB(px, pz) >>> 24) != 0) {
                                    mapped = true;
                                    break;
                                }
                            }
                        }
                        if (mapped) count++;
                    }
                }
            }
        }
        return count;
    }
}
