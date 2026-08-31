package io.fahchouchsm.betterGitCore.ai;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.fahchouchsm.betterGitCore.configuration.AiProvider;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AiProtocolTest {
    private static final URI ENDPOINT = URI.create("https://api.example.test/generate");

    @Test
    void buildsGeminiRequestAndReadsCandidateText() throws Exception {
        AiProtocol protocol = AiProtocol.forProvider(AiProvider.GEMINI);

        HttpRequest request = protocol.request(apiRequest());
        JsonObject body = JsonParser.parseString(body(request)).getAsJsonObject();

        assertEquals("secret", request.headers().firstValue("x-goog-api-key").orElseThrow());
        assertEquals("hello", body.getAsJsonArray("contents").get(0).getAsJsonObject()
                .getAsJsonArray("parts").get(0).getAsJsonObject().get("text").getAsString());
        assertEquals("answer", protocol.outputText(
                "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"answer\"}]}}]}"));
    }

    @Test
    void buildsAnthropicRequestAndReadsOnlyTextBlocks() throws Exception {
        AiProtocol protocol = AiProtocol.forProvider(AiProvider.ANTHROPIC);

        HttpRequest request = protocol.request(apiRequest());
        JsonObject body = JsonParser.parseString(body(request)).getAsJsonObject();

        assertEquals("secret", request.headers().firstValue("x-api-key").orElseThrow());
        assertEquals("2023-06-01", request.headers().firstValue("anthropic-version").orElseThrow());
        assertEquals("test-model", body.get("model").getAsString());
        assertEquals("answer", protocol.outputText("""
                {"content":[{"type":"tool_use","name":"ignored"},{"type":"text","text":"answer"}]}
                """));
        assertThrows(AiResponseException.class,
                () -> protocol.outputText("{\"content\":[{\"type\":{},\"text\":\"invalid\"}]}"));
    }

    @Test
    void buildsOpenAiCompatibleRequestAndReadsMessageContent() throws Exception {
        AiProtocol protocol = AiProtocol.forProvider(AiProvider.OPENAI_COMPATIBLE);

        HttpRequest request = protocol.request(apiRequest());
        JsonObject body = JsonParser.parseString(body(request)).getAsJsonObject();

        assertEquals("Bearer secret", request.headers().firstValue("Authorization").orElseThrow());
        assertEquals("test-model", body.get("model").getAsString());
        assertEquals("hello", body.getAsJsonArray("messages").get(0).getAsJsonObject()
                .get("content").getAsString());
        assertEquals("answer", protocol.outputText(
                "{\"choices\":[{\"message\":{\"content\":\"answer\"}}]}"));
        assertThrows(AiResponseException.class, () -> protocol.outputText(
                "{\"choices\":[{\"message\":{\"content\":\"  \"}}]}"));
    }

    private static AiApiRequest apiRequest() {
        return new AiApiRequest(ENDPOINT, "secret", "test-model", "hello", Duration.ofSeconds(1));
    }

    private static String body(HttpRequest request) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        CompletableFuture<String> completedBody = new CompletableFuture<>();
        request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(ByteBuffer item) {
                byte[] chunk = new byte[item.remaining()];
                item.get(chunk);
                bytes.writeBytes(chunk);
            }

            @Override
            public void onError(Throwable throwable) {
                completedBody.completeExceptionally(throwable);
            }

            @Override
            public void onComplete() {
                completedBody.complete(bytes.toString(StandardCharsets.UTF_8));
            }
        });
        return completedBody.join();
    }
}
