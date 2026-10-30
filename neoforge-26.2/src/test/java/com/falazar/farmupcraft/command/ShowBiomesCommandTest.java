package com.falazar.farmupcraft.command;

import com.falazar.farmupcraft.CropsManager;
import com.falazar.farmupcraft.saveddata.BiomeRulesManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Items;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Set;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class ShowBiomesCommandTest {
    @Test
    void biomeChatUsesDistinctSortedRegisteredBiomeNamesAndHandlesNoPlayer(MinecraftServer server) {
        var biomes = server.registryAccess().lookupOrThrow(Registries.BIOME);
        var plains = biomes.getOrThrow(ResourceKey.create(Registries.BIOME, Identifier.parse("minecraft:plains")));
        var desert = biomes.getOrThrow(ResourceKey.create(Registries.BIOME, Identifier.parse("minecraft:desert")));
        BiomeRulesManager manager = new BiomeRulesManager();
        manager.setItemBiomeList(Items.CARROT, plains);
        manager.setItemBiomeList(Items.CARROT, desert);
        manager.setItemBiomeList(Items.CARROT, plains);
        List<String> expected = List.of(plains, desert).stream()
                .map(biome -> Component.translatable(CropsManager.getBiomeLangKey(biome.unwrapKey().orElseThrow().identifier())).getString())
                .sorted().toList();
        assertEquals(String.join(", ", expected), ShowBiomesCommand.getBiomeCropsChat(null, manager, Items.CARROT).getString());
        assertEquals("None", ShowBiomesCommand.getBiomeCropsChat(null, manager, Items.POTATO).getString());
    }

    @Test
    void commandTreeRetainsAllThreeBranchesAndRequiredArguments(MinecraftServer server) {
        assertNotNull(server.registryAccess());
        var dispatcher = new CommandDispatcher<CommandSourceStack>();
        ShowBiomesCommand.register(dispatcher);
        var show = dispatcher.getRoot().getChild("show");
        assertEquals(Set.of("biomes", "cropbiomes", "biomecrops", "plotcrops"),
                show.getChildren().stream().map(node -> node.getName()).collect(Collectors.toSet()));
        assertNotNull(show.getChild("cropbiomes").getChild("crop").getCommand());
        assertNotNull(show.getChild("biomecrops").getChild("biome").getCommand());
        assertNotNull(show.getChild("biomecrops").getCommand());
        var radius = assertInstanceOf(ArgumentCommandNode.class, show.getChild("biomes").getChild("pos").getChild("radius"));
        assertNotNull(radius.getCommand());
        assertEquals(0, assertInstanceOf(IntegerArgumentType.class, radius.getType()).getMinimum());
    }

    @Test
    void defaultBiomeLookupRejectsConsoleWithExplicitFeedback(MinecraftServer server) throws Exception {
        var dispatcher = new CommandDispatcher<CommandSourceStack>();
        ShowBiomesCommand.register(dispatcher);
        var feedback = new MarketCommandTest.Feedback();
        var source = MarketCommandTest.source(server,
                net.minecraft.server.permissions.PermissionSet.NO_PERMISSIONS, feedback);
        assertEquals(0, dispatcher.execute("show biomecrops", source));
        assertTrue(feedback.messages.contains("Only players can use /show biomecrops without a biome."));
    }

    @Test
    void cropLookupPreservesPamFirstAndVanillaSeedAliasesWithExplicitNamespacedSupport(MinecraftServer server) {
        assertNotNull(server.registryAccess());
        assertSame(BuiltInRegistries.ITEM.getValue(Identifier.parse("pamhc2crops:onionitem")),
                ShowBiomesCommand.resolveCrop("onion"));
        assertNotSame(Items.AIR, ShowBiomesCommand.resolveCrop("onion"));
        assertSame(Items.WHEAT_SEEDS, ShowBiomesCommand.resolveCrop("wheat"));
        assertSame(Items.BEETROOT_SEEDS, ShowBiomesCommand.resolveCrop("beetroot"));
        assertSame(Items.CARROT, ShowBiomesCommand.resolveCrop("carrot"));
        assertSame(Items.POTATO, ShowBiomesCommand.resolveCrop("minecraft:potato"));
        assertSame(Items.AIR, ShowBiomesCommand.resolveCrop("missing_crop"));
        assertSame(Items.AIR, ShowBiomesCommand.resolveCrop("Not an identifier!"));
    }
}
