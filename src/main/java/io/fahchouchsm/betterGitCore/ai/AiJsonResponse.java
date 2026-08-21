package io.fahchouchsm.betterGitCore.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

final class AiJsonResponse {
    private AiJsonResponse() {
    }

    static JsonObject parse(String json) throws AiResponseException {
        if (json == null || json.isBlank()) {
            throw new AiResponseException("The AI service returned an empty response.");
        }
        try {
            return JsonParser.parseString(json).getAsJsonObject();
        } catch (JsonParseException | IllegalStateException exception) {
            throw new AiResponseException("The AI service returned malformed JSON.", exception);
        }
    }

    static JsonArray requiredArray(JsonObject parent, String fieldName) throws AiResponseException {
        JsonElement field = parent.get(fieldName);
        if (field == null || !field.isJsonArray()) {
            throw new AiResponseException("The AI response did not contain " + fieldName + ".");
        }
        return field.getAsJsonArray();
    }

    static JsonObject requiredObject(JsonElement element, String description) throws AiResponseException {
        if (element == null || !element.isJsonObject()) {
            throw new AiResponseException("The AI response did not contain a valid " + description + ".");
        }
        return element.getAsJsonObject();
    }

    static String requiredString(JsonObject parent, String fieldName) throws AiResponseException {
        JsonElement field = parent.get(fieldName);
        if (field == null || !field.isJsonPrimitive() || !field.getAsJsonPrimitive().isString()) {
            throw new AiResponseException("The AI response did not contain " + fieldName + ".");
        }
        return field.getAsString();
    }
}
