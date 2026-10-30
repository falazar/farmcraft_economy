package com.falazar.farmupcraft.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionSet;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class ScanCommandTest {
    @Test
    void fullLegacyCommandTreeAndRadiusLimitsAreRegistered(MinecraftServer server) throws Exception {
        assertNotNull(server.registryAccess());
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        ScanCommand.register(dispatcher);
        var scan = dispatcher.getRoot().getChild("scan");
        assertNotNull(scan);
        assertEquals(Set.of("islands", "point", "clear"),
                scan.getChildren().stream().map(node -> node.getName()).collect(Collectors.toSet()));
        assertNull(scan.getChild("islands").getCommand());
        var radius = assertInstanceOf(ArgumentCommandNode.class, scan.getChild("islands").getChild("chunkradius"));
        var integer = assertInstanceOf(IntegerArgumentType.class, radius.getType());
        assertEquals(1, integer.getMinimum());
        assertEquals(128, integer.getMaximum());
        assertNotNull(radius.getCommand());
        assertNotNull(scan.getChild("point").getCommand());
        assertNull(scan.getChild("point").getChild("x").getCommand());
        assertNotNull(scan.getChild("point").getChild("x").getChild("z").getCommand());
        assertNotNull(scan.getChild("clear").getCommand());

        // Vanilla's compilation context deliberately has no level; no fake world is needed.
        var operator = Commands.createCompilationContext(LevelBasedPermissionSet.GAMEMASTER);
        assertTrue(scan.getRequirement().test(operator));
        assertFalse(scan.getRequirement().test(Commands.createCompilationContext(PermissionSet.NO_PERMISSIONS)));
        assertFalse(scan.getRequirement().test(Commands.createCompilationContext(LevelBasedPermissionSet.MODERATOR)));
        for (String command : Set.of("scan islands 1", "scan islands 128", "scan point", "scan point -5 9", "scan clear")) {
            assertFalse(dispatcher.parse(command, operator).getReader().canRead(), command);
        }
        for (String command : Set.of("scan islands 0", "scan islands 129", "scan point invalid 9")) {
            assertFalse(dispatcher.parse(command, operator).getExceptions().isEmpty(), command);
        }
        assertEquals(0, dispatcher.execute("scan islands 1", operator), "Console cannot start player scans");
        assertEquals(0, dispatcher.execute("scan point", operator), "Console cannot start point scans");
    }
}
