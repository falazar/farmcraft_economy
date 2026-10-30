package com.falazar.farmupcraft.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class BuildCommandTest {
    @Test
    void allPlayersCanParseCommandsButConsoleCannotStartSelection(MinecraftServer server) throws Exception {
        var dispatcher = new CommandDispatcher<CommandSourceStack>();
        BuildCommand.register(dispatcher);
        var feedback = new MarketCommandTest.Feedback();
        var source = MarketCommandTest.source(server, PermissionSet.NO_PERMISSIONS, feedback);
        for (String command : List.of("build pull_wall 1", "build pull_wall 10", "build push_building 10",
                "build confirm", "build cancel")) {
            var parsed = dispatcher.parse(command, source);
            assertTrue(parsed.getExceptions().isEmpty(), command);
            assertEquals("", parsed.getReader().getRemaining(), command);
            assertNotNull(parsed.getContext().getCommand(), command);
            assertEquals(0, dispatcher.execute(command, source));
        }
        assertTrue(feedback.messages.contains("Only players can use /build."));
        for (String command : List.of("build pull_wall 0", "build push_building -1",
                "build push_building 11", "build pull_wall 11")) {
            assertFalse(dispatcher.parse(command, source).getExceptions().isEmpty(), command);
        }
    }

    @Test
    void buildingBoundaryStopsOnlyOnDirtPlainStoneOrAir(MinecraftServer server) {
        assertNotNull(server.registryAccess());
        for (var block : List.of(Blocks.DIRT, Blocks.STONE, Blocks.AIR, Blocks.CAVE_AIR)) {
            assertTrue(BuildCommand.endsBuilding(block.defaultBlockState()));
        }
        for (var block : List.of(Blocks.COBBLESTONE, Blocks.OAK_PLANKS, Blocks.STONE_BRICKS, Blocks.GRASS_BLOCK)) {
            assertFalse(BuildCommand.endsBuilding(block.defaultBlockState()));
        }
    }
}
