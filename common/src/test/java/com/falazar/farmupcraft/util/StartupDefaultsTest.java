package com.falazar.farmupcraft.util;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class StartupDefaultsTest {
    @TempDir Path directory;

    @Test
    void upgradesV1OnceWithoutReplacingItsBackup() throws Exception {
        Path state = directory.resolve("farmupcraft");
        Files.createDirectories(state);
        Files.writeString(state.resolve("solonion-progression-v1.done"), "v1 installed");
        Files.writeString(state.resolve("solonion-before-progression-v1.json"), "original backup");
        String old = "{\"trackCount\":42,\"benefits\":[],\"resetOnDeath\":false}";
        Files.writeString(directory.resolve("solonion.json"), old);
        assertTrue(StartupDefaults.installOnion(directory, true));
        assertEquals(old, Files.readString(state.resolve("solonion-before-progression-v2.json")));
        assertEquals("original backup", Files.readString(state.resolve("solonion-before-progression-v1.json")));
        assertTrue(Files.exists(state.resolve("solonion-progression-v2.done")));
        assertFalse(StartupDefaults.installOnion(directory, true));
    }

    @Test
    void onionInstallsOnceBacksUpAndPreservesUnrelatedAndLaterEdits() throws Exception {
        Path target = directory.resolve("solonion.json");
        String old = "{\"trackCount\":32,\"resetOnDeath\":false,\"complexityStandardSaturation\":6}";
        Files.writeString(target, old);
        assertTrue(StartupDefaults.installOnion(directory, true));
        var json = JsonParser.parseString(Files.readString(target)).getAsJsonObject();
        assertEquals(62, json.get("trackCount").getAsInt());
        assertFalse(json.get("resetOnDeath").getAsBoolean());
        assertEquals(6, json.get("complexityStandardSaturation").getAsInt());
        var rewards = json.getAsJsonArray("benefits");
        assertEquals(8, rewards.size());
        int[] thresholds = {20, 25, 27, 33, 38, 50, 55, 61};
        for (int i = 0; i < thresholds.length; i++) {
            assertEquals(thresholds[i], rewards.get(i).getAsJsonObject().get("threshold").getAsInt());
        }
        assertEquals(61, rewards.get(7).getAsJsonObject().get("threshold").getAsInt());
        assertTrue(rewards.get(7).getAsJsonObject().get("benefit").getAsString().contains("val:6.0d"));
        assertEquals(old, Files.readString(directory.resolve("farmupcraft/solonion-before-progression-v2.json")));
        Files.writeString(target, "{\"trackCount\":99}");
        assertFalse(StartupDefaults.installOnion(directory, true));
        assertEquals("{\"trackCount\":99}", Files.readString(target));
    }

    @Test
    void invalidExistingConfigIsNotOverwrittenOrMarkedComplete() throws Exception {
        Path target = directory.resolve("solonion.json");
        Files.writeString(target, "{broken");
        assertThrows(com.google.gson.JsonParseException.class,
                () -> StartupDefaults.installOnion(directory, true));
        assertEquals("{broken", Files.readString(target));
        assertFalse(Files.exists(directory.resolve("farmupcraft/solonion-progression-v2.done")));
    }

    @Test
    void freshLegacyInstallUsesLegacyBenefitFormat() throws Exception {
        assertTrue(StartupDefaults.installOnion(directory, false));
        var json = JsonParser.parseString(Files.readString(directory.resolve("solonion.json"))).getAsJsonObject();
        assertEquals("minecraft:generic.max_health", json.getAsJsonArray("benefits").get(0)
                .getAsJsonObject().getAsJsonObject("benefit").get("attribute").getAsString());
        assertEquals(62, json.get("trackCount").getAsInt());
        int[] oldThresholds = {10, 15, 17, 23, 28, 30, 35, 41};
        for (int i = 0; i < oldThresholds.length; i++) {
            assertEquals(oldThresholds[i] + (i >= 5 ? 20 : 10),
                    json.getAsJsonArray("benefits").get(i).getAsJsonObject().get("threshold").getAsInt());
        }
    }

    @Test
    void eachWorldInitializesOnceAndFailedInitializationCanRetry() throws Exception {
        var calls = new AtomicInteger();
        Path first = directory.resolve("first"), second = directory.resolve("second");
        assertTrue(StartupDefaults.initializeWorld(first, calls::incrementAndGet));
        assertFalse(StartupDefaults.initializeWorld(first, calls::incrementAndGet));
        assertThrows(IllegalStateException.class, () -> StartupDefaults.initializeWorld(second, () -> {
            throw new IllegalStateException("test failure");
        }));
        assertTrue(StartupDefaults.initializeWorld(second, calls::incrementAndGet));
        assertEquals(2, calls.get());
    }
}
