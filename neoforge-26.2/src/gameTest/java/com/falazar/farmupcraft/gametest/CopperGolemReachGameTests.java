package com.falazar.farmupcraft.gametest;

import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.TransportItemsBetweenContainers;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.Map;

@EventBusSubscriber(modid = FarmUpCraftNeoForge.MOD_ID)
public final class CopperGolemReachGameTests {
    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, functions -> functions.register(
                FarmUpCraftNeoForge.prefix("copper_golem_reach"), CopperGolemReachGameTests::verify));
    }

    private static void verify(GameTestHelper helper) {
        var level = helper.getLevel();
        var golem = new CopperGolem(EntityTypes.COPPER_GOLEM, level);
        var other = new Zombie(EntityTypes.ZOMBIE, level);
        var pos = helper.absolutePos(new BlockPos(2, 1, 2));
        golem.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        other.setPos(golem.position());
        var behavior = new TransportItemsBetweenContainers(1, state -> true, state -> true, 32, 8,
                Map.of(), mob -> {}, target -> false);
        try {
            var method = TransportItemsBetweenContainers.class.getDeclaredMethod("isWithinTargetDistance",
                    double.class, TransportItemsBetweenContainers.TransportItemTarget.class,
                    Level.class, PathfinderMob.class, Vec3.class);
            method.setAccessible(true);
            for (int height = 0; height <= 3; height++) {
                var target = new TransportItemsBetweenContainers.TransportItemTarget(pos.east().above(height),
                        new SimpleContainer(27), null, Blocks.CHEST.defaultBlockState());
                var center = golem.position().add(0, golem.getBbHeight() / 2.0, 0);
                boolean reachable = (boolean) method.invoke(behavior, 0.5, target, level, golem, center);
                helper.assertTrue(reachable == (height <= 2), "Copper golem reach at height +" + height);
                if (height == 2) {
                    boolean otherReach = (boolean) method.invoke(behavior, 0.5, target, level, other,
                            other.position().add(0, other.getBbHeight() / 2.0, 0));
                    var otherBox = other.getBoundingBox();
                    boolean vanilla = target.state().getCollisionShape(level, target.pos()).bounds()
                            .inflate(0.5, 0.5, 0.5).move(target.pos()).intersects(otherBox);
                    helper.assertTrue(otherReach == vanilla, "Other mobs retain vanilla reach");
                }
            }
            var distant = new TransportItemsBetweenContainers.TransportItemTarget(pos.east(4).above(2),
                    new SimpleContainer(27), null, Blocks.CHEST.defaultBlockState());
            helper.assertTrue(!(boolean) method.invoke(behavior, 0.5, distant, level, golem,
                    golem.position().add(0, golem.getBbHeight() / 2.0, 0)), "Horizontal reach is not extended");
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Copper golem reach verification failed", error);
        } finally {
            golem.discard();
            other.discard();
        }
        helper.succeed();
    }
}
