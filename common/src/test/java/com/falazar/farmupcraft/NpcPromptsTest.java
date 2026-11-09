package com.falazar.farmupcraft;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class NpcPromptsTest {
    @Test void createsEditableDefaultsAndReloadsWithoutOverwriting(@TempDir Path config) throws Exception {
        String base = NpcPrompts.base(config, "Laquita", "A gardener", "curious", "falazary", "Hello");
        assertTrue(base.contains("Laquita"));
        assertTrue(base.contains("curious"));
        Path file = config.resolve("farmupcraft").resolve("prompts").resolve("npc-conversation.txt");
        String custom = "Custom {npc_name} {description} {personality} {player_name} {player_message}";
        Files.writeString(file, custom);
        assertTrue(NpcPrompts.base(config, "Laquita", "", "", "falazary", "Hi").startsWith("Custom"));
        assertEquals(custom, Files.readString(file));
        String gemPrompt = NpcPrompts.gems(config, "RUMOR", "Fact");
        assertTrue(gemPrompt.contains("AUTHORITATIVE PERMITTED FACT: Fact"));
        assertTrue(gemPrompt.contains("BOTH biome names"));
        assertFalse(gemPrompt.contains("You know absolutely nothing"));
        assertFalse(gemPrompt.contains("Preserve its direction exactly"));
        assertFalse(gemPrompt.contains("including any minus sign"));
        Files.writeString(file, "");
        assertThrows(IllegalStateException.class, () -> NpcPrompts.base(config, "Laquita", "", "", "falazary", "Hi"));
    }

    @Test void placeholderValidationAndInsertedTextIsNotReinterpreted() {
        assertEquals("hello {npc_name} $1", NpcPrompts.render("{player_message}",
                Map.of("player_message", "hello {npc_name} $1")));
        assertThrows(IllegalArgumentException.class, () -> NpcPrompts.render("no slots", Map.of("npc_name", "Laquita")));
        assertThrows(IllegalArgumentException.class, () -> NpcPrompts.render("{npc_name} {typo}", Map.of("npc_name", "Laquita")));
    }
}
