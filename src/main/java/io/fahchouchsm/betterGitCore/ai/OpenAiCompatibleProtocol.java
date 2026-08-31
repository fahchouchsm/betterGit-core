package io.fahchouchsm.betterGitCore.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.net.http.HttpRequest;

final class OpenAiCompatibleProtocol implements AiProtocol {
    @Override
    public HttpRequest request(AiApiRequest request) {
        return HttpRequest.newBuilder(request.endpoint())
                .timeout(request.timeout())
                .header("Authorization", "Bearer " + request.apiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody(request.model(), request.prompt())))
                .build();
    }

    @Override
    public String outputText(String json) throws AiResponseException {
        JsonObject response = AiJsonResponse.parse(json);
        JsonArray choices = AiJsonResponse.requiredArray(response, "choices");
        if (choices.isEmpty()) {
            throw new AiResponseException("The AI response did not contain choices.");
        }
        JsonObject choice = AiJsonResponse.requiredObject(choices.get(0), "choice");
        JsonObject message = AiJsonResponse.requiredObject(choice.get("message"), "choice message");
        String generatedText = AiJsonResponse.requiredString(message, "content");
        if (generatedText.isBlank()) {
            throw new AiResponseException("The AI response did not contain generated text.");
        }
        return generatedText;
    }

    private static String requestBody(String model, String prompt) {
        JsonObject message = new JsonObject();
        message.addProperty("role", "user");
        message.addProperty("content", prompt);
        JsonArray messages = new JsonArray();
        messages.add(message);
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.add("messages", messages);
        return body.toString();
    }
}
