package com.falazar.farmupcraft.database.backup;

import net.minecraft.server.MinecraftServer;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;

@ExtendWith(EphemeralTestServerProvider.class)
class BackupSchedulerTest {
    @Test
    void registryOnlyServerTicksDeferWorldDependentBackups(MinecraftServer server) {
        assertNull(server.overworld());
        assertDoesNotThrow(() -> BackupScheduler.processServer(server));
        assertDoesNotThrow(() -> BackupScheduler.processServer(server));
    }
}
