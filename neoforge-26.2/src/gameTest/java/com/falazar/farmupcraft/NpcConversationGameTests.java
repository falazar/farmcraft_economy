package com.falazar.farmupcraft;

import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@EventBusSubscriber(modid = FarmUpCraftNeoForge.MOD_ID)
public final class NpcConversationGameTests {
    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, functions -> functions.register(
                FarmUpCraftNeoForge.prefix("npc_conversation_focus"), NpcConversationGameTests::focus));
    }

    private static void focus(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "npc_focus_test"));
        var other = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "npc_focus_other"));
        var villager = helper.spawn(EntityTypes.VILLAGER, new BlockPos(1, 2, 1));
        var cow = helper.spawn(EntityTypes.COW, new BlockPos(3, 2, 1));
        try {
            villager.setNoGravity(true);
            cow.setNoGravity(true);
            var start = villager.position();
            player.setPos(start.x + 4, start.y, start.z);
            other.setPos(start.x, start.y, start.z + 4);
            NpcManager.beginConversation(player, villager);
            villager.setVillagerData(villager.getVillagerData().withProfession(
                    level.registryAccess().lookupOrThrow(Registries.VILLAGER_PROFESSION)
                            .getOrThrow(VillagerProfession.FARMER)).withLevel(1));
            villager.setTradingPlayer(player);
            villager.notifyTrade(new MerchantOffer(new ItemCost(Items.EMERALD),
                    new ItemStack(Items.BREAD), 100, 10, 0));
            helper.assertValueEqual(villager.getVillagerXp(), 10, "Trade must earn novice level-up XP");
            villager.getBrain().setMemoryWithExpiry(MemoryModuleType.HURT_BY_ENTITY, cow, 5);
            for (int tick = 0; tick < 10; tick++) {
                villager.setDeltaMovement(0.3, 0, 0.2);
                villager.getNavigation().moveTo(player, 1);
                villager.tick();
                assertFacing(helper, villager, player.getEyePosition());
                helper.assertTrue(villager.position().distanceToSqr(start) < 0.000001,
                        "Villager must not wander while listening, thinking, or replying");
                helper.assertTrue(villager.getNavigation().isDone(), "Conversation stops navigation");
                helper.assertTrue(!villager.isNoAi(), "Conversation must never modify saved NoAI");
                player.setPos(start.x + 4 - tick * 0.2, start.y, start.z + tick * 0.2);
            }
            helper.assertValueEqual(villager.getVillagerData().level(), 1,
                    "Vanilla must not level up while the trading screen remains open");
            helper.assertTrue(villager.getBrain().hasMemoryValue(MemoryModuleType.HURT_BY_ENTITY),
                    "Conversation must pause brain ticks, not only movement");
            villager.setTradingPlayer(null);
            for (int tick = 0; tick < 39; tick++) villager.tick();
            helper.assertValueEqual(villager.getVillagerData().level(), 1,
                    "Chat must preserve the full vanilla 40-tick level-up delay");
            villager.tick();
            helper.assertValueEqual(villager.getVillagerData().level(), 2,
                    "Earned level-up must complete on tick 40 while chat is still active");
            helper.assertTrue(NpcManager.holdConversation(villager), "Level-up must not end conversation");
            helper.assertTrue(villager.position().distanceToSqr(start) < 0.000001,
                    "Villager must remain stationary during merchant updates");

            NpcManager.beginConversation(other, villager);
            NpcManager.clearConversation(player.getUUID());
            helper.assertTrue(NpcManager.holdConversation(villager), "Another listener retains the hold");
            assertFacing(helper, villager, other.getEyePosition());
            NpcManager.clearConversation(other.getUUID());
            helper.assertTrue(!NpcManager.holdConversation(villager), "Last listener leaving releases the NPC");
            for (int tick = 0; tick < 6; tick++) villager.tick();
            helper.assertTrue(!villager.getBrain().hasMemoryValue(MemoryModuleType.HURT_BY_ENTITY),
                    "Brain memory timers must resume after conversation ends");

            player.setPos(cow.getX() + 4, cow.getY(), cow.getZ());
            var aiTicks = new AtomicInteger();
            cow.goalSelector.addGoal(0, new Goal() {
                @Override
                public boolean canUse() { return true; }
                @Override
                public boolean requiresUpdateEveryTick() { return true; }
                @Override
                public void tick() { aiTicks.incrementAndGet(); }
            });
            NpcManager.beginConversation(player, cow);
            cow.setDeltaMovement(0.2, -0.3, 0.1);
            NpcManager.holdConversation(cow);
            helper.assertTrue(Math.abs(cow.getDeltaMovement().y + 0.3) < 0.000001,
                    "Conversation hold preserves falling and vertical physics");
            cow.setDeltaMovement(Vec3.ZERO);
            for (int i = 0; i < 8; i++) cow.tick();
            helper.assertValueEqual(aiTicks.get(), 0, "Real mob AI ticks are paused by the mixin");
            assertFacing(helper, cow, player.getEyePosition());
            NpcManager.clearConversation(player.getUUID());
            for (int i = 0; i < 8; i++) cow.tick();
            helper.assertTrue(aiTicks.get() > 0, "Original AI resumes immediately after conversation");

            NpcManager.beginConversation(player, cow);
            player.setPos(cow.getX() + 11, cow.getY(), cow.getZ());
            helper.assertTrue(!NpcManager.holdConversation(cow), "Moving beyond 10 blocks releases the NPC");
            player.setPos(cow.getX() + 4, cow.getY(), cow.getZ());
            NpcManager.beginConversation(player, cow);
            expireConversation(player.getUUID());
            helper.assertTrue(!NpcManager.holdConversation(cow), "Two-minute timeout releases the NPC without chat");
            NpcManager.beginConversation(player, cow);
            player.setHealth(0);
            helper.assertTrue(!NpcManager.holdConversation(cow), "Dead player cannot retain the hold");
            player.setHealth(20);

            cow.setNoAi(true);
            NpcManager.beginConversation(player, cow);
            cow.tick();
            assertFacing(helper, cow, player.getEyePosition());
            NpcManager.clearConversation(player.getUUID());
            helper.assertTrue(cow.isNoAi(), "Pre-existing NoAI remains unchanged");
            cow.setDeltaMovement(0.2, -0.3, 0.1);
            var freeMotion = cow.getDeltaMovement();
            helper.assertTrue(!NpcManager.holdConversation(cow) && cow.getDeltaMovement().equals(freeMotion),
                    "NPCs outside conversations retain their movement");
            NpcManager.beginConversation(player, villager);
            NpcManager.shutdown(level.getServer());
            helper.assertTrue(!NpcManager.holdConversation(villager), "Shutdown clears all conversation holds");
        } finally {
            NpcManager.clearConversation(player.getUUID());
            NpcManager.clearConversation(other.getUUID());
            villager.discard();
            cow.discard();
            player.discard();
            other.discard();
        }
        helper.succeed();
    }

    private static void assertFacing(GameTestHelper helper, Mob npc, Vec3 eye) {
        var direction = eye.subtract(npc.getEyePosition()).normalize();
        helper.assertTrue(npc.getLookAngle().dot(direction) > 0.999,
                "NPC must turn its head toward the player's eyes");
        helper.assertTrue(Math.abs(npc.getYHeadRot() - npc.yBodyRot) < 0.001,
                "NPC body must face the player too");
    }

    private static void expireConversation(UUID player) {
        try {
            var field = NpcManager.class.getDeclaredField("ACTIVE_CONVERSATIONS");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            var conversations = (Map<UUID, Object>) field.get(null);
            var old = conversations.get(player);
            var parts = old.getClass().getRecordComponents();
            Object[] values = new Object[parts.length];
            Class<?>[] types = new Class<?>[parts.length];
            for (int i = 0; i < parts.length; i++) {
                var accessor = parts[i].getAccessor();
                accessor.setAccessible(true);
                types[i] = parts[i].getType();
                values[i] = parts[i].getName().equals("startedAt")
                        ? System.currentTimeMillis() - 120_001 : accessor.invoke(old);
            }
            var constructor = old.getClass().getDeclaredConstructor(types);
            constructor.setAccessible(true);
            conversations.put(player, constructor.newInstance(values));
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Could not expire test conversation", error);
        }
    }
}
