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
    void onionInstallsOnceBacksUpAndPreservesUnrelatedAndLaterEdits() throws Exception {
        Path target = directory.resolve("solonion.json");
        String old = "{\"trackCount\":32,\"resetOnDeath\":false,\"complexityStandardSaturation\":6}";
        Files.writeString(target, old);
        assertTrue(StartupDefaults.installOnion(directory, true));
        var json = JsonParser.parseString(Files.readString(target)).getAsJsonObject();
        assertEquals(42, json.get("trackCount").getAsInt());
        assertFalse(json.get("resetOnDeath").getAsBoolean());
        assertEquals(6, json.get("complexityStandardSaturation").getAsInt());
        var rewards = json.getAsJsonArray("benefits");
        assertEquals(8, rewards.size());
        int[] thresholds = {10, 15, 17, 23, 28, 30, 35, 41};
        for (int i = 0; i < thresholds.length; i++) {
            assertEquals(thresholds[i], rewards.get(i).getAsJsonObject().get("threshold").getAsInt());
        }
        assertEquals(41, rewards.get(7).getAsJsonObject().get("threshold").getAsInt());
        assertTrue(rewards.get(7).getAsJsonObject().get("benefit").getAsString().contains("val:6.0d"));
        assertEquals(old, Files.readString(directory.resolve("farmupcraft/solonion-before-progression-v1.json")));
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
        assertFalse(Files.exists(directory.resolve("farmupcraft/solonion-progression-v1.done")));
    }

    @Test
    void freshLegacyInstallUsesLegacyBenefitFormat() throws Exception {
        assertTrue(StartupDefaults.installOnion(directory, false));
        var json = JsonParser.parseString(Files.readString(directory.resolve("solonion.json"))).getAsJsonObject();
        assertEquals("minecraft:generic.max_health", json.getAsJsonArray("benefits").get(0)
                .getAsJsonObject().getAsJsonObject("benefit").get("attribute").getAsString());
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
