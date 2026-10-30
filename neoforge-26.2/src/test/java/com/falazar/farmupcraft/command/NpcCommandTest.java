package com.falazar.farmupcraft.command;

import com.falazar.farmupcraft.data.NpcData;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** Registry-only fixture: no MinecraftServer parameter, worlds, mocked levels, or AI execution. */
@ExtendWith(EphemeralTestServerProvider.class)
class NpcCommandTest {
    @Test
    void modernGamemasterPermissionMatchesLegacyLevelTwoBoundary() {
        assertFalse(NpcCommand.hasAdminPermission(LevelBasedPermissionSet.MODERATOR));
        assertTrue(NpcCommand.hasAdminPermission(LevelBasedPermissionSet.GAMEMASTER));
        assertTrue(NpcCommand.hasAdminPermission(LevelBasedPermissionSet.ADMIN));
        assertTrue(NpcCommand.hasAdminPermission(LevelBasedPermissionSet.OWNER));
    }

    @Test
    void preservesEveryLegacyCommandAndTheAdminAliases() {
        var dispatcher = new CommandDispatcher<CommandSourceStack>();
        NpcCommand.register(dispatcher);
        var root = dispatcher.getRoot().getChild("npc");
        assertEquals(Set.of("create", "findnearestliving", "admin", "claim", "info", "list",
                "setdescription", "createskeleton", "createskleleton", "createcow", "initvillagers", "rename",
                "redoprofile", "cleanprofiles", "setkid", "unsetkid", "shownames", "hidenames",
                "showname", "hidename", "fillnotescache"),
                root.getChildren().stream().map(n -> n.getName()).collect(Collectors.toSet()));
        assertNotNull(root.getChild("admin").getChild("create").getChild("name").getCommand());
        assertNotNull(root.getChild("admin").getChild("findnearestliving").getCommand());
        assertNotNull(root.getChild("claim").getChild("name").getCommand());
        assertNotNull(root.getChild("claim").getChild("name").getChild("behavior").getCommand());
        assertNotNull(root.getChild("rename").getChild("oldName").getChild("newName").getCommand());
        assertNotNull(root.getChild("list").getCommand());
        assertNotNull(root.getChild("list").getChild("radius").getCommand());
        assertNotNull(root.getChild("fillnotescache").getChild("count").getCommand());
        assertNotNull(root.getChild("createskeleton").getChild("name").getCommand());
        assertNotNull(root.getChild("createskleleton").getChild("name").getCommand());
    }

    @Test
    void retriesFailuresBlankResponsesAndErrorSentinelsButNotSuccessfulProfiles() {
        assertTrue(NpcCommand.shouldRetry(null, null));
        assertTrue(NpcCommand.shouldRetry("", null));
        assertTrue(NpcCommand.shouldRetry(" \n\t", null));
        assertTrue(NpcCommand.shouldRetry("[AI Error] connection refused", null));
        assertTrue(NpcCommand.shouldRetry("prefix\n[AI Error] timeout", null));
        assertTrue(NpcCommand.shouldRetry("valid text", new IllegalStateException("offline")));
        assertFalse(NpcCommand.shouldRetry("A quiet farmer who loves the river.", null));
    }

    @Test
    void replenishesOnlyBelowThresholdAndWithoutConcurrentAutomaticRequest() {
        assertTrue(NpcCommand.shouldReplenish(0, false));
        assertTrue(NpcCommand.shouldReplenish(2, false));
        assertFalse(NpcCommand.shouldReplenish(3, false));
        assertFalse(NpcCommand.shouldReplenish(20, false));
        assertFalse(NpcCommand.shouldReplenish(0, true));
        assertFalse(NpcCommand.shouldReplenish(2, true));
    }

    @Test
    void queuedRequestsOwnImmutableAiContextAndPreserveRegenerationInputs() {
        var names = new ArrayList<>(List.of("Ada", "Cassey"));
        var biomes = new ArrayList<>(List.of("plains", "river"));
        var request = new NpcCommand.ProfileRequest("Ada", new UUID(0, 1), "farmer", names,
                "River Town", biomes, "ancient ruins", new UUID(0, 2), true, "{\"personality\":\"kind\"}");
        names.clear();
        biomes.clear();
        assertEquals(List.of("Ada", "Cassey"), request.villagerNames());
        assertEquals(List.of("plains", "river"), request.biomes());
        assertTrue(request.isRegen());
        assertEquals("{\"personality\":\"kind\"}", request.existingText());
        assertThrows(UnsupportedOperationException.class, () -> request.villagerNames().add("Other"));
        assertEquals("alaina_fae-" + new UUID(0, 1) + ".json",
                NpcCommand.defaultProfileFileName(" Alaina Fae ", new UUID(0, 1)));
        assertEquals("npc-" + new UUID(0, 1) + ".json",
                NpcCommand.defaultProfileFileName(null, new UUID(0, 1)));
    }

    @Test
    void generatedPersonalityUpdatesContextWithoutLosingClaimOrForeverKidFields() {
        UUID id = new UUID(0, 1);
        UUID village = new UUID(0, 2);
        var old = new NpcData(id, "Old", null, "old description", "old personality", "",
                true, "wary", "assigned.json");
        var request = new NpcCommand.ProfileRequest("Ada", id, "farmer", List.of(), "River Town",
                List.of("river"), null, village, true, "{}");
        var updated = NpcCommand.applyPersonality(old, request, "A farmer", "A kind friend");
        assertSame(old, updated);
        assertEquals("Ada", updated.getName());
        assertEquals(village, updated.getHomeVillageUUID());
        assertEquals("River Town", updated.getVillageName());
        assertEquals("A farmer", updated.getDescription());
        assertEquals("A kind friend", updated.getPersonality());
        assertTrue(updated.isForeverKid());
        assertEquals("wary", updated.getBehavior());
        assertEquals("assigned.json", updated.getAiProfileFile());
        var fresh = NpcCommand.applyPersonality(null, request, "new description", "new personality");
        assertEquals(id, fresh.getUUID());
        assertFalse(fresh.isForeverKid());
        assertEquals("nonhostile", fresh.getBehavior());
    }
}
