package com.falazar.farmupcraft.gametest;

import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@EventBusSubscriber(modid = FarmUpCraftNeoForge.MOD_ID)
public final class NpcSkeletonCommandGameTests {
    private NpcSkeletonCommandGameTests() {}

    @SubscribeEvent
    public static void registerFunctions(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, functions -> functions.register(
                FarmUpCraftNeoForge.prefix("npc_skeleton_command_parity"), NpcSkeletonCommandGameTests::skeletons));
    }

    private static void skeletons(GameTestHelper helper) {
        var level = helper.getLevel();
        var position = helper.absolutePos(new BlockPos(1, 2, 1));
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "skeleton_parity"));
        player.setPos(position.getX() + 0.5, position.getY(), position.getZ() + 0.5);
        var source = level.getServer().createCommandSourceStack().withLevel(level).withEntity(player)
                .withPosition(player.position()).withSuppressedOutput();
        var dispatcher = level.getServer().getCommands().getDispatcher();
        var area = new AABB(position).inflate(4);
        List<Skeleton> spawned = new ArrayList<>();
        try {
            for (String literal : List.of("createskeleton", "createskleleton")) {
                var existing = level.getEntitiesOfClass(Skeleton.class, area).stream()
                        .map(Skeleton::getUUID).collect(Collectors.toSet());
                boolean rejected = false;
                try {
                    dispatcher.execute("npc " + literal + " TestSkeleton",
                            source.withPermission(PermissionSet.NO_PERMISSIONS));
                } catch (CommandSyntaxException expected) {
                    rejected = true;
                }
                helper.assertTrue(rejected, "Non-op must not execute /npc " + literal);
                helper.assertValueEqual(level.getEntitiesOfClass(Skeleton.class, area).size(),
                        existing.size(), "Rejected skeleton command must not spawn an entity");
                int result = dispatcher.execute("npc " + literal + " TestSkeleton",
                        source.withPermission(LevelBasedPermissionSet.GAMEMASTER));
                var created = level.getEntitiesOfClass(Skeleton.class, area).stream()
                        .filter(skeleton -> !existing.contains(skeleton.getUUID())).toList();
                spawned.addAll(created);
                helper.assertValueEqual(result, 1, "Successful /npc " + literal + " result");
                helper.assertValueEqual(created.size(), 1, "Exactly one actual skeleton per alias");
                helper.assertTrue(created.getFirst().goalSelector.getAvailableGoals().isEmpty()
                                && created.getFirst().targetSelector.getAvailableGoals().isEmpty(),
                        "Skeleton commands must preserve cleared legacy AI goals");
            }
        } catch (CommandSyntaxException failure) {
            throw new IllegalStateException("Skeleton command parity dispatch failed", failure);
        } finally {
            spawned.forEach(Skeleton::discard);
        }
        helper.succeed();
    }
}
