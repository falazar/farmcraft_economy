package com.falazar.farmupcraft.entity;


import com.falazar.farmupcraft.FarmUpCraft;
import com.falazar.farmupcraft.entity.curves.ArcCurve;
import com.falazar.farmupcraft.entity.curves.VerticalHopCurve;
import com.falazar.farmupcraft.util.CustomLogger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.Containers;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.function.Predicate;

public class FlyingBlockChunkEntity extends Entity {
    protected static final EntityDataAccessor<BlockPos> DATA_START_POS = SynchedEntityData.defineId(FlyingBlockChunkEntity.class, EntityDataSerializers.BLOCK_POS);
    public static final CustomLogger LOGGER = new CustomLogger(FlyingBlockChunkEntity.class.getSimpleName());
    private boolean landed = false;

    // Interpolation targets
    private Vec3 serverTargetPos;
    private int lerpStepsRemaining = 0;

    private BlockState blockState;
    private MotionCurve curve; // Not saved directly — see below
    private int duration;
    private boolean shouldPlaceBlock;
    private boolean impactHandled = false;

    private int age = 0;
    private boolean showTrail;

    public FlyingBlockChunkEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.blockState = Blocks.DIRT.defaultBlockState();
        this.curve = t -> this.position();
        this.duration = 20;
        this.shouldPlaceBlock = false;
    }

    public FlyingBlockChunkEntity(Level level, MotionCurve curve, BlockState blockState, int duration, boolean shouldPlaceBlock) {
        super(FUCEntities.FLYING_BLOCK_CHUNK.get(), level);
        this.blockState = blockState;
        this.curve = curve;
        this.duration = duration;
        this.shouldPlaceBlock = shouldPlaceBlock;
        this.setPos(curve.compute(0));

    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_START_POS, BlockPos.ZERO);
    }

    @Override
    public void tick() {
        super.tick();

        if (level().isClientSide) {

            if (level().isClientSide && age % 2 == 0 ) {
                level().addParticle(ParticleTypes.CLOUD, getX(), getY(), getZ(), 0, 0.01, 0);
            }

            if (lerpStepsRemaining > 0 && serverTargetPos != null) {
                double dx = (serverTargetPos.x - getX()) / lerpStepsRemaining;
                double dy = (serverTargetPos.y - getY()) / lerpStepsRemaining;
                double dz = (serverTargetPos.z - getZ()) / lerpStepsRemaining;
                this.setPos(getX() + dx, getY() + dy, getZ() + dz);
                lerpStepsRemaining--;
            }


            return;
        }


        double t = Mth.clamp(age / (double) duration, 0.0, 1.1);


        Vec3 pos = curve.compute(t);
        this.setPos(pos);
        checkBlockCollision();

        if (landed) {
            if(!level().getBlockState(blockPosition().below()).isAir()) {
                onImpact();
            }

            if (shouldPlaceBlock) {


                if ((level().isEmptyBlock(blockPosition()) || level().getBlockState(blockPosition()).isAir())
                        && blockState.canSurvive(level(), blockPosition()) || !level().getBlockState(blockPosition()).canOcclude()) {
                    level().setBlock(blockPosition(), blockState, 3);
                } else {
                    dropBrokenBlockLoot(blockState,blockPosition());
                }
            } else {
                dropBrokenBlockLoot(blockState, blockPosition());
            }

            discard();
            return;
        }

        if (t >= 1.1) {

            landed = true;
            return;
        }

        age++;
    }

    //todo fix onImpact only when there is something below it
    public void onImpact() {
        if (impactHandled) return;
        impactHandled = true;
        checkForHits();
        level().broadcastEntityEvent(this, (byte) 17);
    }

    @Override
    public void handleEntityEvent(byte pId) {
        super.handleEntityEvent(pId);
        if(pId == 17) {
            for (int i = 0; i < 16; i++) {
                double x = getX() + (random.nextDouble() - 0.5) * 1.5;
                double y = getY();
                double z = getZ() + (random.nextDouble() - 0.5) * 1.5;

                // Randomized speed components
                double vx = (random.nextDouble() - 0.5) * 0.3;
                double vy = random.nextDouble() * 0.4 + 0.1; // upward kick
                double vz = (random.nextDouble() - 0.5) * 0.3;

                level().addParticle(
                        new BlockParticleOption(ParticleTypes.BLOCK, blockState),
                        x, y, z,
                        vx, vy, vz
                );
            }
        }
    }

    protected void onMissed() {
    }

    private void checkForHits() {
        if (!level().isClientSide) {
            AABB hitBox = this.getBoundingBox().inflate(0.2); // Slightly expanded for reliable detection
            Predicate<Entity> filter = e -> e != this && e.isAlive() && !e.isSpectator();

            boolean hit = false;
            for (Entity entity : level().getEntities(this, hitBox, filter)) {
                hit = true;
                onHitEntity(entity);
            }
            if (!hit) onMissed();

        }
    }

    public void setShowTrail(boolean showTrail) {
        this.showTrail = showTrail;
    }

    public void onHitEntity(Entity target) {
        target.hurt(getImpactDamageSource(), getImpactDamageAmount());
    }

    public DamageSource getImpactDamageSource() {
        return this.damageSources().fallingBlock(this);
    }

    public float getImpactDamageAmount() {
        return 6.0F;
    }
    protected void checkBlockCollision() {
        if (!level().getBlockState(blockPosition()).isAir()) {
            onImpact(); // Early impact
        }
    }


    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.blockState = NbtUtils.readBlockState(this.level().holderLookup(Registries.BLOCK), tag.getCompound("BlockState"));
        this.age = tag.getInt("Age");
        this.duration = tag.getInt("Duration");
        this.shouldPlaceBlock = tag.getBoolean("PlaceBlock");
        Vec3 start = this.position();
        this.curve = new VerticalHopCurve(start, start,1.5);
    }


    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.put("BlockState", NbtUtils.writeBlockState(blockState));
        tag.putInt("Duration", duration);
        tag.putInt("Age", age);
        tag.putBoolean("PlaceBlock", shouldPlaceBlock);
    }



    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this, Block.getId(this.getBlockState()));
    }
    public void recreateFromPacket(ClientboundAddEntityPacket pPacket) {
        super.recreateFromPacket(pPacket);
        this.blockState = Block.stateById(pPacket.getData());
        this.blocksBuilding = true;
        double d0 = pPacket.getX();
        double d1 = pPacket.getY();
        double d2 = pPacket.getZ();
        this.setPos(d0, d1, d2);
        this.setStartPos(this.blockPosition());
    }

    public void setStartPos(BlockPos pStartPos) {
        this.entityData.set(DATA_START_POS, pStartPos);
    }
    public BlockPos getStartPos() {
        return this.entityData.get(DATA_START_POS);
    }

    protected Entity.MovementEmission getMovementEmission() {
        return Entity.MovementEmission.NONE;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public void lerpTo(double pX, double pY, double pZ, float pYRot, float pXRot, int pLerpSteps, boolean pTeleport) {
        this.serverTargetPos = new Vec3(pX, pY, pZ);
        this.lerpStepsRemaining = pLerpSteps;
    }

    public void dropBrokenBlockLoot(BlockState state, BlockPos pos) {
        if (!(level() instanceof ServerLevel serverLevel)) return;

        LootParams.Builder lootBuilder = new LootParams.Builder(serverLevel)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                .withParameter(LootContextParams.BLOCK_STATE, state)
                .withParameter(LootContextParams.TOOL, new ItemStack(Items.DIAMOND_PICKAXE))
                .withOptionalParameter(LootContextParams.THIS_ENTITY, this);

        List<ItemStack> drops = state.getDrops(lootBuilder);

        for (ItemStack drop : drops) {
            this.spawnAtLocation(drop);
        }
    }

    @Override
    protected Component getTypeName() {
        return Component.literal("Flying Dirt Chunk");
    }

    public BlockState getBlockState() {
        return blockState;
    }
}
