package com.falazar.farmupcraft.events;

import com.falazar.farmupcraft.FarmUpCraft;
import com.falazar.farmupcraft.client.ForgeClientEvents;
import com.falazar.farmupcraft.currency.CoinStack;
import com.falazar.farmupcraft.currency.Wallet;
import com.falazar.farmupcraft.data.*;
import com.falazar.farmupcraft.database.message.DataBaseChunkS2C;
import com.falazar.farmupcraft.entity.FlyingBlockChunkEntity;
import com.falazar.farmupcraft.entity.curves.MotionCurve;
import com.falazar.farmupcraft.entity.curves.*;
import com.falazar.farmupcraft.registry.BiomeRegistryHolder;
import com.falazar.farmupcraft.database.DataBase;
import com.falazar.farmupcraft.database.DataBaseAccess;
import com.falazar.farmupcraft.database.DataBaseManager;
import com.falazar.farmupcraft.database.message.EDBMessages;
import com.falazar.farmupcraft.registry.CoinRegistry;
import com.falazar.farmupcraft.structure.*;
import com.falazar.farmupcraft.structure.plan.EmptyElement;
import com.falazar.farmupcraft.structure.plan.StructureElement;
import com.falazar.farmupcraft.structure.plan.element.PlanElement;
import com.falazar.farmupcraft.structure.rotation.FixedRotation;
import com.falazar.farmupcraft.structure.rotation.RandomRotation;
import com.falazar.farmupcraft.structure.rotation.RotationElement;
import com.falazar.farmupcraft.util.AsyncLocator;
import com.falazar.farmupcraft.util.CustomLogger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Handles various Forge events related to server lifecycle and resource reloads.
 * <p>
 * This class is subscribed to Forge's event bus and contains methods that react to server
 * start, stop, and resource reload events. It is part of the FarmUpCraft mod.
 */
