package com.falazar.farmupcraft;

import com.falazar.farmupcraft.data.GameStructureData;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class StructureManagerTest {
    @AfterEach
    void clearCaches() {
        StructureManager.clearCaches();
    }

    @Test
    void canceledBreakDoesNotInspectTheWorldOrTriggerTrapsAndRunsAfterNormalProtectionHandlers() throws Exception {
        var subscription = StructureManager.class.getMethod("onSpecialChestBreak", BreakBlockEvent.class)
                .getAnnotation(SubscribeEvent.class);
        assertNotNull(subscription);
        assertEquals(EventPriority.LOWEST, subscription.priority());
        assertFalse(subscription.receiveCanceled());
        var event = new BreakBlockEvent(null, BlockPos.ZERO, Blocks.SPAWNER.defaultBlockState(), null) {
            @Override
            public LevelAccessor getLevel() {
                fail("A canceled break must not inspect the world or spawn anything");
                return null;
            }
        };
        event.setCanceled(true);
        assertDoesNotThrow(() -> StructureManager.onSpecialChestBreak(event));
    }

    @Test
    void bookUsesWrittenBookComponentsAndRoundTripsInRegistryContext(MinecraftServer server) {
        String text = "Leave \"now\".\nThe seal belongs to me — not you. 雪";
        ItemStack book = StructureManager.createNoteBook("A Strange Note", text);
        assertTrue(book.is(Items.WRITTEN_BOOK));
        assertEquals(1, book.getCount());
        var content = book.get(DataComponents.WRITTEN_BOOK_CONTENT);
        assertNotNull(content);
        assertEquals("A Strange Note", content.title().raw());
        assertEquals("???", content.author());
        assertEquals(0, content.generation());
        assertFalse(content.resolved());
        assertEquals(1, content.pages().size());
        assertEquals(text, content.pages().getFirst().raw().getString());
        var jsonOps = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        var json = ItemStack.CODEC.encodeStart(jsonOps, book).getOrThrow();
        var fromJson = ItemStack.CODEC.parse(jsonOps, json).getOrThrow();
        assertTrue(ItemStack.matches(book, fromJson));
        var nbtOps = server.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        var nbt = ItemStack.CODEC.encodeStart(nbtOps, book).getOrThrow();
        assertTrue(ItemStack.matches(book, ItemStack.CODEC.parse(nbtOps, nbt).getOrThrow()));
    }

    @Test
    void recognizesOnlyVanillaChestLikeBlocksAndAbsentOptionalMobIdsStayAbsent() {
        for (var block : List.of(Blocks.CHEST, Blocks.TRAPPED_CHEST, Blocks.BARREL))
            assertTrue(StructureManager.isStructureChest(block));
        for (var block : List.of(Blocks.STONE, Blocks.ENDER_CHEST, Blocks.SHULKER_BOX))
            assertFalse(StructureManager.isStructureChest(block));
        assertSame(EntityTypes.ZOMBIE, StructureManager.registeredEntityType(Identifier.parse("minecraft:zombie")));
        assertNull(StructureManager.registeredEntityType(Identifier.parse("farmupcraft:missing_trap_mob")));
        assertNull(StructureManager.registeredEntityType(null));
    }

    @Test
    void readsModernAndLegacySpawnerShapesAndRejectsBadIdsWithoutSpawningAnything() {
        assertNull(StructureManager.readSpawnerEntityId(new CompoundTag()));
        var outer = new CompoundTag();
        var spawnData = new CompoundTag();
        var entity = new CompoundTag();
        entity.putString("id", "minecraft:skeleton");
        spawnData.put("entity", entity);
        outer.put("SpawnData", spawnData);
        assertEquals(Identifier.parse("minecraft:skeleton"), StructureManager.readSpawnerEntityId(outer));
        outer.put("SpawnData", entity);
        assertEquals(Identifier.parse("minecraft:skeleton"), StructureManager.readSpawnerEntityId(outer));
        entity.putString("id", "INVALID ID");
        assertNull(StructureManager.readSpawnerEntityId(outer));
        entity.putInt("id", 1);
        assertNull(StructureManager.readSpawnerEntityId(outer));
        outer.putString("SpawnData", "wrong type");
        assertNull(StructureManager.readSpawnerEntityId(outer));
    }

    @Test
    void bboxLookupIncludesEveryFaceAndRejectsOutsideAndUnsetBounds() {
        var sd = new GameStructureData(1L, "Tower", new BlockPos(-1, 0, 0), "tower");
        assertFalse(StructureManager.containsPosition(sd, BlockPos.ZERO));
        sd.setMinPos(new BlockPos(-16, -64, -32));
        sd.setMaxPos(new BlockPos(15, 320, -1));
        for (var pos : List.of(sd.getMinPos(), sd.getMaxPos(), new BlockPos(-1, 0, -16),
                new BlockPos(-16, 0, -16), new BlockPos(15, 0, -16),
                new BlockPos(-1, -64, -16), new BlockPos(-1, 320, -16),
                new BlockPos(-1, 0, -32), new BlockPos(-1, 0, -1)))
            assertTrue(StructureManager.containsPosition(sd, pos), pos.toString());
        for (var pos : List.of(new BlockPos(-17, 0, -16), new BlockPos(16, 0, -16),
                new BlockPos(-1, -65, -16), new BlockPos(-1, 321, -16),
                new BlockPos(-1, 0, -33), new BlockPos(-1, 0, 0)))
            assertFalse(StructureManager.containsPosition(sd, pos), pos.toString());
    }

    @Test
    void cooldownIsChunkLocalDimensionScopedExpiresAfterFiveMinutesAndClearsOnStop() {
        StructureManager.clearCaches();
        BlockPos pos = new BlockPos(-16, 40, -16);
        StructureManager.rememberTrap(Level.OVERWORLD, pos, 1000);
        assertTrue(StructureManager.wasRecentTrap(Level.OVERWORLD, pos, 1000));
        assertTrue(StructureManager.wasRecentTrap(Level.OVERWORLD, new BlockPos(-1, 99, -1), 1000));
        assertFalse(StructureManager.wasRecentTrap(Level.OVERWORLD, new BlockPos(0, 40, -16), 1000));
        assertFalse(StructureManager.wasRecentTrap(Level.NETHER, pos, 1000));
        assertTrue(StructureManager.wasRecentTrap(Level.OVERWORLD, pos, 301000));
        assertFalse(StructureManager.wasRecentTrap(Level.OVERWORLD, pos, 301001));
        StructureManager.rememberTrap(Level.NETHER, pos, 400000);
        StructureManager.clearCaches();
        assertFalse(StructureManager.wasRecentTrap(Level.NETHER, pos, 400001));
    }

    @Test
    void failureClassifierCoversTimeoutBlankAndServiceErrorTextWithoutRejectingValidNotes() {
        for (String text : List.of("", " \n ", " [AI Error] refused ", "AI Error: timeout",
                "The Ollama returned HTTP 500 response"))
            assertTrue(StructureManager.isFailedNote(text, null), text);
        assertTrue(StructureManager.isFailedNote(null, null));
        assertTrue(StructureManager.isFailedNote("valid text", new java.util.concurrent.TimeoutException()));
        assertFalse(StructureManager.isFailedNote("  You trespass upon my realm.  ", null));
    }
}
