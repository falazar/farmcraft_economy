package com.falazar.farmupcraft.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

public class ShowBiomesCommand {


    public static void register(CommandDispatcher<CommandSourceStack> pDispatcher) {
        // Define the base command "show"
        LiteralArgumentBuilder<CommandSourceStack> builder = Commands.literal("show")
                .requires(s -> s.hasPermission(0));  // Adjust permission as needed

        // Define the "biomes" sub-command with "pos" and "radius" arguments
        LiteralArgumentBuilder<CommandSourceStack> biomesBuilder = Commands.literal("biomes")
                .then(Commands.argument("pos", Vec3Argument.vec3())
                        .then(Commands.argument("radius", IntegerArgumentType.integer())
                                .executes(c -> showBiomes(c, Vec3Argument.getVec3(c, "pos"), IntegerArgumentType.getInteger(c, "radius")))
                        )
                )
                .requires(s -> s.hasPermission(2));  // Adjust permission as needed

        // Add the "biomes" sub-command to the "show" command
        builder.then(biomesBuilder);

        // Register the main "show" command with the dispatcher
        pDispatcher.register(builder);
    }


    public static int showBiomes(CommandContext<CommandSourceStack> c, Vec3 pos, int radius) {
        try {
            Entity nullableSummoner = c.getSource().getEntity();
            Player summoner = nullableSummoner instanceof Player ? (Player) nullableSummoner : c.getSource().getLevel().getNearestPlayer(pos.x(), pos.y(), pos.z(), 64, false);
            if (summoner != null) {
                Level level = summoner.level();
                Set<ResourceLocation> biomesInRadius = new HashSet<>();

                // Calculate the chunk boundaries based on the radius
                int chunkRadius = (int) Math.ceil(radius / 16.0);
                ChunkPos centerChunkPos = new ChunkPos(BlockPos.containing(pos));

                // Iterate over chunks within the radius
                for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
                    for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                        ChunkPos chunkPos = new ChunkPos(centerChunkPos.x + dx, centerChunkPos.z + dz);
                        ChunkAccess chunk = level.getChunk(chunkPos.x, chunkPos.z);

                        // Check all block positions in the chunk within the radius
                        for (int x = 0; x < 16; x++) {
                            for (int z = 0; z < 16; z++) {
                                BlockPos blockPos = chunk.getPos().getBlockAt(x, 0, z);
                                double distance = blockPos.distToCenterSqr(pos.x, 0, pos.z);

                                // Only consider blocks within the radius
                                if (distance <= radius * radius) {
                                    Biome biome = level.getBiome(blockPos).value();
                                    ResourceLocation biomeName = level.registryAccess().registryOrThrow(Registries.BIOME).getKey(biome);

                                    if (biomeName != null) {
                                        biomesInRadius.add(biomeName);
                                    }
                                }
                            }
                        }
                    }
                }

                // Build a response message
                if (!biomesInRadius.isEmpty()) {
                    MutableComponent response = Component.literal("Biomes within radius " + radius + ": ");
                    for (ResourceLocation biome : biomesInRadius) {
                        response = response.append(Component.literal(biome.toString() + " "));
                    }
                    MutableComponent finalResponse = response;
                    c.getSource().sendSuccess(() -> finalResponse, false);
                } else {
                    c.getSource().sendFailure(Component.literal("No biomes found within the given radius."));
                }
            } else {
                c.getSource().sendFailure(Component.literal("Player not found within 64 blocks."));
            }
        } catch (Exception ex) {
            c.getSource().sendFailure(Component.literal("Exception thrown - see log"));
            ex.printStackTrace();
        }
        return 0;
    }
}
