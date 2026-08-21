package io.fahchouchsm.betterGitCore.configuration;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AiModelCatalogTest {
    @Test
    void normalizesAndFiltersGeminiModelIds() throws Exception {
        List<String> models = AiModelCatalog.modelIds(AiProvider.GEMINI, """
                {"models":[
                  {"name":"models/gemini-pro"},
                  {"name":"models/gemini-flash"},
                  {"name":"models/gemini-pro"},
                  {"name":""},
                  {"name":42},
                  {}
                ]}
                """);

        assertEquals(List.of("gemini-flash", "gemini-pro"), models);
    }

    @Test
    void readsOpenAiCompatibleIdsAndRejectsMalformedPayloads() throws Exception {
        assertEquals(List.of("model-a", "model-b"), AiModelCatalog.modelIds(
                AiProvider.OPENAI_COMPATIBLE,
                "{\"data\":[{\"id\":\"model-b\"},{\"id\":\"model-a\"}]}"));
        assertThrows(IOException.class,
                () -> AiModelCatalog.modelIds(AiProvider.ANTHROPIC, "{\"models\":[]}"));
    }
}
