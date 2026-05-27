package com.falazar.farmupcraft;

import com.falazar.farmupcraft.data.GameStructureData;
import com.falazar.farmupcraft.database.DataBase;
import com.falazar.farmupcraft.events.ModEvents;
import com.falazar.farmupcraft.util.CustomLogger;
import com.falazar.farmupcraft.util.StructureUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Random;

import static com.falazar.farmupcraft.FarmUpCraft.MODID;

/**
 * Handles structure-related gameplay logic: chest trap detection,
 * mob spawning, and spawn position resolution.
 */
@Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class StructureManager {
    public static final CustomLogger LOGGER = new CustomLogger(StructureManager.class.getSimpleName());

    @SubscribeEvent
    public static void onSpecialChestInteract(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide())
            return;
        triggerSpecialChestTrap(event.getLevel(), event.getPos(),
                event.getEntity() instanceof net.minecraft.server.level.ServerPlayer sp ? sp : null);
    }

    @SubscribeEvent
    public static void onSpecialChestBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel().isClientSide())
            return;
        net.minecraft.world.level.Level level = (net.minecraft.world.level.Level) event.getLevel();
        triggerSpecialChestTrap(level, event.getPos(),
                event.getPlayer() instanceof net.minecraft.server.level.ServerPlayer sp ? sp : null);
    }

    // TODO MOBS: Replace skeleton with one of these 5 mob types (random pick):
    // - iceandfire:fire_dragon stage 2, random color — breathes fire, ~1.5 blocks
    // wide
    // - iceandfire:cockatrice — blinds/debuffs, skeleton-sized
    // - lycanitesmobs:ventorapter — fast flying attacker
    // - lycanitesmobs:tremor — underground ambusher
    // - lycanitesmobs:crusk — armored brute

    /**
     * Returns true if the block is a chest-like block (vanilla or Ice and Fire
     * cursed chest).
     */
    public static boolean isStructureChest(Block blk) {
        if (blk == Blocks.CHEST || blk == Blocks.TRAPPED_CHEST || blk == Blocks.BARREL) {
            return true;
        }
        ResourceLocation rl = ForgeRegistries.BLOCKS.getKey(blk);
        return rl != null && rl.getNamespace().equals("iceandfire") && rl.getPath().contains("chest");
    }

    /**
     * Entry point called from ForgeEvents when any block is right-clicked or
     * broken.
     * Filters to chest-like blocks inside active structure traps, then fires the
     * trap.
     */
    public static void triggerSpecialChestTrap(Level level, BlockPos pos, ServerPlayer player) {
        if (!(level instanceof ServerLevel serverLevel))
            return;

        // Must be a chest or barrel (vanilla or modded).
        Block blk = level.getBlockState(pos).getBlock();
        if (!isStructureChest(blk))
            return;

        // Resolve double-chest partner position.
        BlockPos partnerPos = null;
        BlockState state = level.getBlockState(pos);
        if (blk instanceof ChestBlock) {
            ChestType chestType = state.getValue(ChestBlock.TYPE);
            if (chestType != ChestType.SINGLE) {
                net.minecraft.core.Direction partnerDir = ChestBlock.getConnectedDirection(state);
                partnerPos = pos.relative(partnerDir).immutable();
            }
        }

        DataBase<Long, GameStructureData> structureDb = ModEvents.getGameStructureDatabase(serverLevel);
        GameStructureData s = StructureUtils.findStructureAtPos(structureDb, pos);
        if (s == null || !s.hasSpecialChest())
            return;

        // Village structures don't trigger traps.
        if (s.getType().contains("village") || s.getName().contains("village"))
            return;

        // Already opened this chest (or its double-chest partner)?
        if (s.isChestOpened(pos) || (partnerPos != null && s.isChestOpened(partnerPos)))
            return;

        // Mark both halves as opened so this chest never triggers again.
        s.markChestOpened(pos);
        if (partnerPos != null)
            s.markChestOpened(partnerPos);
        structureDb.putData(s.getId(), s);
        structureDb.setDirty();

        // Spawn chance: >4 chests in structure → 30%, ≤4 → 70%.
        int count = s.getTotalChestCount();
        double chance = count > 4 ? 0.30 : 0.70;
        if (Math.random() >= chance) {
            LOGGER.info("Chest opened at {} in {} — no spawn ({}% miss)", pos, s.getName(),
                    (int) ((1 - chance) * 100));
            return;
        }

        LOGGER.info("Chest trap triggered at {} in structure {} ({}) — spawning mob", pos, s.getName(), s.getId());

        BlockPos spawnPos = findRandomSpawnPos(serverLevel, pos, 10, 30);
        spawnTrapMob(serverLevel, spawnPos, player, s);
    }

    /**
     * Spawns the trap mob at the given position and notifies the player.
     * TODO MOBS: pick randomly from the 5 mob types listed above.
     * For fire_dragon: use stage 2 (size=2) and a random color (0–4).
     * Example NBT: {DragonStage:2, DragonType:2} — type 0=fire,1=ice; color is
     * random 0-4.
     */
    private static void spawnTrapMob(ServerLevel serverLevel, BlockPos spawnPos,
            ServerPlayer player, GameStructureData s) {
        Skeleton skeleton = new Skeleton(EntityType.SKELETON, serverLevel);
        skeleton.setPos(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);
        skeleton.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        skeleton.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        skeleton.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
        skeleton.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
        for (EquipmentSlot slot : new EquipmentSlot[] {
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET }) {
            skeleton.setDropChance(slot, 0f);
        }
        skeleton.finalizeSpawn(serverLevel, serverLevel.getCurrentDifficultyAt(spawnPos),
                MobSpawnType.MOB_SUMMONED, null, null);
        serverLevel.addFreshEntity(skeleton);

        if (player != null) {
            player.sendSystemMessage(Component.literal(
                    "You disturbed something in " + s.getName() + "...")
                    .withStyle(ChatFormatting.DARK_RED));
        }
    }

    /**
     * Finds a random passable position within {@code radius} blocks of
     * {@code center}.
     */
    public static BlockPos findRandomSpawnPos(ServerLevel level, BlockPos center, int radius, int attempts) {
        Random rand = new Random();
        for (int i = 0; i < attempts; i++) {
            int dx = rand.nextInt(radius * 2 + 1) - radius;
            int dz = rand.nextInt(radius * 2 + 1) - radius;
            int dy = rand.nextInt(5) - 2;
            BlockPos candidate = center.offset(dx, dy, dz);
            if (level.getBlockState(candidate.below()).isSolidRender(level, candidate.below())
                    && level.getBlockState(candidate).isAir()
                    && level.getBlockState(candidate.above()).isAir()) {
                return candidate;
            }
        }
        return center; // fallback: spawn at chest itself
    }
}
