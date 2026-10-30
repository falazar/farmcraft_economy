package com.falazar.farmupcraft.command;

import com.falazar.farmupcraft.data.WorldData;
import com.falazar.farmupcraft.database.DataBase;
import com.falazar.farmupcraft.database.serializers.CodecDataSerializer;
import com.falazar.farmupcraft.database.serializers.IntDataSerializer;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionSet;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class RootCommandsTest {
    private static CommandDispatcher<CommandSourceStack> dispatcher() {
        var dispatcher = new CommandDispatcher<CommandSourceStack>();
        FarmCraftCommand.register(dispatcher);
        ManagersCommand.register(dispatcher);
        AiCommand.register(dispatcher);
        return dispatcher;
    }

    private static Set<String> children(CommandNode<CommandSourceStack> node) {
        return node.getChildren().stream().map(CommandNode::getName).collect(Collectors.toSet());
    }

    @Test
    void everyLegacyRootBranchAndPermissionIsRetained(MinecraftServer server) throws Exception {
        assertNotNull(server.registryAccess());
        var dispatcher = dispatcher();
        var root = dispatcher.getRoot().getChild("farmcraft");
        assertEquals(Set.of("scheduler", "runchecks", "map", "lan", "admin", "help"), children(root));
        var map = root.getChild("map");
        assertEquals(Set.of("waypoint", "chunk", "here", "village", "clearwaypoints",
                "removenearestwaypoint", "clear"), children(map));
        assertNotNull(map.getChild("village").getCommand());
        assertNotNull(map.getChild("village").getChild("clear").getCommand());
        var settings = root.getChild("admin").getChild("settings");
        assertEquals(Set.of("useFarms", "useBiomeCropRules", "lycaniteLightBlock", "mobTorchBreaking"), children(settings));
        assertNotNull(settings.getCommand());
        var guest = Commands.createCompilationContext(PermissionSet.NO_PERMISSIONS);
        var moderator = Commands.createCompilationContext(LevelBasedPermissionSet.MODERATOR);
        var operator = Commands.createCompilationContext(LevelBasedPermissionSet.GAMEMASTER);
        assertTrue(root.canUse(guest));
        assertTrue(root.getChild("scheduler").canUse(guest));
        assertTrue(root.getChild("lan").canUse(guest));
        for (String admin : Set.of("map", "admin", "runchecks")) {
            assertFalse(root.getChild(admin).canUse(guest));
            assertFalse(root.getChild(admin).canUse(moderator));
            assertTrue(root.getChild(admin).canUse(operator));
        }
        assertTrue(dispatcher.getRoot().getChild("frecipe").canUse(guest));
        for (String command : List.of("farmcraft scheduler", "farmcraft runchecks", "farmcraft lan",
                "farmcraft admin settings", "farmcraft admin settings useFarms true",
                "farmcraft admin settings useBiomeCropRules false", "farmcraft admin settings lycaniteLightBlock true",
                "farmcraft map waypoint -1 64 7 Home sweet home", "farmcraft map chunk -3 8 A label",
                "farmcraft map here Here label", "farmcraft map village", "farmcraft map village clear",
                "farmcraft map clearwaypoints", "farmcraft map clearwaypoints 200",
                "farmcraft map clearwaypoints test", "farmcraft map clearwaypoints test 1",
                "farmcraft map removenearestwaypoint", "farmcraft map clear", "frecipe minecraft:bread")) {
            var parsed = dispatcher.parse(command, operator);
            assertFalse(parsed.getReader().canRead(), command);
            assertNotNull(parsed.getContext().getCommand(), command);
        }
        var chunks = (ArgumentCommandNode<?, ?>) map.getChild("clearwaypoints").getChild("chunks");
        var range = assertInstanceOf(IntegerArgumentType.class, chunks.getType());
        assertEquals(1, range.getMinimum());
        assertEquals(200, range.getMaximum());
        for (String invalid : List.of("farmcraft map clearwaypoints 0",
                "farmcraft map clearwaypoints 201", "farmcraft map clearwaypoints test -1",
                "farmcraft admin settings useFarms maybe")) {
            assertFalse(dispatcher.parse(invalid, operator).getExceptions().isEmpty(), invalid);
        }
        for (String playerOnly : List.of("farmcraft lan", "frecipe minecraft:bread",
                "farmcraft map waypoint 1 2 3 Home", "farmcraft map chunk 1 2 test",
                "farmcraft map here test", "farmcraft map village", "farmcraft map village clear",
                "farmcraft map clearwaypoints", "farmcraft map removenearestwaypoint", "farmcraft map clear")) {
            assertEquals(0, dispatcher.execute(playerOnly, operator), playerOnly);
        }
        assertEquals(0, FarmCraftCommand.showNearestStructureDetail(operator, 1, true));
        assertEquals(0, FarmCraftCommand.setupSpecialChestCommand(operator));
    }

    @Test
    void managerEnumArgumentsAndLegacyAiAccessArePreservedWithoutCallingAi(MinecraftServer server) throws Exception {
        assertNotNull(server.registryAccess());
        var dispatcher = dispatcher();
        var managers = dispatcher.getRoot().getChild("managers");
        var guest = Commands.createCompilationContext(PermissionSet.NO_PERMISSIONS);
        var operator = Commands.createCompilationContext(LevelBasedPermissionSet.GAMEMASTER);
        assertEquals(Set.of("clear", "repopulate"), children(managers));
        assertFalse(managers.canUse(guest));
        assertFalse(managers.canUse(Commands.createCompilationContext(LevelBasedPermissionSet.MODERATOR)));
        assertTrue(managers.canUse(operator));
        for (String command : List.of("managers clear BIOME_RULES", "managers repopulate BIOME_RULES",
                "managers clear NONE", "managers repopulate NONE")) {
            assertFalse(dispatcher.parse(command, operator).getReader().canRead(), command);
        }
        assertEquals(0, dispatcher.execute("managers clear NONE", operator));
        assertEquals(0, dispatcher.execute("managers repopulate NONE", operator));
        assertFalse(dispatcher.parse("managers clear not_a_manager", operator).getExceptions().isEmpty());
        assertEquals(ManagersCommand.ManagerType.BIOME_RULES, ManagersCommand.ManagerType.getByName("BiOmE_RuLeS"));
        assertEquals(ManagersCommand.ManagerType.NONE, ManagersCommand.ManagerType.getByName("unknown"));
        var ai = dispatcher.getRoot().getChild("ai");
        assertTrue(ai.canUse(guest), "The legacy AI command is deliberately not admin-gated");
        assertEquals(Set.of("test"), children(ai));
        var parsed = dispatcher.parse("ai test A multi word prompt", guest);
        assertFalse(parsed.getReader().canRead());
        assertNotNull(parsed.getContext().getCommand());
    }

    @Test
    void adminSettingsPersistAllFlagsAndRejectUnknownNames(MinecraftServer server) {
        try (var database = new DataBase<>("admin_command_test", new IntDataSerializer(),
                new CodecDataSerializer<>(WorldData.CODEC), false, 0, false, server.registryAccess())) {
            for (String setting : List.of("useFarms", "useBiomeCropRules", "lycaniteLightBlock", "mobTorchBreaking")) {
                assertTrue(FarmCraftCommand.setAdminSetting(database, setting, false));
            }
            WorldData data = database.getData(0);
            assertFalse(data.isUseVillageFarms());
            assertFalse(data.isUseBiomeCropRules());
            assertFalse(data.isLycaniteLightBlock());
            assertFalse(data.isMobTorchBreaking());
            assertTrue(database.isDirty());
            assertEquals("--- FarmCraft Admin Settings ---\nuseFarms: false\nuseBiomeCropRules: false\n"
                    + "lycaniteLightBlock: false\nmobTorchBreaking: false\n", FarmCraftCommand.adminSettings(data).getString());
            CompoundTag before = database.save(new CompoundTag());
            assertFalse(FarmCraftCommand.setAdminSetting(database, "notASetting", true));
            assertEquals(before, database.save(new CompoundTag()));
            for (String setting : List.of("useFarms", "useBiomeCropRules", "lycaniteLightBlock", "mobTorchBreaking")) {
                assertTrue(FarmCraftCommand.setAdminSetting(database, setting, true));
            }
            assertTrue(data.isUseVillageFarms());
            assertTrue(data.isUseBiomeCropRules());
            assertTrue(data.isLycaniteLightBlock());
            try (var restored = new DataBase<>("admin_command_test", new IntDataSerializer(),
                    new CodecDataSerializer<>(WorldData.CODEC), false, 0, false, server.registryAccess())) {
                restored.load(database.save(new CompoundTag()), server.registryAccess());
                assertTrue(restored.getData(0).isUseVillageFarms());
                assertTrue(restored.getData(0).isUseBiomeCropRules());
                assertTrue(restored.getData(0).isLycaniteLightBlock());
                assertTrue(restored.getData(0).isMobTorchBreaking());
            }
        }
    }

    @Test
    void aiCompletionAndFailureAreExplicitAndMarshalledWithoutExternalCalls(MinecraftServer server) {
        assertNotNull(server.registryAccess());
        var feedback = new Feedback();
        var source = Commands.createCompilationContext(PermissionSet.NO_PERMISSIONS).withSource(feedback);
        List<Runnable> callbacks = new ArrayList<>();
        var response = new CompletableFuture<String>();
        assertEquals(1, AiCommand.runTest(source, "test prompt", prompt -> {
            assertEquals("test prompt", prompt);
            return response;
        }, callbacks::add));
        assertEquals(List.of("[AI] Thinking..."), feedback.messages);
        response.complete("Fixture answer");
        assertEquals(1, callbacks.size());
        assertEquals(1, feedback.messages.size(), "Completion must wait for the server-thread callback");
        callbacks.removeFirst().run();
        assertEquals(List.of("[AI] Thinking...", "Fixture answer"), feedback.messages);
        AiCommand.runTest(source, "failure", prompt -> CompletableFuture.failedFuture(
                new CompletionException(new IOException("fixture unavailable"))), callbacks::add);
        assertEquals(1, callbacks.size());
        callbacks.removeFirst().run();
        assertEquals("[AI Error] fixture unavailable", feedback.messages.getLast());
        assertEquals(0, AiCommand.runTest(source, "rejected", prompt -> {
            throw new RejectedExecutionException("fixture stopped");
        }, callbacks::add));
        assertEquals("[AI Error] fixture stopped", feedback.messages.getLast());
    }

    private static class Feedback implements CommandSource {
        final List<String> messages = new ArrayList<>();
        @Override public void sendSystemMessage(Component component) { messages.add(component.getString()); }
        @Override public boolean acceptsSuccess() { return true; }
        @Override public boolean acceptsFailure() { return true; }
        @Override public boolean shouldInformAdmins() { return false; }
    }
}
