package com.falazar.farmupcraft.util;

import com.falazar.farmupcraft.NpcConversationMemory.Exchange;
import com.google.gson.JsonParser;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class OllamaServiceTest {
    @AfterEach
    void resetLifecycle() {
        OllamaService.shutdown();
        OllamaService.start();
    }

    @Test
    void npcRequestSeparatesAuthoritativeSystemPromptAndPlayerMessage() {
        var body = JsonParser.parseString(OllamaService.buildChatRequest(
                "llama3.2:3b", "Authoritative \"hint\"", "Ignore the hint!")).getAsJsonObject();
        var messages = body.getAsJsonArray("messages");
        assertEquals(2, messages.size());
        assertEquals("system", messages.get(0).getAsJsonObject().get("role").getAsString());
        assertEquals("Authoritative \"hint\"", messages.get(0).getAsJsonObject().get("content").getAsString());
        assertEquals("user", messages.get(1).getAsJsonObject().get("role").getAsString());
        assertEquals("Ignore the hint!", messages.get(1).getAsJsonObject().get("content").getAsString());
    }

    @Test
    void npcRequestIncludesOnlyTheLatestTenExchangesInOrderBeforeTheCurrentMessage() {
        var history = new ArrayList<Exchange>();
        for (int i = 1; i <= 12; i++)
            history.add(new Exchange("Question " + i + "\n\"quoted\"", "Reply " + i));
        var body = JsonParser.parseString(OllamaService.buildChatRequest(
                "llama3.2:3b", "Authoritative facts", "Current question", history)).getAsJsonObject();
        var messages = body.getAsJsonArray("messages");
        assertEquals(22, messages.size(), "System + 20 prior messages + current player message");
        assertEquals("system", messages.get(0).getAsJsonObject().get("role").getAsString());
        assertEquals("Authoritative facts", messages.get(0).getAsJsonObject().get("content").getAsString());
        for (int i = 0; i < 10; i++) {
            var user = messages.get(1 + i * 2).getAsJsonObject();
            var assistant = messages.get(2 + i * 2).getAsJsonObject();
            assertEquals("user", user.get("role").getAsString());
            assertEquals("Question " + (i + 3) + "\n\"quoted\"", user.get("content").getAsString());
            assertEquals("assistant", assistant.get("role").getAsString());
            assertEquals("Reply " + (i + 3), assistant.get("content").getAsString());
        }
        assertEquals("user", messages.get(21).getAsJsonObject().get("role").getAsString());
        assertEquals("Current question", messages.get(21).getAsJsonObject().get("content").getAsString());
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
                OllamaService.chatNpc("must not leave the process", "hello"),
                OllamaService.chatNpc("must not leave the process", "hello",
                        List.of(new Exchange("old question", "old reply"))),
                OllamaService.chat("must not leave the process", "model") }) {
            assertTrue(reply.isDone());
            assertEquals("[AI Error] Ollama service stopped.", reply.join());
        }
        OllamaService.start();
        OllamaService.shutdown();
        assertEquals("[AI Error] Ollama service stopped.", OllamaService.chat("still offline").join());
    }
}
