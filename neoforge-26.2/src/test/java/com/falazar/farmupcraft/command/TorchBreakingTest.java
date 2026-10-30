package com.falazar.farmupcraft.command;

import com.falazar.farmupcraft.ai.BreakTorchGoal;
import com.falazar.farmupcraft.data.WorldData;
import com.google.gson.JsonObject;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.serialization.JsonOps;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class TorchBreakingTest {
    @Test
    void exactlyOneOfOneHundredRollsExtendsTheSixBlockRange() {
        int longSearches = 0;
        for (int roll = 0; roll < 100; roll++) {
            int radius = BreakTorchGoal.searchRadius(roll);
            assertEquals(roll == 0 ? 16 : 6, radius);
            if (radius == 16) longSearches++;
        }
        assertEquals(1, longSearches);
        assertThrows(IllegalArgumentException.class, () -> BreakTorchGoal.searchRadius(-1));
        assertThrows(IllegalArgumentException.class, () -> BreakTorchGoal.searchRadius(100));
    }

    @Test
    void onlyNormalAndSoulTorchesAreTargets(MinecraftServer server) {
        assertNotNull(server.registryAccess());
        for (var block : new net.minecraft.world.level.block.Block[]{
                Blocks.TORCH, Blocks.WALL_TORCH, Blocks.SOUL_TORCH, Blocks.SOUL_WALL_TORCH}) {
            assertTrue(BreakTorchGoal.isTorch(block.defaultBlockState()));
        }
        for (var block : new net.minecraft.world.level.block.Block[]{
                Blocks.REDSTONE_TORCH, Blocks.REDSTONE_WALL_TORCH, Blocks.LANTERN, Blocks.STONE, Blocks.AIR}) {
            assertFalse(BreakTorchGoal.isTorch(block.defaultBlockState()));
        }
    }

    @Test
    void toggleDefaultsOnForOldWorldsAndPersistsOff() {
        JsonObject legacy = new JsonObject();
        legacy.addProperty("upkeep_tick", 0);
        var data = WorldData.CODEC.parse(JsonOps.INSTANCE, legacy).getOrThrow();
        assertTrue(data.isMobTorchBreaking());
        data.setMobTorchBreaking(false);
        var encoded = WorldData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow();
        assertFalse(WorldData.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow().isMobTorchBreaking());
        assertTrue(FarmCraftCommand.adminSettings(data).getString().contains("mobTorchBreaking: false"));
    }

    @Test
    void toggleBranchRequiresGamemasterPermissions(MinecraftServer server) {
        var dispatcher = new CommandDispatcher<CommandSourceStack>();
        FarmCraftCommand.register(dispatcher);
        var feedback = new MarketCommandTest.Feedback();
        var ordinary = MarketCommandTest.source(server, PermissionSet.NO_PERMISSIONS, feedback);
        var admin = MarketCommandTest.source(server, LevelBasedPermissionSet.GAMEMASTER, feedback);
        String command = "farmcraft admin settings mobTorchBreaking false";
        assertThrows(com.mojang.brigadier.exceptions.CommandSyntaxException.class,
                () -> dispatcher.execute(command, ordinary));
        var parsed = dispatcher.parse(command, admin);
        assertTrue(parsed.getExceptions().isEmpty());
        assertEquals("", parsed.getReader().getRemaining());
        assertNotNull(parsed.getContext().getCommand());
    }
}
