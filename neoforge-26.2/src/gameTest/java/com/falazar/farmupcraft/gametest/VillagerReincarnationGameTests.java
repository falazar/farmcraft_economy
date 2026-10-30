package com.falazar.farmupcraft.gametest;

import com.falazar.farmupcraft.data.ChunkData;
import com.falazar.farmupcraft.data.NpcData;
import com.falazar.farmupcraft.data.VillageData;
import com.falazar.farmupcraft.data.VillagerRecord;
import com.falazar.farmupcraft.events.ModEvents;
import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = FarmUpCraftNeoForge.MOD_ID)
public final class VillagerReincarnationGameTests {
    private VillagerReincarnationGameTests() {
    }

    @SubscribeEvent
    public static void registerFunctions(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, functions -> {
            functions.register(FarmUpCraftNeoForge.prefix("villager_reincarnation_death_identity"),
                    VillagerReincarnationGameTests::deathIdentity);
            functions.register(FarmUpCraftNeoForge.prefix("villager_reincarnation_loaded_unchanged"),
                    VillagerReincarnationGameTests::loadedUnchanged);
            functions.register(FarmUpCraftNeoForge.prefix("villager_reincarnation_unclaimed_unchanged"),
                    VillagerReincarnationGameTests::unclaimedUnchanged);
            functions.register(FarmUpCraftNeoForge.prefix("villager_reincarnation_forever_kid"),
                    VillagerReincarnationGameTests::foreverKid);
        });
    }

    private static void deathIdentity(GameTestHelper helper) {
        try (var fixture = new Fixture(helper)) {
            var victim = fixture.create(fixture.identity(1), "Remembered farmer");
            fixture.add(victim);
            UUID identity = victim.getUUID();
            var npc = new NpcData(identity, "Remembered farmer", fixture.village.getUUID(),
                    "Orchard keeper", "Patient", fixture.village.getName(), false,
                    "nonhostile", "reincarnation-test-profile");
            ModEvents.getNpcDatabase(fixture.level).putData(identity, npc);

            helper.assertTrue(victim.hurtServer(fixture.level, victim.damageSources().genericKill(), Float.MAX_VALUE),
                    "The real lethal damage path must accept damage");
            helper.assertTrue(!victim.isAlive(), "The original villager must actually die");
            var pool = fixture.village.getReincarnationPool();
            helper.assertValueEqual(pool.size(), 1, "death records queued by the death event");
            var record = pool.getFirst();
            helper.assertValueEqual(record.getUuid(), identity, "queued identity");
            helper.assertValueEqual(record.getName(), "Remembered farmer", "queued custom name");
            helper.assertValueEqual(record.getDeathPos(), victim.blockPosition(), "queued death position");
            var next = fixture.queue(fixture.identity(2), "Next resident");

            // Remove the corpse before reusing its UUID, just as a later spawn would.
            victim.discard();
            helper.assertTrue(fixture.level.getEntity(identity) == null, "The corpse UUID must be unregistered");
            var reborn = fixture.create(fixture.identity(3), "New arrival");
            UUID arrivalId = reborn.getUUID();
            fixture.add(reborn);
            helper.assertValueEqual(reborn.getUUID(), identity, "restored identity");
            helper.assertValueEqual(reborn.getName().getString(), "Remembered farmer", "restored name");
            helper.assertTrue(fixture.level.getEntity(identity) == reborn,
                    "The actual server entity index must use the restored UUID");
            helper.assertTrue(fixture.level.getEntity(arrivalId) == null,
                    "The temporary spawn UUID must not remain indexed");
            helper.assertValueEqual(reborn.getMaxHealth(), 60.0F, "village resident maximum health");
            helper.assertValueEqual(reborn.getHealth(), 60.0F, "village resident current health");
            helper.assertTrue(!reborn.isBaby(), "This restored identity must remain an adult");
            helper.assertValueEqual(pool.size(), 1, "exactly one consumed record");
            helper.assertTrue(pool.getFirst() == next, "Reincarnation must consume the oldest record first");
            helper.assertTrue(ModEvents.getVillageDatabase(fixture.level).getData(fixture.village.getUUID())
                    .getReincarnationPool().getFirst() == next, "The database must retain the remaining queue");
            helper.assertTrue(ModEvents.getNpcDatabase(fixture.level).getData(identity) == npc,
                    "Adult reincarnation must retain the existing NPC personality record");
            helper.assertValueEqual(npc.getAiProfileFile(), "reincarnation-test-profile", "retained AI profile link");
            helper.assertValueEqual(npc.getPersonality(), "Patient", "retained personality");
        }
        helper.succeed();
    }

    private static void loadedUnchanged(GameTestHelper helper) {
        try (var fixture = new Fixture(helper)) {
            var queued = fixture.queue(fixture.identity(1), "Waiting resident");
            var loaded = fixture.create(fixture.identity(0), "Loaded resident");
            loaded.setHealth(7.0F);
            int age = loaded.getAge();
            float maxHealth = loaded.getMaxHealth();
            UUID identity = loaded.getUUID();
            // Exercise the disk-load discriminator without registering a fresh-spawn event first.
            var event = NeoForge.EVENT_BUS.post(new EntityJoinLevelEvent(loaded, fixture.level, true));
            helper.assertTrue(!event.isCanceled(), "Loading an existing villager must not be canceled");
            helper.assertValueEqual(loaded.getUUID(), identity, "loaded identity unchanged");
            helper.assertValueEqual(loaded.getName().getString(), "Loaded resident", "loaded name unchanged");
            helper.assertValueEqual(loaded.getHealth(), 7.0F, "loaded health unchanged");
            helper.assertValueEqual(loaded.getMaxHealth(), maxHealth, "loaded maximum health unchanged");
            helper.assertValueEqual(loaded.getAge(), age, "loaded age unchanged");
            fixture.assertOnlyQueued(queued);
            helper.assertTrue(ModEvents.getNpcDatabase(fixture.level).getData(identity) == null,
                    "Disk loading must not assign a forever-kid record, even for an eligible UUID");
            helper.assertTrue(ModEvents.getNpcDatabase(fixture.level).getData(queued.getUuid()) == null,
                    "Disk loading must not create NPC data for the waiting identity");
        }
        helper.succeed();
    }

    private static void unclaimedUnchanged(GameTestHelper helper) {
        try (var fixture = new Fixture(helper)) {
            var queued = fixture.queue(fixture.identity(1), "Village resident");
            ModEvents.getChunkDataDatabase(fixture.level).removeDataBatch(List.of(fixture.key));
            var outsider = fixture.create(fixture.identity(0), "Wilderness resident");
            UUID identity = outsider.getUUID();
            float maxHealth = outsider.getMaxHealth();
            fixture.add(outsider);
            helper.assertValueEqual(outsider.getUUID(), identity, "unclaimed spawn identity unchanged");
            helper.assertValueEqual(outsider.getName().getString(), "Wilderness resident", "unclaimed name unchanged");
            helper.assertValueEqual(outsider.getMaxHealth(), maxHealth, "unclaimed maximum health unchanged");
            helper.assertTrue(!outsider.isBaby(), "Unclaimed spawns must not become forever kids");
            helper.assertTrue(ModEvents.getNpcDatabase(fixture.level).getData(identity) == null,
                    "Unclaimed spawns must not create NPC data");
            fixture.assertOnlyQueued(queued);
            helper.assertTrue(outsider.hurtServer(fixture.level, outsider.damageSources().genericKill(), Float.MAX_VALUE),
                    "The wilderness villager must take lethal damage through the actual damage path");
            helper.assertTrue(!outsider.isAlive(), "The wilderness villager must actually die");
            fixture.assertOnlyQueued(queued);
        }
        helper.succeed();
    }

    private static void foreverKid(GameTestHelper helper) {
        try (var fixture = new Fixture(helper)) {
            var queued = fixture.queue(fixture.identity(0), "Young resident");
            var reborn = fixture.create(fixture.identity(1), "New arrival");
            fixture.add(reborn);
            helper.assertValueEqual(reborn.getUUID(), queued.getUuid(), "restored forever-kid identity");
            helper.assertTrue(fixture.level.getEntity(queued.getUuid()) == reborn,
                    "The restored child must be registered under its preserved identity");
            helper.assertTrue(fixture.village.getReincarnationPool().isEmpty(), "The child record must be consumed");
            helper.assertTrue(reborn.isBaby(), "Eligible restored identities must become children");
            var npc = ModEvents.getNpcDatabase(fixture.level).getData(queued.getUuid());
            helper.assertTrue(npc != null && npc.isForeverKid(), "The NPC database must persist the child marker");
            helper.assertValueEqual(npc.getName(), "Young resident", "child NPC name");
            helper.assertValueEqual(npc.getHomeVillageUUID(), fixture.village.getUUID(), "child NPC home village");
            reborn.setAge(-1);
            reborn.tickCount = 1;
            NeoForge.EVENT_BUS.post(new EntityTickEvent.Pre(reborn));
            helper.assertValueEqual(reborn.getAge(), -24000, "production tick must prevent the child growing up");
            helper.assertValueEqual(reborn.getHealth(), 60.0F, "child resident health");
        }
        helper.succeed();
    }

    private static final class Fixture implements AutoCloseable {
        final GameTestHelper helper;
        final ServerLevel level;
        final BlockPos pos;
        final long key;
        final VillageData village;
        private final ChunkData previousPlot;
        private final VillageData previousVillage;
        private final Map<UUID, NpcData> previousNpcs = new HashMap<>();
        private final List<Villager> entities = new ArrayList<>();

        Fixture(GameTestHelper helper) {
            this.helper = helper;
            level = helper.getLevel();
            pos = helper.absolutePos(new BlockPos(1, 1, 1));
            key = ChunkPos.pack(pos);
            previousPlot = ModEvents.getChunkDataDatabase(level).getData(key);
            UUID villageId = UUID.randomUUID();
            previousVillage = ModEvents.getVillageDatabase(level).getData(villageId);
            village = new VillageData(villageId, "Reincarnation fixture", ChunkPos.containing(pos), 1,
                    List.of(ChunkPos.containing(pos)), true, 0, "2000-01-01", "", "2000-01-01", "", List.of());
            ModEvents.getVillageDatabase(level).putData(villageId, village);
            ModEvents.getChunkDataDatabase(level).putData(key, new ChunkData("village", -1, villageId));
        }

        UUID identity(long discriminator) {
            // Production eligibility is floorMod(villager.lsb XOR village.msb, 5) == 0.
            UUID id = new UUID(UUID.randomUUID().getMostSignificantBits(),
                    village.getUUID().getMostSignificantBits() ^ discriminator);
            previousNpcs.put(id, ModEvents.getNpcDatabase(level).getData(id));
            return id;
        }

        Villager create(UUID id, String name) {
            var villager = EntityTypes.VILLAGER.create(level, EntitySpawnReason.COMMAND);
            helper.assertTrue(villager != null, "The villager entity factory must succeed");
            entities.add(villager);
            villager.setUUID(id);
            villager.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            villager.setCustomName(Component.literal(name));
            villager.setNoAi(true);
            villager.setAge(0);
            return villager;
        }

        void add(Villager villager) {
            helper.assertTrue(level.addFreshEntity(villager), "The real entity join path must accept the villager");
        }

        VillagerRecord queue(UUID id, String name) {
            var record = new VillagerRecord(id, name, pos);
            village.addToReincarnationPool(record);
            ModEvents.getVillageDatabase(level).putData(village.getUUID(), village);
            return record;
        }

        void assertOnlyQueued(VillagerRecord expected) {
            var pool = ModEvents.getVillageDatabase(level).getData(village.getUUID()).getReincarnationPool();
            helper.assertValueEqual(pool.size(), 1, "denied reincarnation queue size unchanged");
            helper.assertTrue(pool.getFirst() == expected, "Denied reincarnation must preserve the queued record");
        }

        @Override
        public void close() {
            entities.forEach(Villager::discard);
            var npcs = ModEvents.getNpcDatabase(level);
            previousNpcs.forEach((id, previous) -> {
                if (previous == null) {
                    npcs.removeDataBatch(List.of(id));
                } else {
                    npcs.putData(id, previous);
                }
            });
            var villages = ModEvents.getVillageDatabase(level);
            if (previousVillage == null) {
                villages.removeDataBatch(List.of(village.getUUID()));
            } else {
                villages.putData(village.getUUID(), previousVillage);
            }
            var plots = ModEvents.getChunkDataDatabase(level);
            if (previousPlot == null) {
                plots.removeDataBatch(List.of(key));
            } else {
                plots.putData(key, previousPlot);
            }
        }
    }
}
