package io.fahchouchsm.betterGitCore.commitreport;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Instant;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommitReportStoreTest {
    private static final Instant CREATED_AT = Instant.parse("2026-08-21T15:16:17.123Z");

    @TempDir
    Path projectPath;

    private final CommitReportStore store = new CommitReportStore();

    @Test
    void savesFailureDiagnosticWithActionableSectionsAndOwnerOnlyPermissions() throws Exception {
        Path diagnostic = store.saveFailureDiagnostic(
                projectPath, CREATED_AT, "The response was invalid.", "Unexpected response");

        assertEquals(
                projectPath.resolve(".bettergit/reports/errors/ai-report-failure-20260821-151617-123.md"),
                diagnostic);
        String content = Files.readString(diagnostic);
        assertTrue(content.contains("**Time:** 2026-08-21T15:16:17.123Z"));
        assertTrue(content.contains("## Reason\n\n    The response was invalid."));
        assertTrue(content.contains("## Expected response format"));
        assertTrue(content.contains("## Redacted AI response\n\n    Unexpected response"));

        if (Files.getFileStore(diagnostic).supportsFileAttributeView("posix")) {
            assertEquals(
                    Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
                    Files.getPosixFilePermissions(diagnostic));
        }
    }

    @Test
    void createsUniqueDiagnosticNamesForFailuresInTheSameMillisecond() throws Exception {
        Path first = store.saveFailureDiagnostic(projectPath, CREATED_AT, "First", "Response one");
        Path second = store.saveFailureDiagnostic(projectPath, CREATED_AT, "Second", "Response two");

        assertEquals("ai-report-failure-20260821-151617-123.md", first.getFileName().toString());
        assertEquals("ai-report-failure-20260821-151617-123-2.md", second.getFileName().toString());
        assertTrue(Files.readString(first).contains("    First"));
        assertTrue(Files.readString(second).contains("    Second"));
    }

    @Test
    void substitutesPlaceholderWhenNoResponseBodyExists() throws Exception {
        Path diagnostic = store.saveFailureDiagnostic(projectPath, CREATED_AT, "Timed out", "  \n  ");

        String content = Files.readString(diagnostic);
        assertTrue(content.contains("## Redacted AI response\n\n    [No response body was available.]"));
    }

    @Test
    void refusesToWriteDiagnosticsThroughSymbolicErrorsDirectory() throws Exception {
        Path reports = projectPath.resolve(".bettergit/reports");
        Path outside = projectPath.resolve("outside");
        Files.createDirectories(reports);
        Files.createDirectories(outside);
        Files.createSymbolicLink(reports.resolve("errors"), outside);

        IOException exception = assertThrows(IOException.class, () ->
                store.saveFailureDiagnostic(projectPath, CREATED_AT, "Reason", "Response"));

        assertTrue(exception.getMessage().contains("symbolic BetterGit errors directory"));
        try (var files = Files.list(outside)) {
            assertFalse(files.findAny().isPresent());
        }
    }
}
