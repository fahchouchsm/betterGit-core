package io.fahchouchsm.betterGitCore.configuration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiCredentialStoreTest {
    @TempDir
    Path projectPath;

    @Test
    void updatesAiSettingsWhilePreservingUnrelatedAndUnreplacedValues() throws Exception {
        Path envFile = projectPath.resolve(".env");
        Files.writeString(envFile, "APP_MODE=dev\nAI_API_KEY=existing-key\nAI_API_MODEL=old-model\n");

        new AiCredentialStore().update(projectPath, Map.of(
                "AI_API_MODEL", "gemini-2.5-flash",
                "AI_API_URL", AiSetupService.DEFAULT_GEMINI_URL));

        String stored = Files.readString(envFile);
        assertTrue(stored.contains("APP_MODE=dev"));
        assertTrue(stored.contains("AI_API_KEY=existing-key"));
        assertTrue(stored.contains("AI_API_MODEL=gemini-2.5-flash"));
        assertEquals(1, stored.lines().filter(line -> line.startsWith("AI_API_MODEL=")).count());
        assertOwnerOnlyWhenSupported(envFile);
    }

    @Test
    void rejectsLineInjectionAndSymbolicCredentialFiles() throws Exception {
        AiCredentialStore store = new AiCredentialStore();
        Path external = Files.writeString(projectPath.resolve("external"), "safe");
        Files.createSymbolicLink(projectPath.resolve(".env"), external);

        assertThrows(IllegalArgumentException.class,
                () -> store.update(projectPath, Map.of("AI_API_KEY", "key\nINJECTED=true")));
        assertThrows(java.io.IOException.class,
                () -> store.update(projectPath, Map.of("AI_API_KEY", "safe-key")));
        assertEquals("safe", Files.readString(external));
    }

    private static void assertOwnerOnlyWhenSupported(Path envFile) throws Exception {
        try {
            Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(envFile);
            assertEquals(Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE), permissions);
        } catch (UnsupportedOperationException exception) {
            assertTrue(Files.isRegularFile(envFile));
        }
    }
}
