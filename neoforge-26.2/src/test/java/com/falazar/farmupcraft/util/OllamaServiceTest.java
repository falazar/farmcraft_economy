package com.falazar.farmupcraft.util;

import com.google.gson.JsonParser;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class OllamaServiceTest {
    @AfterEach
    void resetLifecycle() {
        OllamaService.shutdown();
        OllamaService.start();
    }

    @Test
    void requestIsOneNonStreamingUserMessageWithEscapedUnicodeAndExactModel() {
        String prompt = "A \"quote\", a slash \\, newline\nand 雪";
        var body = JsonParser.parseString(OllamaService.buildChatRequest("llama3.2:3b", prompt)).getAsJsonObject();
        assertEquals(3, body.size());
        assertEquals(OllamaService.DEFAULT_MODEL, body.get("model").getAsString());
        assertFalse(body.get("stream").getAsBoolean());
        var messages = body.getAsJsonArray("messages");
        assertEquals(1, messages.size());
        var message = messages.get(0).getAsJsonObject();
        assertEquals(2, message.size());
        assertEquals("user", message.get("role").getAsString());
        assertEquals(prompt, message.get("content").getAsString());
    }

    @Test
    void parserReturnsOnlyAssistantContentAndPreservesWhitespaceAndUnicode() {
        assertEquals("  Leave \"now\".\n雪  ", OllamaService.parseContentFromResponse(
                "{\"model\":\"unused\",\"message\":{\"role\":\"assistant\",\"content\":\"  Leave \\\"now\\\".\\n雪  \"},\"done\":true}"));
        assertEquals("", OllamaService.parseContentFromResponse("{\"message\":{\"content\":\"\"}}"));
        for (String malformed : new String[] { "not json", "{}", "{\"message\":{}}", "{\"message\":null}" })
            assertThrows(RuntimeException.class, () -> OllamaService.parseContentFromResponse(malformed));
    }

    @Test
    void stoppedServiceRejectsRequestsSynchronouslyWithoutOpeningAnyConnection() {
        OllamaService.shutdown();
        for (var reply : new java.util.concurrent.CompletableFuture[] {
                OllamaService.chat("must not leave the process"),
                OllamaService.chat("must not leave the process", "model") }) {
            assertTrue(reply.isDone());
            assertEquals("[AI Error] Ollama service stopped.", reply.join());
        }
        OllamaService.start();
        OllamaService.shutdown();
        assertEquals("[AI Error] Ollama service stopped.", OllamaService.chat("still offline").join());
    }
}
