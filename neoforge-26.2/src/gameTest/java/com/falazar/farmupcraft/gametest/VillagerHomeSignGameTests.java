package com.falazar.farmupcraft.gametest;

import com.falazar.farmupcraft.VillagerHomeSigns;
import com.falazar.farmupcraft.VillagerManager;
import com.falazar.farmupcraft.data.ChunkData;
import com.falazar.farmupcraft.data.VillageData;
import com.falazar.farmupcraft.events.ModEvents;
import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = FarmUpCraftNeoForge.MOD_ID)
public final class VillagerHomeSignGameTests {
    private static final String KEY = "farmupcraft_home_sign";

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, functions -> functions.register(
                FarmUpCraftNeoForge.prefix("villager_home_signs"), VillagerHomeSignGameTests::verify));
        event.register(Registries.TEST_FUNCTION, functions -> functions.register(
                FarmUpCraftNeoForge.prefix("villager_home_sign_entrances"), VillagerHomeSignGameTests::verifyEntrances));
        event.register(Registries.TEST_FUNCTION, functions -> functions.register(
                FarmUpCraftNeoForge.prefix("villager_home_sign_double_door"), VillagerHomeSignGameTests::verifyDoubleDoor));
    }

    private static void verify(GameTestHelper helper) {
        try (var fixture = new Fixture(helper)) {
            var level = fixture.level;
            var alice = fixture.villager("Alice", fixture.bed(0, 1));
            var bob = fixture.villager("Bob", fixture.bed(0, 2));
            var cara = fixture.villager("Cara", fixture.bed(0, 4));
            var dan = fixture.villager("Dan", fixture.bed(0, 5));
            alice.tickCount = Math.floorMod(-alice.getUUID().hashCode(), 200);
            VillagerManager.onVillagerTick(new EntityTickEvent.Pre(alice));
            helper.assertValueEqual(alice.getPersistentData().getLongOr("farmupcraft_home_sign_next_check", 0L),
                    level.getGameTime() + 144000L, "Full checks are scheduled exactly two game-time hours apart");
            var first = fixture.sign(alice);
            helper.assertValueEqual(first.getBlockState().getValue(WallSignBlock.FACING), Direction.NORTH,
                    "Sign faces the outside approach, not the bedroom");
            helper.assertTrue(level.getHeight(Heightmap.Types.MOTION_BLOCKING,
                            first.getBlockPos().getX(), first.getBlockPos().getZ()) > first.getBlockPos().getY(),
                    "The test entrance has a covered porch");
            helper.assertTrue(first.getBlockPos().getZ() < fixture.door(0).getZ(),
                    "Sign is outside the actual exterior door");
            helper.assertTrue(fixture.hasText(first, "Alice") && fixture.hasText(first, "Farmer"),
                    "Sign contains name and profession");
            VillagerHomeSigns.update(bob);
            helper.assertValueEqual(fixture.sign(bob).getBlockPos(), first.getBlockPos(),
                    "Two residents share a household sign");
            VillagerHomeSigns.update(cara);
            VillagerHomeSigns.update(dan);
            var second = fixture.sign(cara);
            helper.assertTrue(!second.getBlockPos().equals(first.getBlockPos()), "Overflow creates a second sign");
            helper.assertValueEqual(fixture.sign(dan).getBlockPos(), second.getBlockPos(),
                    "Four residents fit on two shared signs");
            helper.assertTrue(fixture.hasText(second, "Cara") && fixture.hasText(second, "Dan"),
                    "Overflow sign contains both residents");
            helper.assertValueEqual(fixture.signCount(), 2, "No duplicate signs after creating four residents");
            for (var resident : List.of(alice, bob, cara, dan)) VillagerHomeSigns.update(resident);
            helper.assertValueEqual(fixture.signCount(), 2, "Repeated checks are idempotent");
            var signProblems = new ProblemReporter.Collector();
            var signSaved = first.saveWithFullMetadata(level.registryAccess());
            var signState = first.getBlockState();
            var signPos = first.getBlockPos();
            level.removeBlock(signPos, false);
            level.setBlock(signPos, signState, 2);
            first = fixture.sign(alice);
            first.loadWithComponents(TagValueInput.create(signProblems, level.registryAccess(), signSaved));
            helper.assertTrue(signProblems.isEmpty(), "Household sign save/reload must preserve resident metadata");
            helper.assertTrue(fixture.hasText(first, "Alice") && fixture.hasText(first, "Bob"),
                    "A reloaded household sign keeps both resident entries");

            fixture.assign(alice, fixture.bed(1, 1));
            VillagerHomeSigns.update(alice);
            helper.assertTrue(fixture.hasText(first, "Bob") && !fixture.hasText(first, "Alice"),
                    "A move removes only that resident, not the shared sign or other resident");
            var newHome = fixture.sign(alice);
            fixture.assign(bob, fixture.bed(1, 2));
            VillagerHomeSigns.update(bob);
            helper.assertTrue(level.getBlockState(first.getBlockPos()).isAir(),
                    "The unchanged old sign is removed when its final resident moves");
            helper.assertValueEqual(fixture.sign(bob).getBlockPos(), newHome.getBlockPos(),
                    "Moved residents share their new home sign");

            fixture.assign(alice, fixture.bed(2, 1));
            VillagerHomeSigns.update(alice);
            var edited = fixture.sign(alice);
            edited.setText(edited.getFrontText().setMessage(0, Component.literal("Player's shop")), true);
            fixture.assign(alice, fixture.bed(0, 1));
            VillagerHomeSigns.update(alice);
            helper.assertTrue(level.getBlockEntity(edited.getBlockPos()) == edited
                            && fixture.hasText(edited, "Player's shop"),
                    "A bed move never removes or rewrites a player-edited old sign");

            var removed = fixture.sign(alice).getBlockPos();
            level.removeBlock(removed, false);
            VillagerHomeSigns.update(alice);
            helper.assertTrue(level.getBlockState(removed).isAir(), "Removed signs stay removed at the same bed");
            fixture.assign(alice, fixture.bed(0, 1).south());
            VillagerHomeSigns.update(alice);
            helper.assertTrue(level.getBlockState(removed).isAir(),
                    "The bed's foot and head are one home, not a move that recreates a sign");
            var problems = new ProblemReporter.Collector();
            var output = TagValueOutput.createWithContext(problems, level.registryAccess());
            alice.saveWithoutId(output);
            var restored = fixture.villager("Restored", fixture.bed(0, 1));
            restored.load(TagValueInput.create(problems, level.registryAccess(), output.buildResult()));
            helper.assertTrue(problems.isEmpty(), "Villager save/reload must preserve sign metadata: " + problems.getReport());
            helper.assertValueEqual(restored.getPersistentData().getLongOr("farmupcraft_home_sign_next_check", 0L),
                    alice.getPersistentData().getLongOr("farmupcraft_home_sign_next_check", 0L),
                    "The two-hour deadline survives actual entity serialization");
            VillagerHomeSigns.update(restored);
            helper.assertTrue(level.getBlockState(removed).isAir(),
                    "The per-bed placed flag survives actual entity serialization");
            fixture.assign(restored, fixture.bed(1, 1));
            VillagerHomeSigns.update(restored);
            helper.assertTrue(fixture.hasText(fixture.sign(restored), "Alice"),
                    "Moving after removal allows an entry at the new home");

            var newborn = fixture.villager("Newborn", null);
            newborn.setAge(-24000);
            int count = fixture.signCount();
            VillagerHomeSigns.update(newborn);
            helper.assertValueEqual(fixture.signCount(), count, "Newborns without assigned beds create no signs");
            fixture.assign(newborn, fixture.bed(0, 2));
            VillagerHomeSigns.update(newborn);
            helper.assertTrue(fixture.hasText(fixture.sign(newborn), "Child"),
                    "A newborn gets a sign after receiving a bed");
            newborn.setAge(0);
            VillagerHomeSigns.update(newborn);
            helper.assertTrue(fixture.hasText(fixture.sign(newborn), "Farmer"),
                    "Growing up refreshes an unchanged generated profession");
            var newbornSign = fixture.sign(newborn);
            newbornSign.setWaxed(true);
            newborn.setCustomName(Component.literal("Renamed"));
            VillagerHomeSigns.update(newborn);
            helper.assertTrue(fixture.hasText(newbornSign, "Newborn"),
                    "Player waxing protects a sign from automatic updates");

            var outsider = fixture.villager("Outsider", fixture.bed(2, 2));
            fixture.unbuy();
            count = fixture.signCount();
            VillagerHomeSigns.update(outsider);
            helper.assertValueEqual(fixture.signCount(), count, "Unbought villages never get automatic signs");
            helper.assertTrue(!outsider.getPersistentData().getCompoundOrEmpty(KEY).getBooleanOr("placed", false),
                    "An unbought village does not consume the sign flag");
        }
        helper.succeed();
    }

    private static void verifyEntrances(GameTestHelper helper) {
        try (var fixture = new Fixture(helper)) {
            var level = fixture.level;
            var villager = fixture.villager("Entrance", fixture.bed(0, 1));
            var primary = fixture.door(0);
            level.setBlock(primary.north().below(), Blocks.OAK_FENCE.defaultBlockState(), 2);
            VillagerHomeSigns.update(villager);
            helper.assertValueEqual(fixture.signCount(), 0, "A fence protruding into the approach is not walkable ground");
            level.setBlock(primary.north().below(), Blocks.STONE.defaultBlockState(), 2);
            level.setBlock(primary.north(), Blocks.STONE.defaultBlockState(), 2);
            var blocked = fixture.villager("Blocked", fixture.bed(0, 2));
            VillagerHomeSigns.update(blocked);
            helper.assertValueEqual(fixture.signCount(), 0, "A blocked exterior approach is not a valid entrance");
            helper.assertTrue(blocked.getPersistentData().getCompoundOrEmpty(KEY).getBooleanOr("placed", false),
                    "An unsuitable home consumes the same done flag as a successful placement");
            var other = fixture.start(0).offset(6, 0, 3);
            var state = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.EAST);
            level.setBlock(other, state, 2);
            level.setBlock(other.above(), state.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 2);
            VillagerHomeSigns.update(villager);
            helper.assertValueEqual(fixture.signCount(), 0, "A repaired doorway does not retry a completed failed attempt");
            fixture.assign(villager, fixture.bed(0, 4));
            VillagerHomeSigns.update(villager);
            var sign = fixture.sign(villager);
            helper.assertValueEqual(sign.getBlockState().getValue(WallSignBlock.FACING), Direction.EAST,
                    "The reachable alternate exterior doorway is selected when the nearer approach is blocked");
            helper.assertTrue(sign.getBlockPos().getX() > other.getX(), "Alternate sign goes on the outdoor side");

            var resident = fixture.villager("No space", fixture.bed(1, 1));
            var door = fixture.door(1);
            for (int offset : new int[]{1, 2, 3}) {
                for (int height : new int[]{0, 1}) {
                    level.setBlock(door.north().relative(Direction.WEST, offset).above(height),
                            Blocks.STONE.defaultBlockState(), 2);
                }
            }
            int before = fixture.signCount();
            VillagerHomeSigns.update(resident);
            helper.assertValueEqual(fixture.signCount(), before, "Occupied sign positions are never overwritten");
            helper.assertTrue(resident.getPersistentData().getCompoundOrEmpty(KEY).getBooleanOr("placed", false),
                    "Full sign space marks the task done without placement");
            var free = door.north().west().above();
            level.setBlock(free, Blocks.AIR.defaultBlockState(), 2);
            VillagerHomeSigns.update(resident);
            helper.assertTrue(level.getBlockState(free).isAir(), "Freed sign space is not retried at the same bed");
            fixture.assign(resident, fixture.bed(1, 2));
            resident.tickCount = Math.floorMod(-resident.getUUID().hashCode(), 200);
            var persistent = resident.getPersistentData();
            persistent.putLong("farmupcraft_home_sign_next_check", level.getGameTime() + 1);
            VillagerHomeSigns.tick(resident);
            helper.assertTrue(level.getBlockState(free).isAir(), "A changed bed is not inspected before the saved deadline");
            persistent.putLong("farmupcraft_home_sign_next_check", level.getGameTime());
            VillagerHomeSigns.tick(resident);
            helper.assertValueEqual(fixture.sign(resident).getBlockPos(), free,
                    "The exact saved deadline permits one attempt for a different assigned bed");
            helper.assertValueEqual(persistent.getLongOr("farmupcraft_home_sign_next_check", 0L),
                    level.getGameTime() + 144000L, "The due check saves another two-hour deadline");
            resident.setCustomName(Component.literal("Too soon"));
            resident.tickCount += 200;
            VillagerHomeSigns.tick(resident);
            helper.assertTrue(fixture.hasText(fixture.sign(resident), "No space"),
                    "Another lightweight gate cannot trigger a full sign refresh during the two-hour interval");

            var upstairs = fixture.start(2);
            for (var pos : BlockPos.betweenClosed(upstairs, upstairs.offset(6, 10, 6))) {
                boolean wall = pos.getX() == upstairs.getX() || pos.getX() == upstairs.getX() + 6
                        || pos.getZ() == upstairs.getZ() || pos.getZ() == upstairs.getZ() + 6
                        || pos.getY() == upstairs.getY() + 10;
                level.setBlock(pos, wall ? Blocks.OAK_PLANKS.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
            }
            var downstairsDoor = fixture.door(2);
            var downstairsState = Blocks.SPRUCE_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH);
            level.setBlock(downstairsDoor, downstairsState, 2);
            level.setBlock(downstairsDoor.above(), downstairsState.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 2);
            for (int x = 1; x <= 5; x++) {
                level.setBlock(upstairs.offset(x, 5 - x, 3),
                        Blocks.BIRCH_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.WEST), 2);
            }
            var upstairsBed = upstairs.offset(1, 6, 4);
            level.setBlock(upstairsBed.below(), Blocks.OAK_PLANKS.defaultBlockState(), 2);
            level.setBlock(upstairsBed.south().below(), Blocks.OAK_PLANKS.defaultBlockState(), 2);
            var bedState = Blocks.BED.gray().defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH);
            level.setBlock(upstairsBed, bedState.setValue(BedBlock.PART, BedPart.HEAD), 2);
            level.setBlock(upstairsBed.south(), bedState.setValue(BedBlock.PART, BedPart.FOOT), 2);
            var stairsResident = fixture.villager("Stairs", upstairsBed);
            VillagerHomeSigns.update(stairsResident);
            helper.assertTrue(fixture.sign(stairsResident).getBlockPos().getZ() < downstairsDoor.getZ(),
                    "A six-block descent on real birch stairs reaches the downstairs exterior doorway");
            var legacy = fixture.villager("Previously skipped", upstairsBed);
            var legacyData = new net.minecraft.nbt.CompoundTag();
            legacyData.putLong("bed", upstairsBed.asLong());
            legacyData.putString("dimension", level.dimension().identifier().toString());
            legacyData.putBoolean("placed", true);
            legacyData.putString("skipped", "Previous entrance failure");
            legacy.getPersistentData().put(KEY, legacyData);
            before = fixture.signCount();
            VillagerHomeSigns.update(legacy);
            helper.assertValueEqual(fixture.signCount(), before, "Diagnostics never retry permanent skipped attempts");
            helper.assertTrue(!legacyData.contains("sign") && legacyData.getBooleanOr("placed", false),
                    "A now-reachable old failure remains skipped without migration or placement");
        }
        helper.succeed();
    }

    private static void verifyDoubleDoor(GameTestHelper helper) {
        try (var fixture = new Fixture(helper)) {
            var level = fixture.level;
            var door = fixture.door(0);
            var doubleDoor = door.west();
            var doorState = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH);
            level.setBlock(doubleDoor, doorState, 2);
            level.setBlock(doubleDoor.above(), doorState.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 2);
            var playerPos = doubleDoor.west().north().above();
            var signState = Blocks.OAK_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING, Direction.NORTH);
            level.setBlock(playerPos, signState, 2);
            var playerSign = (SignBlockEntity) level.getBlockEntity(playerPos);
            playerSign.setText(playerSign.getFrontText().setMessage(0, Component.literal("Lili's Kitchen")), true);
            var alice = fixture.villager("Alice", fixture.bed(0, 1));
            var bob = fixture.villager("Bob", fixture.bed(0, 2));
            VillagerHomeSigns.update(alice);
            VillagerHomeSigns.update(bob);
            var correct = fixture.sign(alice);
            helper.assertValueEqual(correct.getBlockPos(), playerPos.below(),
                    "Double doors are skipped and the resident sign goes below the existing sign on the outside right");
            helper.assertTrue(fixture.hasText(playerSign, "Lili's Kitchen"),
                    "Existing player sign is not reused, overwritten, or moved");
            var saved = correct.saveWithFullMetadata(level.registryAccess());
            var oldPos = doubleDoor.north().above();
            level.removeBlock(correct.getBlockPos(), false);
            level.setBlock(oldPos, signState, 2);
            var old = (SignBlockEntity) level.getBlockEntity(oldPos);
            var problems = new ProblemReporter.Collector();
            old.loadWithComponents(TagValueInput.create(problems, level.registryAccess(), saved));
            helper.assertTrue(problems.isEmpty(), "Old generated sign fixture must retain saved data");
            for (var villager : List.of(alice, bob)) {
                villager.getPersistentData().getCompoundOrEmpty(KEY).putLong("sign", oldPos.asLong());
            }
            VillagerHomeSigns.update(alice);
            helper.assertValueEqual(fixture.sign(alice).getBlockPos(), playerPos.below(),
                    "An unchanged existing door-mounted sign is safely relocated");
            helper.assertTrue(fixture.hasText(old, "Bob") && !fixture.hasText(old, "Alice"),
                    "Repair keeps residents who have not been checked yet");
            VillagerHomeSigns.update(bob);
            helper.assertTrue(level.getBlockState(oldPos).isAir(), "The vacated generated sign is removed from the door");
            helper.assertValueEqual(fixture.sign(bob).getBlockPos(), playerPos.below(),
                    "Shared residents reunite at the corrected sign");
            helper.assertTrue(level.getBlockState(doubleDoor).is(Blocks.OAK_DOOR)
                    && level.getBlockState(doubleDoor.above()).is(Blocks.OAK_DOOR), "Repair does not alter either door half");
            var cara = fixture.villager("Cara", fixture.bed(0, 4));
            VillagerHomeSigns.update(cara);
            helper.assertValueEqual(fixture.sign(cara).getBlockPos(), playerPos.west(),
                    "Overflow uses the next right wall column only after the nearest column is full");
            var editedPos = doubleDoor.north().above();
            var caraSaved = fixture.sign(cara).saveWithFullMetadata(level.registryAccess());
            level.removeBlock(fixture.sign(cara).getBlockPos(), false);
            level.setBlock(editedPos, signState, 2);
            var edited = (SignBlockEntity) level.getBlockEntity(editedPos);
            edited.loadWithComponents(TagValueInput.create(problems, level.registryAccess(), caraSaved));
            edited.setText(edited.getFrontText().setMessage(0, Component.literal("Player edited")), true);
            cara.getPersistentData().getCompoundOrEmpty(KEY).putLong("sign", editedPos.asLong());
            VillagerHomeSigns.update(cara);
            helper.assertTrue(fixture.hasText(edited, "Player edited"),
                    "Even door-mounted signs stay untouched if a player edited them");
        }
        helper.succeed();
    }

    private static final class Fixture implements AutoCloseable {
        final ServerLevel level;
        final BlockPos base;
        final UUID villageId = UUID.randomUUID();
        final Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        final Map<Long, ChunkData> plots = new LinkedHashMap<>();
        final List<Villager> villagers = new ArrayList<>();
        final GameTestHelper helper;

        Fixture(GameTestHelper helper) {
            this.helper = helper;
            level = helper.getLevel();
            var origin = helper.absolutePos(new BlockPos(2, 1, 2));
            base = new BlockPos(origin.getX(), Math.max(100, origin.getY()), origin.getZ());
            for (var pos : BlockPos.betweenClosed(base.offset(-2, -1, -4), base.offset(18, 10, 18))) {
                var immutable = pos.immutable();
                blocks.put(immutable, level.getBlockState(pos));
                long key = ChunkPos.pack(pos);
                if (!plots.containsKey(key)) plots.put(key, ModEvents.getChunkDataDatabase(level).getData(key));
                level.setBlock(pos, pos.getY() == base.getY() - 1
                        ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
            }
            var village = village(true);
            ModEvents.getVillageDatabase(level).putData(villageId, village);
            for (long key : plots.keySet()) {
                ModEvents.getChunkDataDatabase(level).putData(key, new ChunkData("village", -1, villageId));
            }
            for (int house = 0; house < 3; house++) {
                var start = start(house);
                for (var pos : BlockPos.betweenClosed(start, start.offset(6, 3, 6))) {
                    boolean wall = pos.getX() == start.getX() || pos.getX() == start.getX() + 6
                            || pos.getZ() == start.getZ() || pos.getZ() == start.getZ() + 6
                            || pos.getY() == start.getY() + 3;
                    level.setBlock(pos, wall ? Blocks.OAK_PLANKS.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
                }
                var door = door(house);
                var state = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH);
                level.setBlock(door, state, 2);
                level.setBlock(door.above(), state.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 2);
                for (int x = 0; x <= 6; x++) {
                    for (int z = -3; z < 0; z++) {
                        level.setBlock(start.offset(x, 3, z), Blocks.OAK_PLANKS.defaultBlockState(), 2);
                    }
                }
                for (int x : new int[]{1, 2, 4, 5}) {
                    var head = bed(house, x);
                    var bed = Blocks.BED.red().defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH);
                    level.setBlock(head, bed.setValue(BedBlock.PART, BedPart.HEAD), 2);
                    level.setBlock(head.south(), bed.setValue(BedBlock.PART, BedPart.FOOT), 2);
                }
            }
            // A geographically closer door opens into a sealed closet, not outdoors.
            var closet = start(0).offset(0, 0, 3);
            var state = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.WEST);
            level.setBlock(closet, state, 2);
            level.setBlock(closet.above(), state.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 2);
            for (var pos : BlockPos.betweenClosed(closet.offset(-2, 0, -1), closet.offset(-1, 3, 1))) {
                boolean interior = pos.getX() == closet.getX() - 1 && pos.getZ() == closet.getZ()
                        && pos.getY() < closet.getY() + 2;
                level.setBlock(pos, interior ? Blocks.AIR.defaultBlockState() : Blocks.OAK_PLANKS.defaultBlockState(), 2);
            }
        }

        VillageData village(boolean bought) {
            return new VillageData(villageId, "Home sign fixture", ChunkPos.containing(base), 1,
                    plots.keySet().stream().map(ChunkPos::unpack).toList(), bought, 0,
                    "2000-01-01", "", "2000-01-01", "", List.of());
        }

        void unbuy() {
            ModEvents.getVillageDatabase(level).putData(villageId, village(false));
        }

        BlockPos start(int house) {
            return base.offset(house == 1 ? 10 : 0, 0, house == 2 ? 10 : 0);
        }

        BlockPos door(int house) {
            return start(house).offset(3, 0, 0);
        }

        BlockPos bed(int house, int x) {
            return start(house).offset(x, 0, 3);
        }

        void assign(Villager villager, BlockPos bed) {
            villager.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), bed));
        }

        Villager villager(String name, BlockPos bed) {
            var villager = EntityTypes.VILLAGER.create(level, EntitySpawnReason.COMMAND);
            if (villager == null) throw new IllegalStateException("Could not create home-sign test villager");
            villagers.add(villager);
            villager.setCustomName(Component.literal(name));
            villager.setPos(base.getX() + 2.5, base.getY(), base.getZ() + 2.5);
            villager.setAge(0);
            villager.setVillagerData(villager.getVillagerData().withProfession(
                    level.registryAccess().lookupOrThrow(Registries.VILLAGER_PROFESSION).getOrThrow(VillagerProfession.FARMER)));
            if (bed != null) assign(villager, bed);
            return villager;
        }

        SignBlockEntity sign(Villager villager) {
            var data = villager.getPersistentData().getCompoundOrEmpty(KEY);
            helper.assertTrue(data.getBooleanOr("placed", false), "Resident must have a placed household sign: " + data);
            var block = level.getBlockEntity(BlockPos.of(data.getLongOr("sign", 0L)));
            if (!(block instanceof SignBlockEntity sign)) throw new IllegalStateException("Expected household sign");
            return sign;
        }

        boolean hasText(SignBlockEntity sign, String value) {
            for (int i = 0; i < 4; i++) if (sign.getFrontText().getMessage(i, false).getString().equals(value)) return true;
            return false;
        }

        int signCount() {
            return (int) blocks.keySet().stream().filter(pos -> level.getBlockEntity(pos) instanceof SignBlockEntity).count();
        }

        @Override
        public void close() {
            villagers.forEach(Villager::discard);
            blocks.forEach((pos, state) -> level.setBlock(pos, state, 2));
            var database = ModEvents.getChunkDataDatabase(level);
            plots.forEach((key, plot) -> {
                if (plot == null) database.removeDataBatch(List.of(key));
                else database.putData(key, plot);
            });
            ModEvents.getVillageDatabase(level).removeDataBatch(List.of(villageId));
            VillagerManager.clearCaches();
        }
    }
}
