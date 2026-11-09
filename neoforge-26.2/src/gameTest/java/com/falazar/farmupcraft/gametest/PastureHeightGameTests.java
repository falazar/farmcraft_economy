package com.falazar.farmupcraft.gametest;

import com.falazar.farmupcraft.AnimalsManager;
import com.falazar.farmupcraft.data.ChunkData;
import com.falazar.farmupcraft.data.PlayerData;
import com.falazar.farmupcraft.data.VillageData;
import com.falazar.farmupcraft.events.ModEvents;
import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = FarmUpCraftNeoForge.MOD_ID)
public final class PastureHeightGameTests {
    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, functions -> functions.register(
                FarmUpCraftNeoForge.prefix("pasture_height"), PastureHeightGameTests::verify));
    }

    private static void verify(GameTestHelper helper) {
        try (var f = new ResourcePlotFixture(helper)) {
            int base = 80;
            var chunks = ModEvents.getChunkDataDatabase(f.level);
            var villages = ModEvents.getVillageDatabase(f.level);
            var cleanup = new ArrayList<Entity>();
            UUID id = UUID.randomUUID();
            var village = new VillageData(id, "Height test", f.player.chunkPosition(), 8,
                    List.of(f.player.chunkPosition()), true, 250, "", "cow", "",
                    f.player.getName().getString(), List.of(f.player.getUUID()));
            village.setAnimalGrainsFromMap(Map.of("cow", List.of("minecraft:wheat")));
            villages.putData(id, village);
            ModEvents.getPlayerDatabase(f.level).putData(f.player.getUUID(),
                    new PlayerData(f.player.getId(), f.player.getUUID(), id, 1000));
            var pasture = new ChunkData("pasture", f.player.getId(), id, f.player.getUUID(), List.of(), base);
            pasture.setPlotLevel(4);
            var encoded = ChunkData.CODEC.encodeStart(JsonOps.INSTANCE, pasture).getOrThrow();
            pasture = ChunkData.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
            helper.assertValueEqual(pasture.getBoughtY(), base, "Purchase base survives save/load");
            chunks.putData(f.key, pasture);
            try {
                for (int offset : new int[]{-7, -6, 0, 6, 7}) {
                    var cow = EntityTypes.COW.create(f.level, EntitySpawnReason.TRIGGERED);
                    helper.assertTrue(cow != null, "Count cow created");
                    cow.setPos(f.pos.getX() + 0.5, base + offset, f.pos.getZ() + 0.5);
                    cow.setNoAi(true);
                    helper.assertTrue(f.level.addFreshEntity(cow), "Count cow added");
                    cleanup.add(cow);
                }
                for (int viewerY : new int[]{base - 40, base - 6, base, base + 6, base + 40}) {
                    helper.assertValueEqual(AnimalsManager.countAnimalsInChunk(f.level, f.pos.atY(viewerY)), 3,
                            "Fixed band includes both boundaries, excludes body overlap, ignores query Y");
                    helper.assertValueEqual(AnimalsManager.pastureAnimalBreakdown(f.level, f.pos.atY(viewerY)),
                            "Cow: 3", "Breakdown uses exactly the same fixed band");
                }
                cleanup.forEach(Entity::discard);
                cleanup.clear();
                for (int offset : new int[]{-7, -6, 6, 7}) {
                    boolean inside = Math.abs(offset) <= 6;
                    var pos = f.pos.atY(base + offset);
                    f.player.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
                    helper.assertValueEqual(AnimalsManager.isActivePasture(f.level, pos), inside, "Pasture height boundary");
                    var cow = EntityTypes.COW.create(f.level, EntitySpawnReason.TRIGGERED);
                    var sheep = EntityTypes.SHEEP.create(f.level, EntitySpawnReason.TRIGGERED);
                    var chicken = EntityTypes.CHICKEN.create(f.level, EntitySpawnReason.TRIGGERED);
                    helper.assertTrue(cow != null && sheep != null && chicken != null, "Interaction animals created");
                    cleanup.addAll(List.of(cow, sheep, chicken));
                    for (var animal : List.of(cow, sheep, chicken)) {
                        animal.setPos(f.player.position());
                        animal.setAge(0);
                        animal.setNoAi(true);
                    }
                    f.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET));
                    var milk = interaction(f, cow);
                    AnimalsManager.onRightClickCow(milk);
                    helper.assertValueEqual(milk.isCanceled(), !inside, "Milk requires active height band");
                    helper.assertValueEqual(cow.getPersistentData().contains("farmupcraft_last_milked"), inside,
                            "Outside milk does not set daily marker");

                    f.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WHEAT, 8));
                    AnimalsManager.onRightClickBreeding(interaction(f, cow));
                    helper.assertValueEqual(cow.isInLove(), inside, "Breeding only activates within height band");
                    helper.assertValueEqual(f.player.getMainHandItem().getCount(), inside ? 7 : 8,
                            "Outside breeding does not consume feed");

                    f.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SHEARS));
                    var dropBounds = new net.minecraft.world.phys.AABB(pos).inflate(2);
                    var beforeDrops = f.level.getEntitiesOfClass(ItemEntity.class, dropBounds).stream()
                            .map(Entity::getUUID).toList();
                    var shear = interaction(f, sheep);
                    AnimalsManager.onRightClickSheepShearing(shear);
                    var bonusWool = f.level.getEntitiesOfClass(ItemEntity.class, dropBounds,
                            item -> !beforeDrops.contains(item.getUUID()));
                    cleanup.addAll(bonusWool);
                    helper.assertValueEqual(bonusWool.stream().mapToInt(item -> item.getItem().getCount()).sum(),
                            inside ? 3 : 0, "Bonus wool only inside height band");
                    helper.assertValueEqual(shear.isCanceled(), !inside, "Shearing requires active pasture or wilderness");
                    helper.assertValueEqual(sheep.getPersistentData().contains("farmupcraft_last_sheared_tick"), inside,
                            "Outside shearing does not set regrowth marker");
                    sheep.getPersistentData().putLong("farmupcraft_last_sheared_tick", f.level.getGameTime());
                    sheep.setSheared(false);
                    AnimalsManager.onSheepTick(new EntityTickEvent.Post(sheep));
                    helper.assertValueEqual(sheep.isSheared(), inside, "Regrowth enforcement inactive outside height band");

                    sheep.setSheared(false);
                    sheep.setColor(DyeColor.WHITE);
                    f.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DYE.pick(DyeColor.RED), 3));
                    long seed = 0;
                    do { f.level.getRandom().setSeed(seed++); } while (f.level.getRandom().nextInt(100) < 80);
                    f.level.getRandom().setSeed(seed - 1);
                    var dye = interaction(f, sheep);
                    AnimalsManager.onRightClickSheepDye(dye);
                    helper.assertValueEqual(dye.isCanceled(), inside, "Pasture dye-chance check inactive outside band");
                    helper.assertValueEqual(f.player.getMainHandItem().getCount(), inside ? 2 : 3,
                            "No failed-dye consumption outside band");

                    var drop = new ItemEntity(f.level, pos.getX(), pos.getY(), pos.getZ(), new ItemStack(Items.BEEF));
                    cleanup.add(drop);
                    AnimalsManager.onPastureAnimalDrops(new LivingDropsEvent(cow, f.level.damageSources().generic(),
                            List.of(drop), false));
                    helper.assertValueEqual(drop.getItem().getCount(), inside ? 4 : 1, "Loot bonus only inside band");
                    helper.assertValueEqual(AnimalsManager.eggLayingTime(chicken, 1) == 1, inside,
                            "Pasture egg-laying permission only inside band");
                    helper.assertValueEqual(AnimalsManager.addEggHatchling(f.level, chicken), inside,
                            "Egg hatchlings require active band");
                    cow.discard();
                    sheep.discard();
                    chicken.discard();
                }
                for (int i = 0; i < 30; i++) {
                    var cow = EntityTypes.COW.create(f.level, EntitySpawnReason.TRIGGERED);
                    helper.assertTrue(cow != null, "Capacity cow created");
                    cow.setPos(f.pos.getX() + 0.5, base - 6, f.pos.getZ() + 0.5);
                    cow.setNoAi(true);
                    helper.assertTrue(f.level.addFreshEntity(cow), "Capacity cow added");
                    cleanup.add(cow);
                }
                var highPos = f.pos.atY(base + 6);
                helper.assertValueEqual(AnimalsManager.countAnimalsInChunk(f.level, highPos), 30,
                        "Upper-bound interactions count lower-bound animals for capacity");
                var hatchling = EntityTypes.CHICKEN.create(f.level, EntitySpawnReason.TRIGGERED);
                helper.assertTrue(hatchling != null, "Capacity hatchling created");
                hatchling.setPos(highPos.getX() + 0.5, highPos.getY(), highPos.getZ() + 0.5);
                cleanup.add(hatchling);
                helper.assertTrue(!AnimalsManager.addEggHatchling(f.level, hatchling),
                        "Cannot bypass full lower pasture by hatching at upper boundary");
                var warnings = new ArrayList<String>();
                var thrower = new net.neoforged.neoforge.common.util.FakePlayer(f.level,
                        new com.mojang.authlib.GameProfile(UUID.randomUUID(), "height_egg")) {
                    @Override
                    public void sendSystemMessage(net.minecraft.network.chat.Component message) {
                        warnings.add(message.getString());
                    }
                };
                cleanup.add(thrower);
                for (int offset : new int[]{-7, -6, 6, 7}) {
                    var egg = EntityTypes.EGG.create(f.level, EntitySpawnReason.TRIGGERED);
                    helper.assertTrue(egg != null, "Warning egg created");
                    cleanup.add(egg);
                    egg.setOwner(thrower);
                    egg.setPos(f.pos.getX() + 0.5, base + offset, f.pos.getZ() + 0.5);
                    warnings.clear();
                    AnimalsManager.warnFullPastureEgg(egg);
                    helper.assertValueEqual(warnings.size(), Math.abs(offset) <= 6 ? 1 : 0,
                            "Full-pasture warning only triggers inside inclusive height band");
                }
            } finally {
                cleanup.forEach(Entity::discard);
                villages.removeDataBatch(List.of(id));
            }
        }
        helper.succeed();
    }

    private static PlayerInteractEvent.EntityInteract interaction(ResourcePlotFixture f, Entity animal) {
        return new PlayerInteractEvent.EntityInteract(f.player, InteractionHand.MAIN_HAND, animal, Vec3.ZERO);
    }
}
