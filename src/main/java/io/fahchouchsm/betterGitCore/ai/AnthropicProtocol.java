package io.fahchouchsm.betterGitCore.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.net.http.HttpRequest;

final class AnthropicProtocol implements AiProtocol {
    private static final int MAX_OUTPUT_TOKENS = 4096;

    @Override
    public HttpRequest request(AiApiRequest request) {
        return HttpRequest.newBuilder(request.endpoint())
                .timeout(request.timeout())
                .header("x-api-key", request.apiKey())
                .header("anthropic-version", "2023-06-01")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody(request.model(), request.prompt())))
                .build();
    }

    @Override
    public String outputText(String json) throws AiResponseException {
        JsonObject response = AiJsonResponse.parse(json);
        StringBuilder generatedText = new StringBuilder();
        for (JsonElement contentElement : AiJsonResponse.requiredArray(response, "content")) {
            JsonObject content = AiJsonResponse.requiredObject(contentElement, "content block");
            JsonElement type = content.get("type");
            if (type != null && type.isJsonPrimitive()
                    && type.getAsJsonPrimitive().isString()
                    && "text".equals(type.getAsString())) {
                generatedText.append(AiJsonResponse.requiredString(content, "text"));
            }
        }
        if (generatedText.isEmpty()) {
            throw new AiResponseException("The AI response did not contain generated text.");
        }
        return generatedText.toString();
    }

    private static String requestBody(String model, String prompt) {
        JsonObject message = new JsonObject();
        message.addProperty("role", "user");
        message.addProperty("content", prompt);
        JsonArray messages = new JsonArray();
        messages.add(message);
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("max_tokens", MAX_OUTPUT_TOKENS);
        body.add("messages", messages);
        return body.toString();
    }
}
