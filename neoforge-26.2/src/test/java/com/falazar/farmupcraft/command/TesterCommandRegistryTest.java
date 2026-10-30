package com.falazar.farmupcraft.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.Items;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class TesterCommandRegistryTest {
    // The FML fixture bootstraps vanilla commands and registries but deliberately has no ServerLevel.
    @Test
    void completeZoneTreeRetainsPublicAccessAndQuotedNames(MinecraftServer server) {
        assertNotNull(server.registryAccess());
        var dispatcher = new CommandDispatcher<CommandSourceStack>();
        ZoneCommand.register(dispatcher);
        var source = Commands.createCompilationContext(permission -> false);
        var root = dispatcher.getRoot().getChild("zone");
        assertEquals(2, root.getChildren().size());
        assertTrue(root.canUse(source));
        assertTrue(root.getChild("list").canUse(source));
        assertTrue(root.getChild("rename").canUse(source));
        assertNotNull(root.getChild("list").getCommand());
        assertNotNull(root.getChild("list").getChild("type").getCommand());
        assertNotNull(root.getChild("rename").getChild("oldName").getChild("newName").getCommand());
        var parsed = dispatcher.parse("zone rename \"Old Name\" \"New Name\"", source);
        assertFalse(parsed.getReader().canRead());
        assertTrue(parsed.getExceptions().isEmpty());
        assertNotNull(parsed.getContext().getCommand());
        assertTrue(root.canUse(Commands.createCompilationContext(PermissionSet.ALL_PERMISSIONS)));
    }

    @Test
    void testerRegistersEveryToolWithLegacyPermissionBoundariesAndDistanceLimits(MinecraftServer server) {
        assertNotNull(server.registryAccess());
        var dispatcher = new CommandDispatcher<CommandSourceStack>();
        TesterCommand.register(dispatcher);
        var root = dispatcher.getRoot().getChild("tester");
        assertEquals(Set.of("islandsAndLakes", "findvillagers", "renamevillager", "findneareststructure",
                        "testcloseststructure", "findchests"),
                root.getChildren().stream().map(node -> node.getName()).collect(Collectors.toSet()));
        var regular = Commands.createCompilationContext(permission -> false);
        var moderator = Commands.createCompilationContext(LevelBasedPermissionSet.MODERATOR);
        var gamemaster = Commands.createCompilationContext(LevelBasedPermissionSet.GAMEMASTER);
        assertTrue(root.canUse(regular));
        for (String name : List.of("islandsAndLakes", "findvillagers")) {
            assertTrue(root.getChild(name).canUse(regular));
            assertNotNull(root.getChild(name).getCommand());
        }
        for (String name : List.of("renamevillager", "findneareststructure", "testcloseststructure", "findchests")) {
            assertFalse(root.getChild(name).canUse(regular));
            assertFalse(root.getChild(name).canUse(moderator));
            assertTrue(root.getChild(name).canUse(gamemaster));
        }
        var argument = assertInstanceOf(ArgumentCommandNode.class,
                root.getChild("islandsAndLakes").getChild("distance"));
        var distance = assertInstanceOf(IntegerArgumentType.class, argument.getType());
        assertEquals(1, distance.getMinimum());
        assertEquals(100, distance.getMaximum());
        for (String command : List.of("tester islandsAndLakes", "tester islandsAndLakes 1",
                "tester islandsAndLakes 100", "tester renamevillager \"Old Name\" \"New Name\"",
                "tester findneareststructure", "tester testcloseststructure", "tester findchests", "tester findvillagers")) {
            var parsed = dispatcher.parse(command, gamemaster);
            assertFalse(parsed.getReader().canRead(), command);
            assertTrue(parsed.getExceptions().isEmpty(), command);
            assertNotNull(parsed.getContext().getCommand(), command);
        }
        for (String command : List.of("tester islandsAndLakes 0", "tester islandsAndLakes 101")) {
            assertFalse(dispatcher.parse(command, gamemaster).getExceptions().isEmpty(), command);
        }
    }

    @Test
    void realRegistriesResolveVillagerSelectorAndWrittenBookComponentsWithoutAWorld(MinecraftServer server) {
        assertSame(EntityTypes.VILLAGER, server.registryAccess().lookupOrThrow(Registries.ENTITY_TYPE)
                .getValue(Identifier.parse("minecraft:villager")));
        var book = TesterCommand.familyHistoryBook();
        assertSame(Items.WRITTEN_BOOK, book.getItem());
        assertSame(book.getItem(), server.registryAccess().lookupOrThrow(Registries.ITEM)
                .getValue(Identifier.parse("minecraft:written_book")));
        assertEquals(1, book.getCount());
        var content = book.get(DataComponents.WRITTEN_BOOK_CONTENT);
        assertNotNull(content);
        assertEquals("Family History", content.title().raw());
        assertEquals("Corbin Eldrin", content.author());
        assertEquals(0, content.generation());
        assertFalse(content.resolved());
        assertEquals(3, content.pages().size());
        assertTrue(content.pages().getFirst().raw().getString().startsWith("We, the Eldrin Family,"));
        assertTrue(content.pages().get(1).raw().getString().startsWith("The mystic lake offers solace,"));
        assertTrue(content.pages().getLast().raw().getString().startsWith("This book is a testament not of glory,"));
    }
}
