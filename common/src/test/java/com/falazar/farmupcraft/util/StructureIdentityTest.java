package com.falazar.farmupcraft.util;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class StructureIdentityTest {
    @Test
    void typesSharingTheActualCabinStartChunkGetSeparateStableIds() {
        long start = -103079215078L;
        var cabin = new StructureIdentity("alexscaves:underground_cabin", start);
        var grave = new StructureIdentity("graveyard:small_grave", start);
        Map<Long, StructureIdentity> records = new HashMap<>();
        long cabinId = cabin.resolveId(records::get);
        records.put(cabinId, cabin);
        long graveId = grave.resolveId(records::get);
        records.put(graveId, grave);
        assertNotEquals(cabinId, graveId);
        assertEquals(cabinId, cabin.resolveId(records::get));
        assertEquals(graveId, grave.resolveId(records::get));
        assertEquals(cabinId, cabin.resolveId(Map.<Long, StructureIdentity>of()::get));
        assertEquals(graveId, grave.resolveId(Map.<Long, StructureIdentity>of()::get));
        assertNotEquals(cabinId, new StructureIdentity(cabin.type(), start + 1).resolveId(records::get));
    }

    @Test
    void existingLegacyRecordKeepsItsIdAndDoesNotMaskOtherTypes() {
        long start = -103079215078L;
        var grave = new StructureIdentity("graveyard:small_grave", start);
        var cabin = new StructureIdentity("alexscaves:underground_cabin", start);
        Map<Long, StructureIdentity> records = new HashMap<>(Map.of(start, grave));
        long id = cabin.resolveId(records::get);
        records.put(id, cabin);
        assertNotEquals(start, id);
        assertEquals(start, grave.resolveId(records::get));
        assertEquals(id, cabin.resolveId(records::get));
        assertEquals(grave, records.get(start));
    }

    @Test
    void occupiedHashSlotsAreNeverOverwrittenEvenForTheSameType() {
        var target = new StructureIdentity("test:cabin", Long.MIN_VALUE);
        long first = target.resolveId(key -> null);
        Map<Long, StructureIdentity> records = new HashMap<>();
        records.put(first, new StructureIdentity(target.type(), 7));
        records.put(first + 1, new StructureIdentity("test:grave", target.startChunk()));
        long id = target.resolveId(records::get);
        assertEquals(first + 2, id);
        records.put(id, target);
        assertEquals(id, target.resolveId(records::get));
        assertEquals(3, records.size());
    }
}
