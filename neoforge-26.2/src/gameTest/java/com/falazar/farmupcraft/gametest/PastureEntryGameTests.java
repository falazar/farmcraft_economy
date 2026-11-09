package com.falazar.farmupcraft.gametest;

import com.falazar.farmupcraft.AnimalsManager;
import com.falazar.farmupcraft.ChunkManager;
import com.falazar.farmupcraft.data.ChunkData;
import com.falazar.farmupcraft.events.ModEvents;
import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import com.mojang.authlib.GameProfile;
import net.minecraft.ChatFormatting;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.EntityEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@EventBusSubscriber(modid = FarmUpCraftNeoForge.MOD_ID)
public final class PastureEntryGameTests {
    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, functions -> functions.register(
                FarmUpCraftNeoForge.prefix("pasture_entry"), PastureEntryGameTests::verify));
    }

    private static void verify(GameTestHelper helper) {
        try (var f = new ResourcePlotFixture(helper)) {
            var chunks = ModEvents.getChunkDataDatabase(f.level);
            var messages = new ArrayList<Component>();
            var player = new FakePlayer(f.level, new GameProfile(UUID.randomUUID(), "pasture_notice")) {
                @Override
                public void sendSystemMessage(Component message) { messages.add(message); }
            };
            var animals = new ArrayList<Entity>();
            try {
                player.setPos(f.pos.getX() + 0.5, f.pos.getY(), f.pos.getZ() + 0.5);
                var section = SectionPos.of(player.blockPosition());
                var outside = SectionPos.of(section.x() - 1, section.y(), section.z());
                var above = SectionPos.of(section.x(), section.y() + 1, section.z());
                var entering = new EntityEvent.EnteringSection(player, outside.asLong(), section.asLong());
                chunks.putData(f.key, new ChunkData("farm", -1, UUID.randomUUID()));
                ChunkManager.onPlayerEnterChunk(entering);
                helper.assertTrue(notices(messages).isEmpty(), "Farm entry must not show pasture count");
                var pasture = new ChunkData("pasture", -1, UUID.randomUUID());
                pasture.setBoughtY(f.pos.getY());
                chunks.putData(f.key, pasture);
                for (int offset : new int[]{-7, 7, 40}) {
                    player.setPos(f.pos.getX() + 0.5, f.pos.getY() + offset, f.pos.getZ() + 0.5);
                    ChunkManager.onPlayerEnterChunk(entering);
                    helper.assertTrue(notices(messages).isEmpty(), "Entry outside height band must not notify or consume cooldown");
                }
                for (int offset : new int[]{-6, 6}) {
                    player.setPos(f.pos.getX() + 0.5, f.pos.getY() + offset, f.pos.getZ() + 0.5);
                    ChunkManager.onPlayerEnterChunk(entering);
                    helper.assertValueEqual(notices(messages).size(), 1, "Inclusive height boundary notifies");
                    ChunkManager.clearTransientState();
                    messages.clear();
                }
                player.setPos(f.pos.getX() + 0.5, f.pos.getY(), f.pos.getZ() + 0.5);
                ChunkManager.onPlayerEnterChunk(new EntityEvent.EnteringSection(player, above.asLong(), section.asLong()));
                helper.assertTrue(notices(messages).isEmpty(), "Vertical-only movement must not show or consume cooldown");
                for (int count : new int[]{0, 29, 30, 31}) {
                    animals.forEach(Entity::discard);
                    animals.clear();
                    for (int i = 0; i < count; i++) {
                        var animal = EntityTypes.COW.create(f.level, EntitySpawnReason.TRIGGERED);
                        helper.assertTrue(animal != null, "Animal fixture exists");
                        animal.setPos(player.position());
                        animal.setNoAi(true);
                        helper.assertTrue(f.level.addFreshEntity(animal), "Animal fixture added");
                        animals.add(animal);
                    }
                    helper.assertValueEqual(AnimalsManager.countAnimalsInChunk(f.level, player.blockPosition()), count,
                            "Exact measured pasture animal count");
                    ChunkManager.clearTransientState();
                    messages.clear();
                    ChunkManager.onPlayerEnterChunk(entering);
                    var notices = notices(messages);
                    helper.assertValueEqual(notices.size(), 1, "One pasture message on horizontal entry");
                    String expected = "Pasture: " + count + "/30 animals" + (count >= 30 ? " - FULL (at maximum)." : ".");
                    helper.assertValueEqual(notices.getFirst().getString(), expected, "Count and fullness text");
                    helper.assertValueEqual(notices.getFirst().getStyle().getColor(),
                            TextColor.fromLegacyFormat(count >= 30 ? ChatFormatting.RED : ChatFormatting.GREEN),
                            "Full pasture red; available capacity green");
                    ChunkManager.onPlayerEnterChunk(entering);
                    helper.assertValueEqual(notices(messages).size(), 1, "Reentry during cooldown must not spam");
                    ChunkManager.onPlayerEnterChunk(new EntityEvent.EnteringSection(player, section.asLong(), above.asLong()));
                    helper.assertValueEqual(notices(messages).size(), 1, "Jumping does not send another notice");
                }
            } finally {
                animals.forEach(Entity::discard);
                player.discard();
                ChunkManager.clearTransientState();
            }
        }
        helper.succeed();
    }

    private static List<Component> notices(List<Component> messages) {
        return messages.stream().filter(m -> m.getString().startsWith("Pasture:")).toList();
    }
}
