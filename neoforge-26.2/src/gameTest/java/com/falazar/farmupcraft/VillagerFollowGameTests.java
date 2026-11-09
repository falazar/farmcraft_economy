package com.falazar.farmupcraft;

import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.HashMap;
import java.util.UUID;

@EventBusSubscriber(modid = FarmUpCraftNeoForge.MOD_ID)
public final class VillagerFollowGameTests {
    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, functions -> functions.register(
                FarmUpCraftNeoForge.prefix("villager_bread_follow"), VillagerFollowGameTests::follow));
    }

    private static void follow(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "follow-test"));
        var other = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "follow-other"));
        var players = level.getServer().getPlayerList().getPlayersByUUID();
        var previous = new HashMap<BlockPos, BlockState>();
        var villager = helper.spawn(EntityTypes.VILLAGER, new BlockPos(2, 2, 2));
        try {
            for (int x = 0; x <= 12; x++) for (int z = 0; z <= 4; z++) {
                var pos = helper.absolutePos(new BlockPos(x, 1, z));
                previous.put(pos, level.getBlockState(pos));
                level.setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
            }
            players.put(player.getUUID(), player);
            var target = helper.absolutePos(new BlockPos(10, 2, 2));
            player.setPos(target.getX() + 0.5, target.getY(), target.getZ() + 0.5);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BREAD, 3));
            villager.setPos(villager.getX() + 0.5, villager.getY(), villager.getZ() + 0.5);
            NpcManager.beginConversation(player, villager);
            var click = new PlayerInteractEvent.EntityInteract(player, InteractionHand.MAIN_HAND, villager, Vec3.ZERO);
            NpcManager.onRightClickNpc(click);
            helper.assertTrue(click.isCanceled(), "Bread click must suppress trading and greeting");
            helper.assertValueEqual(player.getMainHandItem().getCount(), 3, "Bread must not be consumed");
            helper.assertTrue(!NpcManager.holdConversation(villager), "Bread click must end the old conversation");
            helper.assertValueEqual(villager.getPersistentData().getStringOr(
                    VillagerFollowManager.FOLLOW_PLAYER_KEY, ""), player.getUUID().toString(),
                    "Follow ownership must be persisted on the villager");
            other.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BREAD));
            NpcManager.onRightClickNpc(new PlayerInteractEvent.EntityInteract(
                    other, InteractionHand.MAIN_HAND, villager, Vec3.ZERO));
            helper.assertValueEqual(villager.getPersistentData().getStringOr(
                    VillagerFollowManager.FOLLOW_PLAYER_KEY, ""), player.getUUID().toString(),
                    "Another player cannot steal or stop a follower");

            double initialDistance = villager.distanceToSqr(player);
            for (int tick = 0; tick < 100; tick++) villager.tick();
            helper.assertTrue(villager.distanceToSqr(player) < initialDistance / 2,
                    "Real brain/navigation ticks must move the villager toward its player");
            helper.assertTrue(villager.distanceToSqr(player) <= 5,
                    "Follower must reach its two-block stopping radius without teleporting");
            player.setPos(villager.getX() + 1, villager.getY(), villager.getZ());
            villager.tick();
            helper.assertTrue(villager.getNavigation().isDone(), "Follower must stop near its player");
            player.setPos(villager.getX() + 49, villager.getY(), villager.getZ());
            villager.tick();
            helper.assertTrue(villager.getNavigation().isDone(), "Out-of-range followers wait, never teleport");
            players.remove(player.getUUID());
            helper.assertTrue(VillagerFollowManager.tickFollowing(villager), "Offline follower keeps its assignment");
            helper.assertTrue(villager.getNavigation().isDone(), "Offline follower waits");
            players.put(player.getUUID(), player);
            var stop = new PlayerInteractEvent.EntityInteract(player, InteractionHand.MAIN_HAND, villager, Vec3.ZERO);
            NpcManager.onRightClickNpc(stop);
            helper.assertTrue(stop.isCanceled(), "Stop click must not open a conversation");
            helper.assertTrue(!VillagerFollowManager.tickFollowing(villager), "Second bread click restores ordinary AI");
            helper.assertTrue(!villager.isNoAi(), "Following must not change saved NoAI");
        } catch (RuntimeException error) {
            throw new IllegalStateException("Bread-follow test failed: " + error.getClass().getSimpleName()
                    + ": " + error.getMessage(), error);
        } finally {
            players.remove(player.getUUID());
            NpcManager.clearConversation(player.getUUID());
            NpcManager.clearConversation(other.getUUID());
            villager.discard();
            player.getInventory().clearContent();
            other.getInventory().clearContent();
            previous.forEach((pos, state) -> level.setBlock(pos, state, 2));
        }
        helper.succeed();
    }
}
