package io.fahchouchsm.betterGitCore.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.net.http.HttpRequest;

final class GeminiProtocol implements AiProtocol {
    @Override
    public HttpRequest request(AiApiRequest request) {
        return HttpRequest.newBuilder(request.endpoint())
                .timeout(request.timeout())
                .header("x-goog-api-key", request.apiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody(request.prompt())))
                .build();
    }

    @Override
    public String outputText(String json) throws AiResponseException {
        JsonObject response = AiJsonResponse.parse(json);
        StringBuilder generatedText = new StringBuilder();
        for (JsonElement candidateElement : AiJsonResponse.requiredArray(response, "candidates")) {
            appendCandidate(generatedText, candidateElement);
        }
        if (generatedText.isEmpty()) {
            throw new AiResponseException("The AI response did not contain generated text.");
        }
        return generatedText.toString();
    }

    private static String requestBody(String prompt) {
        JsonObject textPart = new JsonObject();
        textPart.addProperty("text", prompt);
        JsonArray parts = new JsonArray();
        parts.add(textPart);
        JsonObject content = new JsonObject();
        content.add("parts", parts);
        JsonArray contents = new JsonArray();
        contents.add(content);
        JsonObject body = new JsonObject();
        body.add("contents", contents);
        return body.toString();
    }

    private static void appendCandidate(StringBuilder generatedText, JsonElement candidate)
            throws AiResponseException {
        JsonObject candidateObject = AiJsonResponse.requiredObject(candidate, "candidate");
        JsonObject content = AiJsonResponse.requiredObject(candidateObject.get("content"), "candidate content");
        for (JsonElement part : AiJsonResponse.requiredArray(content, "parts")) {
            JsonObject partObject = AiJsonResponse.requiredObject(part, "candidate part");
            JsonElement text = partObject.get("text");
            if (text != null && text.isJsonPrimitive() && text.getAsJsonPrimitive().isString()) {
                generatedText.append(text.getAsString());
            }
        }
    }
}
