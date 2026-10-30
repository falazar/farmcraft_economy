package com.falazar.farmupcraft.gametest;

import com.falazar.farmupcraft.data.ChunkData;
import com.falazar.farmupcraft.data.PlayerData;
import com.falazar.farmupcraft.events.ModEvents;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

final class ResourcePlotFixture implements AutoCloseable {
    final ServerLevel level;
    final BlockPos pos;
    final long key;
    final FakePlayer player;
    private final BlockState previousBlock;
    private final ChunkData previousPlot;
    private final PlayerData previousPlayer;
    private final AABB bounds;
    private final Set<UUID> previousDrops;

    ResourcePlotFixture(GameTestHelper helper) {
        level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(1, 1, 1));
        // Protection falls back to Y=60 for nonpositive bought_y, even in low-lying test structures.
        pos = new BlockPos(origin.getX(), Math.max(64, origin.getY()), origin.getZ());
        key = ChunkPos.pack(pos);
        previousBlock = level.getBlockState(pos);
        previousPlot = ModEvents.getChunkDataDatabase(level).getData(key);
        bounds = new AABB(pos).inflate(1);
        previousDrops = level.getEntitiesOfClass(ItemEntity.class, bounds).stream()
                .map(ItemEntity::getUUID).collect(Collectors.toSet());
        player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "resource-test"));
        player.setGameMode(GameType.SURVIVAL);
        player.setPos(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        previousPlayer = ModEvents.getPlayerDatabase(level).getData(player.getUUID());
        ModEvents.getChunkDataDatabase(level).removeDataBatch(List.of(key));
    }

    List<ItemEntity> newDrops() {
        return level.getEntitiesOfClass(ItemEntity.class, bounds,
                item -> !previousDrops.contains(item.getUUID()));
    }

    int dropCount(Item item) {
        return newDrops().stream().map(ItemEntity::getItem).filter(stack -> stack.is(item))
                .mapToInt(ItemStack::getCount).sum();
    }

    @Override
    public void close() {
        newDrops().forEach(ItemEntity::discard);
        level.setBlock(pos, previousBlock, 2);
        var plots = ModEvents.getChunkDataDatabase(level);
        if (previousPlot == null) {
            plots.removeDataBatch(List.of(key));
        } else {
            plots.putData(key, previousPlot);
        }
        var players = ModEvents.getPlayerDatabase(level);
        if (previousPlayer == null) {
            players.removeDataBatch(List.of(player.getUUID()));
        } else {
            players.putData(player.getUUID(), previousPlayer);
        }
        player.getInventory().clearContent();
        player.discard();
    }
}
