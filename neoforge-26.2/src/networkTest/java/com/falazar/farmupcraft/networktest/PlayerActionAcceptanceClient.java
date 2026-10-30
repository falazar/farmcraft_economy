package com.falazar.farmupcraft.networktest;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

import java.net.InetSocketAddress;

@EventBusSubscriber(modid = "farmupcraft", value = Dist.CLIENT)
public final class PlayerActionAcceptanceClient {
    private static int stage = -1;
    private static boolean ready;
    private static boolean acted;
    private static boolean complete;
    private static long deadline;

    private PlayerActionAcceptanceClient() {}

    @SubscribeEvent
    public static void register(RegisterClientPayloadHandlersEvent event) {
        if (PlayerActionAcceptance.enabled()) {
            event.register(PlayerActionAcceptance.Step.TYPE, (packet, context) -> {
                if (!ready || packet.stage() != stage + 1 || packet.stage() > 4) {
                    throw new IllegalStateException("Out-of-order player action stage " + packet.stage());
                }
                Minecraft.getInstance().options.keyUp.setDown(false);
                Minecraft.getInstance().options.keyAttack.setDown(false);
                stage = packet.stage();
                acted = false;
                PlayerActionAcceptance.report("SERVER_VERIFIED stage=" + stage);
            });
        }
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!PlayerActionAcceptance.enabled() || complete) return;
        var minecraft = Minecraft.getInstance();
        if (deadline == 0) {
            PlayerActionAcceptance.directory();
            deadline = System.nanoTime() + 240_000_000_000L;
            PlayerActionAcceptance.report("START real connected client; vanilla input/block action paths");
        }
        if (System.nanoTime() > deadline) {
            fail("240s deadline at stage " + stage);
            return;
        }
        if (minecraft.player == null || minecraft.level == null || minecraft.getConnection() == null
                || minecraft.gameMode == null) return;
        var remote = minecraft.getConnection().getConnection().getRemoteAddress();
        if (minecraft.hasSingleplayerServer() || !(remote instanceof InetSocketAddress address)
                || address.getAddress() == null || !address.getAddress().isLoopbackAddress()) {
            fail("Separate loopback dedicated server required");
            return;
        }
        if (!ready) {
            ready = true;
            ClientPacketDistributor.sendToServer(new PlayerActionAcceptance.Acknowledge(-1));
        }
        if (stage < 0) return;
        if (minecraft.gameMode.getPlayerMode() != GameType.SURVIVAL
                || minecraft.player.getY() < 80 || minecraft.player.getY() > 82
                || !minecraft.level.getBlockState(PlayerActionAcceptance.BREAK.below()).is(Blocks.STONE)) return;
        switch (stage) {
            case 0 -> {
                if (minecraft.gui.screen() != null) return;
                minecraft.player.setYRot(-90);
                minecraft.player.setXRot(15);
                minecraft.options.keyUp.setDown(minecraft.player.getX() < 34.65);
            }
            case 1 -> {
                minecraft.options.keyUp.setDown(false);
                Vec3 toward = Vec3.atCenterOf(PlayerActionAcceptance.BREAK)
                        .subtract(minecraft.player.getEyePosition());
                minecraft.player.setYRot((float) Math.toDegrees(Math.atan2(toward.z, toward.x)) - 90);
                minecraft.player.setXRot((float) -Math.toDegrees(
                        Math.atan2(toward.y, Math.sqrt(toward.x * toward.x + toward.z * toward.z))));
                minecraft.options.keyAttack.setDown(true);
            }
            case 2 -> {
                minecraft.options.keyAttack.setDown(false);
                minecraft.gameMode.stopDestroyBlock();
                if (acted || !minecraft.player.getMainHandItem().is(Items.DIRT)) return;
                var support = PlayerActionAcceptance.PLACE.below();
                minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atBottomCenterOf(PlayerActionAcceptance.PLACE),
                                Direction.UP, support, false));
                acted = true;
            }
            case 3 -> {
                if (acted || !minecraft.level.getBlockState(PlayerActionAcceptance.BREAK).isAir()
                        || !minecraft.level.getBlockState(PlayerActionAcceptance.PLACE).is(Blocks.DIRT)
                        || minecraft.player.getMainHandItem().getCount() != 2) return;
                minecraft.options.keyUp.setDown(false);
                ClientPacketDistributor.sendToServer(new PlayerActionAcceptance.Acknowledge(3));
                acted = true;
            }
            case 4 -> {
                PlayerActionAcceptance.report("PASS client observed server stages movement break place and final blocks/dirt=2");
                complete = true;
                minecraft.stop();
            }
            default -> throw new IllegalStateException("Invalid action stage " + stage);
        }
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        if (PlayerActionAcceptance.enabled() && ready && !complete) {
            fail("Disconnected before final authoritative block/inventory confirmation");
        }
    }

    private static void fail(String reason) {
        complete = true;
        var minecraft = Minecraft.getInstance();
        minecraft.options.keyUp.setDown(false);
        minecraft.options.keyAttack.setDown(false);
        PlayerActionAcceptance.report("FAIL " + reason);
        minecraft.stop();
    }
}