@Mod.EventBusSubscriber(modid = FarmUpCraft.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ForgeEvents {
    public static final CustomLogger LOGGER = new CustomLogger(ForgeEvents.class.getSimpleName());

    public static final List<BuildableStructureInstance> ACTIVE_BUILDS = new ArrayList<>();
    public static final List<FlyingBlockBuildInstance> ACTIVE_FLYING_BUILDS = new ArrayList<>();

    @SubscribeEvent
    public static void structurePlaceItem(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        Level level = player.level();
        //if (!(level instanceof ServerLevel serverLevel)) return;

        ItemStack stack = event.getItemStack();

        if (stack.is(Items.STICK) && event.getHand() == InteractionHand.MAIN_HAND) {
            BlockPos origin = event.getPos();
            BuildableStructureInstance instance = new BuildableStructureInstance(BuildableStructureRegistry.get(TEST_ID), origin, Rotation.CLOCKWISE_90);
            //ACTIVE_BUILDS.add(instance);
            BuildableStructure structure = BuildableStructureRegistry.get(TEST_ID);

            ForgeClientEvents.PREVIEW_MANAGER.showPreview(instance);

            if (player.isShiftKeyDown()) {
                ForgeClientEvents.PREVIEW_MANAGER.clearAll();
            }
        }
        if(stack.is(Items.WOODEN_SWORD) && event.getHand() == InteractionHand.MAIN_HAND) {
            EarthQuakeParticle(level, event.getPos(), player);
        }
    }


    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        Level level = player.level();

        if (level.isClientSide || event.getHand() != InteractionHand.MAIN_HAND) return;

        ItemStack stack = event.getItemStack();

        // Use wooden shovel to trigger the test
        if (stack.is(Items.WOODEN_SHOVEL)) {

            BlockPos sourcePos = event.getPos(); // Illager's current position
            Vec3 start = Vec3.atBottomCenterOf(sourcePos);

            RandomSource random = level.getRandom();

            BlockState blockState = level.getBlockState(sourcePos);
            level.removeBlock(sourcePos, false); // remove only once

            int count = 20; // number of chunks to launch
            int range = 10;

            for (int i = 0; i < count; i++) {
                // 1. Random nearby position
                int dx = random.nextInt(-range, range + 1);
                int dz = random.nextInt(-range, range + 1);
                BlockPos.MutableBlockPos mutableTarget = new BlockPos.MutableBlockPos(sourcePos.getX() + dx, 0, sourcePos.getZ() + dz);

                int topY = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, mutableTarget).getY();
                BlockPos targetPos = new BlockPos(mutableTarget.getX(), topY, mutableTarget.getZ());

                // 2. Motion curve from start to target
                Vec3 end = Vec3.atBottomCenterOf(targetPos);
                Vec3 upFromStart = start.add(0, 10, 0);
                Vec3 downToEnd = end.add(0, 10, 0);

                //MotionCurve upward = new EasedLinearMotionCurve(start, upFromStart, Easing.ELASTIC_OUT);
                //MotionCurve arc = new EasedMotionCurve(new VerticalHopCurve(upFromStart, downToEnd, 3.5), Easing.BOUNCE_OUT);
                //MotionCurve downward = new EasedLinearMotionCurve(downToEnd, end, Easing.ELASTIC_IN);
                SerializableMotionCurve upward = new EasedLinearMotionCurve(start, upFromStart, Easing.SINE_IN); // gentle lift
                SerializableMotionCurve arc = new EasedMotionCurve(new VerticalHopCurve(upFromStart, downToEnd, 3), Easing.BOUNCE_OUT); // accelerating travel
                SerializableMotionCurve downward = new EasedLinearMotionCurve(downToEnd, end, Easing.SINE_OUT); // bounce on impact

                SerializableMotionCurve fullCurve = new ChainedMotionCurve()
                        .addSegment(upward, 0.1)
                        .addSegment(arc, 0.8)
                        .addSegment(downward, 0.1);


                //MotionCurve easedFull = new EasedMotionCurve(fullCurve, Easing.CUBIC_OUT);

                // 3. Spawn entity
                FlyingBlockChunkEntity entity = new FlyingBlockChunkEntity(
                        level,
                        fullCurve,
                        blockState,
                        60, // slight delay between launches
                        true
                );
                entity.setShowTrail(true);
                level.addFreshEntity(entity);
            }








            /**
            BlockPos clickedPos = event.getPos();
            BlockState blockState = level.getBlockState(clickedPos);

            // Remove the block being launched
            level.removeBlock(clickedPos, false);

            // Compute start and end positions
            Vec3 start = Vec3.atBottomCenterOf(clickedPos);
            BlockPos targetPos = clickedPos; // For example, 4 blocks east, 2 blocks up, 2 blocks south
            Vec3 middle = start.add(0, 10, 0); // up

            Vec3 end = start;
            MotionCurve upward = new LinearCurve(start, middle);
            MotionCurve arc = new VerticalHopCurve(middle, end, 1.5);
            MotionCurve curve = new SpiralCurve(middle, 10, 1);
            MotionCurve chained = new ChainedMotionCurve()
                    .addSegment(upward, 0.3)   // 30% of time
                    .addSegment(curve, 0.7);     // 70% of time
            // Create a nice hopping motion



            // Create and spawn the flying block entity
            FlyingBlockChunkEntity flyingBlock = new FlyingBlockChunkEntity(
                    level,
                    chained,
                    blockState,
                    30, // Duration in ticks
                    false
            );

            level.addFreshEntity(flyingBlock);*/
            //player.sendSystemMessage(Component.literal("Launched block from " + clickedPos + " to " + targetPos));
        }
    }


    @SubscribeEvent
    public static void onRightClickBlockStrcuture(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        Level level = player.level();

        if (level.isClientSide || event.getHand() != InteractionHand.MAIN_HAND) return;

        ItemStack stack = event.getItemStack();

        // Test trigger with Blaze Rod
        if (!stack.is(Items.BLAZE_ROD)) return;

        ServerLevel serverLevel = (ServerLevel) level;

        // 1. Get the clicked block and look direction
        BlockPos clickedPos = event.getPos();
        Vec3 castFrom = player.position().add(0, 1.6, 0); // wand cast height

        // 2. Get facing direction and place structure X blocks in front
        Direction facing = player.getDirection(); // horizontal direction
        int distance = 17; // how far to place the structure
        BlockPos buildOrigin = player.blockPosition().relative(facing, distance);

        // 3. Get structure
        BuildableStructure structure = BuildableStructureRegistry.get(TEST_ID);
        if (structure == null) {
            player.sendSystemMessage(Component.literal("Structure not found."));
            return;
        }

        // 4. Determine rotation to align the front of the structure with player look direction
        Rotation rotation = switch (facing) {
            case NORTH -> Rotation.NONE;
            case EAST  -> Rotation.CLOCKWISE_90;
            case SOUTH -> Rotation.CLOCKWISE_180;
            case WEST  -> Rotation.COUNTERCLOCKWISE_90;
            default    -> Rotation.NONE;
        };
        Supplier<Vec3> launchOriginSupplier = () -> {
            Vec3 look = player.getLookAngle().normalize();
            Vec3 pos = player.getEyePosition(); // or .position()
            return pos.add(look.scale(11)).add(0, 6, 0); // in front and above
        };
        // 5. Create flying block build instance
        FlyingBlockBuildInstance flyingBuild = new FlyingBlockBuildInstance(
                structure,
                buildOrigin,
                rotation,
                launchOriginSupplier
        );

        // 6. Register for ticking
        ForgeEvents.ACTIVE_FLYING_BUILDS.add(flyingBuild);

        // 7. Feedback
        player.sendSystemMessage(Component.literal("Started structure build at " + buildOrigin));
    }


    @SubscribeEvent
    public static void onRightClickBlockStructurePlanner(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        Level level = player.level();

        if (level.isClientSide || event.getHand() != InteractionHand.MAIN_HAND) return;

        ItemStack stack = event.getItemStack();

        if (!stack.is(Items.DIAMOND_SHOVEL)) return;

        ServerLevel serverLevel = (ServerLevel) level;

        // ==== Test Plan Setup ====
        StructurePlanner planner = StructurePlanner.builder('z')
                .define('z', new StructureElement(TEST_ID, new RandomRotation(Rotation.NONE, Rotation.CLOCKWISE_90, Rotation.CLOCKWISE_180, Rotation.COUNTERCLOCKWISE_90)))
                .define('l', new StructureElement(TEST_ID_2, new RandomRotation(Rotation.NONE, Rotation.CLOCKWISE_90)))
                .define('m', new StructureElement(TEST_ID_3, new FixedRotation(Rotation.CLOCKWISE_180)))
                .define('n', new StructureElement(TEST_ID_4, new RandomRotation(Rotation.NONE)))
                .define('j', new EmptyElement(new Vec3i(3, 2, 3)))
                .row("jmj")
                .row("lzn")
                .row("jnj")
                .setSpacing(5, 5)
                .build();

        LOGGER.info("Origin at: " + planner.getOrigin());
        for (Map.Entry<StructurePlanner.Vec2i, PlanElement> entry : planner.getLayout().entrySet()) {
            StructurePlanner.Vec2i pos = entry.getKey();
            PlanElement elem = entry.getValue();
            LOGGER.info("At [" + pos + "]: " + elem);
        }

        LOGGER.info("Bounds:");
        //LOGGER.info("X: " + planner.getMinX() + " to " + planner.getMaxX());
        //LOGGER.info("Z: " + planner.getMinZ() + " to " + planner.getMaxZ());

        // ==== Place preview at clicked block ====
        BlockPos worldOrigin = event.getPos();
        for (Map.Entry<StructurePlanner.Vec2i, PlanElement> entry : planner.getLayout().entrySet()) {
            StructurePlanner.Vec2i offset = entry.getKey();
            PlanElement element = entry.getValue();

            if (element instanceof StructureElement structElem) {
                Rotation rot = structElem.resolveRotation(level.random);
                BlockPos placePos = worldOrigin.offset(offset.x(), 0, offset.z());
                BuildableStructureInstance instance = new BuildableStructureInstance(
                        BuildableStructureRegistry.get(structElem.getStructureId()),
                        placePos,
                        rot
                );

                Supplier<Vec3> launchOriginSupplier = () -> {
                    Vec3 look = player.getLookAngle().normalize();
                    Vec3 pos = player.getEyePosition(); // or .position()
                    return pos.add(look.scale(11)).add(0, 6, 0); // in front and above
                };
                // 5. Create flying block build instance
                FlyingBlockBuildInstance flyingBuild = new FlyingBlockBuildInstance(
                        BuildableStructureRegistry.get(structElem.getStructureId()),
                        placePos,
                        rot,
                        launchOriginSupplier
                );

                // 6. Register for ticking
                ForgeEvents.ACTIVE_FLYING_BUILDS.add(flyingBuild);




                ForgeClientEvents.PREVIEW_MANAGER.showPreview(instance);
            }
        }

        player.sendSystemMessage(Component.literal("Test structure plan logged."));
    }

    @SubscribeEvent
    public static void onRightClickBlockStructurePlannerNew(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        Level level = player.level();
        if (level.isClientSide || event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!event.getItemStack().is(Items.IRON_SHOVEL)) return;

        ServerLevel serverLevel = (ServerLevel) level;
        BlockPos origin = event.getPos();

        PlanElement master = new StructureElement(TEST_ID, RotationElement.random90());
        List<PlanElement> subs = List.of(
                new StructureElement(TEST_ID_2, RotationElement.random90()),
                new StructureElement(TEST_ID_2, RotationElement.random90()),
                new StructureElement(TEST_ID_2, RotationElement.random90()),
                new StructureElement(TEST_ID_2, RotationElement.random90()),
                new StructureElement(TEST_ID_2, RotationElement.random90()),
                new StructureElement(TEST_ID_2, RotationElement.random90()),

                new StructureElement(TEST_ID_3, RotationElement.random90()),
                new StructureElement(TEST_ID_3, RotationElement.random90()),
                new StructureElement(TEST_ID_3, RotationElement.random90()),
                new StructureElement(TEST_ID_3, RotationElement.random90()),
                new StructureElement(TEST_ID_3, RotationElement.random90()),
                new StructureElement(TEST_ID_3, RotationElement.random90()),

                new StructureElement(TEST_ID_4, RotationElement.random90()),
                new StructureElement(TEST_ID_4, RotationElement.random90()),
                new StructureElement(TEST_ID_4, RotationElement.random90()),
                new StructureElement(TEST_ID_4, RotationElement.random90()),
                new StructureElement(TEST_ID_4, RotationElement.random90()),
                new StructureElement(TEST_ID_4, RotationElement.random90())
                );

        StructureAreaPlan plan = new StructureAreaPlan(
                master,
                subs,
                2
        );

        plan.generateLayout(level.random);

        for (Map.Entry<StructureAreaPlan.Vec2i, PlanElement> entry : plan.getLayout().entrySet()) {
            StructureAreaPlan.Vec2i offset = entry.getKey();
            PlanElement element = entry.getValue();
            Rotation rotation = element.resolveRotation(level.getRandom());
            BlockPos placePos = origin.offset(offset.x(), 0, offset.z());

            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, placePos.getX(), placePos.getZ());
            placePos = placePos.atY(y);

            BuildableStructure structure = BuildableStructureRegistry.get(element.getStructureId());
            BuildableStructureInstance instance = new BuildableStructureInstance(structure, placePos, rotation);
            ForgeClientEvents.PREVIEW_MANAGER.showPreview(instance);

            Supplier<Vec3> launchOriginSupplier = () -> {
                Vec3 look = player.getLookAngle().normalize();
                Vec3 pos = player.getEyePosition();
                return pos.add(look.scale(11)).add(0, 6, 0);
            };

            FlyingBlockBuildInstance flyingBuild = new FlyingBlockBuildInstance(
                    structure,
                    placePos,
                    rotation,
                    launchOriginSupplier
            );
            ForgeEvents.ACTIVE_FLYING_BUILDS.add(flyingBuild);
        }

        player.sendSystemMessage(Component.literal("AreaPlan built and previewed."));
    }


    public static void spawnFlyingBlocks(ServerLevel level, Vec3 from, List<BuildableStructureInstance.BuildStep> steps) {
        for (BuildableStructureInstance.BuildStep step : steps) {
            Vec3 to = Vec3.atBottomCenterOf(step.position());

            // Create nice chained curve
            Vec3 mid1 = from.add(0, 10, 0);
            Vec3 mid2 = to.add(0, 10, 0);
            double hopHeight = 3.5;

            SerializableMotionCurve upward = new EasedLinearMotionCurve(from, mid1, Easing.SINE_IN);
            SerializableMotionCurve arc = new EasedMotionCurve(new VerticalHopCurve(mid1, mid2, hopHeight), Easing.BOUNCE_OUT);
            SerializableMotionCurve downward = new EasedLinearMotionCurve(mid2, to, Easing.SINE_OUT);

            SerializableMotionCurve curve = new ChainedMotionCurve()
                    .addSegment(upward, 0.15)
                    .addSegment(arc, 0.7)
                    .addSegment(downward, 0.15);

            FlyingBlockChunkEntity entity = new FlyingBlockChunkEntity(
                    level,
                    curve,
                    step.state(),
                    60,
                    false
            );
            entity.setShowTrail(true);
            //entity.setBlockEntityTag(step.tag());
            level.addFreshEntity(entity);
        }
    }



    private static void EarthQuakeParticle(Level level, BlockPos pos, Player player) {
        if (level.isClientSide) {
            RandomSource randomSource = level.getRandom();
            BlockState block = level.getBlockState(pos.below());
            for (int i1 = 0; i1 < 20 + randomSource.nextInt(12); i1++) {
                double DeltaMovementX = randomSource.nextGaussian() * 0.07D;
                double DeltaMovementY = randomSource.nextGaussian() * 0.07D;
                double DeltaMovementZ = randomSource.nextGaussian() * 0.07D;
                float angle = (0.01745329251F * player.yBodyRot) + i1;
                double extraX = 4F * Mth.sin((float) (Math.PI + angle));
                double extraY = 0.3F;
                double extraZ = 4F * Mth.cos(angle);
                if (block.getRenderShape() != RenderShape.INVISIBLE) {
                    level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, block), pos.getX() + extraX, pos.getY() + extraY, pos.getZ() + extraZ, DeltaMovementX, DeltaMovementY, DeltaMovementZ);
                }
            }
        }
    }


    @SubscribeEvent
    public static void serverTickEvent(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) return;
        tickAll(event.getServer().overworld(), 5);

    }

    public static void tickAll(ServerLevel level, int blocksPerTick) {
        Iterator<BuildableStructureInstance> it = ACTIVE_BUILDS.iterator();
        while (it.hasNext()) {
            BuildableStructureInstance instance = it.next();
            instance.tickBuild(level, blocksPerTick);
            if (instance.isFinished()) it.remove();
        }

        Iterator<FlyingBlockBuildInstance> it2 = ACTIVE_FLYING_BUILDS.iterator();
        while (it2.hasNext()) {
            FlyingBlockBuildInstance instance = it2.next();
            instance.tick(level, 5, FlyingBlockBuildInstance.BuildStyle.CHUNKY);
            if (instance.isFinished()) it2.remove();
        }

    }


    /**
     * Called when the server is about to start.
     * <p>
     * This method handles tasks that need to be done just before the server starts.
     * Currently, it manages asynchronous tasks related to server startup.
     *
     * @param event The event containing server startup details.
     */
    @SubscribeEvent
    public static void serverAboutToStart(final ServerAboutToStartEvent event) {
        AsyncLocator.handleServerAboutToStartEvent();
        BiomeRegistryHolder.setupBiomeRegistry(event.getServer());
    }

    /**
     * Called when the server has started.
     * <p>
     * This method populates crop block and crop item data after the server starts.
     * It uses the server's overworld to initialize data for crop blocks and items.
     *
     * @param event The event containing server start details.
     */

    public static final ResourceLocation TEST_ID = new ResourceLocation("farmupcraft", "test_house");
    public static final ResourceLocation TEST_ID_2 = new ResourceLocation("farmupcraft", "test_house_2");
    public static final ResourceLocation TEST_ID_3 = new ResourceLocation("farmupcraft", "test_house_3");
    public static final ResourceLocation TEST_ID_4 = new ResourceLocation("farmupcraft", "test_house_4");

    @SubscribeEvent
    public static void serverStarted(final ServerStartedEvent event) {
        CropBlockDataJsonManager.populateCropBlockEntries(event.getServer().overworld());
        CropItemDataJsonManager.populateCropItemEntries(event.getServer().overworld());
        BiomeRulesDataJsonManager.populateBiomeRulesInstances(event.getServer().overworld());
        BuildableStructure testStructure = new BuildableStructure(new ResourceLocation("minecraft", "ancient_city/city_center/city_center_1"));
        BuildableStructure testStructure2 = new BuildableStructure(new ResourceLocation("minecraft", "village/plains/houses/plains_small_house_1"));
        BuildableStructure testStructure3 = new BuildableStructure(new ResourceLocation("minecraft", "bastion/treasure/bases/centers/center_0"));
        BuildableStructure testStructure4 = new BuildableStructure(new ResourceLocation("minecraft", "end_city/fat_tower_base"));

        BuildableStructureRegistry.register(TEST_ID, testStructure);
        BuildableStructureRegistry.register(TEST_ID_2, testStructure2);
        BuildableStructureRegistry.register(TEST_ID_3, testStructure3);
        BuildableStructureRegistry.register(TEST_ID_4, testStructure4);

        BuildableStructureRegistry.getStructures().values().forEach(buildableStructure -> buildableStructure.gatherAndPopulateStructureData(event.getServer().overworld()));
    }

    /**
     * Called when the server is stopping.
     * <p>
     * This method handles cleanup tasks when the server is about to stop, such as
     * clearing cached entries and handling asynchronous tasks.
     *
     * @param event The event containing server stopping details.
     */
    @SubscribeEvent
    public static void onServerStopping(final ServerStoppingEvent event) {
        AsyncLocator.handleServerStoppingEvent();
        CropBlockDataJsonManager.clearEntries();
        CropItemDataJsonManager.clearEntries();
        BiomeRulesDataJsonManager.clearEntries();


    }

    @SubscribeEvent
    public static void serverStopped(final ServerStoppedEvent event) {
        DataBaseManager.shutDownDataBases(event.getServer().overworld());
    }

    /**
     * Called when the reload listeners are being registered.
     * <p>
     * This method registers new reload listeners for handling JSON resource data related
     * to crop blocks, crop items, and the market. These listeners will be triggered
     * when the resources are reloaded.
     *
     * @param event The event containing reload listener registration details.
     */
    @SubscribeEvent
    public static void onRegisterReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new CropBlockDataJsonManager());
        event.addListener(new CropItemDataJsonManager());
        //event.addListener(new MarketDataJsonManager());
        event.addListener(new BiomeRulesDataJsonManager());
    }

    @SubscribeEvent
    public static void onLoginEvent(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity().level() instanceof ServerLevel level) {

            DataBase<UUID, PlayerData> playerDatabase = ModEvents.getPlayerDatabase();
            UUID uuid = ((ServerPlayer) event.getEntity()).getUUID();

            if (!playerDatabase.containsKey(uuid)) {
                CoinStack bronzeStack = new CoinStack(CoinRegistry.getCoin(CoinRegistry.BRONZE_COIN), 10);
                playerDatabase.putData(uuid, new PlayerData(event.getEntity().getId(), UUID.randomUUID(), new Wallet(List.of(bronzeStack))));
            }

            for (ResourceLocation dataBaseName : DataBaseManager.getDataBasesToSync()) {
                DataBaseAccess<?, ?> dataBaseAccess = DataBaseManager.getDataBaseAccess(dataBaseName);

                CompletableFuture.runAsync(() -> {
                    DataBase<?, ?> dataBase = dataBaseAccess.get(level);
                    List<CompoundTag> chunks = dataBase.saveChunked(50); // ← send always in chunks

                    level.getServer().execute(() -> {
                        ServerPlayer player = (ServerPlayer) event.getEntity();
                        for (int i = 0; i < chunks.size(); i++) {
                            EDBMessages.sendToPlayer(new DataBaseChunkS2C<>(
                                    chunks.get(i),
                                    dataBase.getDatabaseName(),
                                    i,
                                    chunks.size()
                            ), player);
                        }
                    });
                });
            }
        }
    }


}
