package com.falazar.farmupcraft.database;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class DataBaseClientCacheTest {
    @Test
    void eachAccessKeepsAnIndependentCacheForTheActualLevelIdentity() {
        List<String> disposed = new ArrayList<>();
        AtomicInteger created = new AtomicInteger();
        var first = new DataBaseAccess.ClientCache<Object, String>(
                level -> "first-" + created.incrementAndGet(), disposed::add);
        var second = new DataBaseAccess.ClientCache<Object, String>(
                level -> "second-" + created.incrementAndGet(), disposed::add);
        Object level = new Object();
        String firstDatabase = first.get(level);
        String secondDatabase = second.get(level);
        assertSame(firstDatabase, first.get(level));
        assertSame(secondDatabase, second.get(level));
        Object replacement = new Object();
        String replacementDatabase = first.get(replacement);
        assertNotEquals(firstDatabase, replacementDatabase);
        assertEquals(List.of(firstDatabase), disposed);
        assertSame(secondDatabase, second.get(level));
        assertEquals(3, created.get());
    }

    @Test
    void disconnectDisposesOnceAndReconnectingCreatesAFreshDatabase() {
        List<Object> disposed = new ArrayList<>();
        var cache = new DataBaseAccess.ClientCache<Object, Object>(level -> new Object(), disposed::add);
        Object level = new Object();
        Object initial = cache.get(level);
        cache.clear();
        cache.clear();
        assertEquals(List.of(initial), disposed);
        Object reconnected = cache.get(level);
        assertNotSame(initial, reconnected);
        assertThrows(NullPointerException.class, () -> cache.get(null));
        assertSame(reconnected, cache.get(level));
    }

    @Test
    void equalButDistinctLevelObjectsStillInvalidateCache() {
        var cache = new DataBaseAccess.ClientCache<String, Object>(level -> new Object(), ignored -> {});
        String first = new String("level");
        String second = new String("level");
        assertEquals(first, second);
        assertNotSame(cache.get(first), cache.get(second));
    }
}
