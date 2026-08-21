package io.fahchouchsm.betterGitCore.configuration;

import java.io.IOException;
import java.net.URI;
import java.util.List;

@FunctionalInterface
public interface AiModelSource {
    List<String> availableModels(AiProvider provider, URI endpoint, String apiKey)
            throws IOException, InterruptedException;
}
