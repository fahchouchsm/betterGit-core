package io.fahchouchsm.betterGitCore.history;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;

import java.time.Instant;
import java.nio.file.Path;

public final class HistoryJsonRenderer {
    private static final Gson JSON = new GsonBuilder()
            .registerTypeAdapter(Instant.class,
                    (JsonSerializer<Instant>) (instant, type, context) -> new JsonPrimitive(instant.toString()))
            .registerTypeHierarchyAdapter(Path.class,
                    (JsonSerializer<Path>) (path, type, context) -> new JsonPrimitive(path.toString()))
            .setPrettyPrinting()
            .create();

    public String render(RepositoryHistory history) {
        return JSON.toJson(history) + System.lineSeparator();
    }
}
