package io.fahchouchsm.betterGitCore.api;

import io.fahchouchsm.betterGitCore.commitreport.CommitReportStatus;
import io.fahchouchsm.betterGitCore.commitreport.CommitReportStore;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BetterGitServiceTest {
    @TempDir
    Path projectPath;

    @Test
    void exposesStatusAndStagesAndUnstagesPaths() throws Exception {
        initializeGit();
        Files.writeString(projectPath.resolve("notes.txt"), "UI facade\n");
        BetterGitService service = new BetterGitService();

        assertEquals(ChangeKind.UNTRACKED, service.status(projectPath).unstaged().getFirst().kind());

        service.stage(projectPath, List.of("notes.txt"));
        assertEquals(ChangeKind.ADDED, service.status(projectPath).staged().getFirst().kind());

        service.unstage(projectPath, List.of("notes.txt"));
        assertTrue(service.status(projectPath).staged().isEmpty());
        assertEquals(ChangeKind.UNTRACKED, service.status(projectPath).unstaged().getFirst().kind());
    }

    @Test
    void initializesAndCommitsThroughStructuredOperations() throws Exception {
        initializeGit();
        RecordingObserver observer = new RecordingObserver();
        BetterGitService service = new BetterGitService();
        assertTrue(service.initialize(projectPath, observer).succeeded());
        Files.writeString(projectPath.resolve("feature.txt"), "feature\n");
        service.stage(projectPath, List.of("feature.txt"));

        OperationOutcome outcome = service.commit(
                projectPath, new CommitRequest("feat: add UI facade", false), observer);

        assertTrue(outcome.succeeded());
        assertTrue(observer.events.stream().anyMatch(event -> event.severity() == EventSeverity.SUCCESS));
        assertTrue(service.history(projectPath, 10).commits().stream()
                .anyMatch(commit -> commit.subject().equals("feat: add UI facade")));
    }

    @Test
    void rejectsPathsThatEscapeTheRepository() throws Exception {
        initializeGit();
        BetterGitService service = new BetterGitService();

        IllegalArgumentException failure = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> service.stage(projectPath, List.of("../outside.txt")));

        assertTrue(failure.getMessage().contains("escapes"));
        assertFalse(Files.exists(projectPath.getParent().resolve("outside.txt")));
    }

    @Test
    void reportsMissingAiCredentialsWithoutCallingAProvider() throws Exception {
        initializeGit();
        Files.writeString(projectPath.resolve("feature.txt"), "feature\n");
        BetterGitService service = new BetterGitService();
        service.stage(projectPath, List.of("feature.txt"));

        CommitDocumentation documentation = service.prepareCommitDocumentation(projectPath, Map.of());

        assertEquals(CommitReportStatus.AI_NOT_CONFIGURED.name(), documentation.status());
        assertTrue(documentation.markdown().isEmpty());
        assertTrue(documentation.reportPath().isEmpty());
    }

    @Test
    void finalizesOnlyValidatedPendingDocumentation() throws Exception {
        initializeGit();
        String markdown = "Description.\n\n## Changes\n- Added docs.\n\n## Validation\nTests passed.\n";
        Path pending = new CommitReportStore().savePending(projectPath, Instant.EPOCH, markdown);
        String commitHash = "a".repeat(40);

        CommitDocumentationFinalization finalization = new BetterGitService()
                .finalizeCommitDocumentation(projectPath, pending, commitHash);

        assertEquals(projectPath.resolve(".bettergit/reports/" + commitHash + ".md").toString(),
                finalization.reportPath());
        assertTrue(finalization.warning().isEmpty());
        assertFalse(Files.exists(pending));
    }

    @Test
    void discardsPendingDocumentationWithoutTouchingOtherFiles() throws Exception {
        initializeGit();
        Path pending = new CommitReportStore().savePending(
                projectPath, Instant.EPOCH, "Pending documentation");

        new BetterGitService().discardCommitDocumentation(projectPath, pending);

        assertFalse(Files.exists(pending));
        assertTrue(Files.isDirectory(projectPath.resolve(".git")));
    }

    private void initializeGit() throws Exception {
        try (Git git = Git.init().setDirectory(projectPath.toFile()).setInitialBranch("main").call()) {
            git.getRepository().getConfig().setString("user", null, "name", "BetterGit Tests");
            git.getRepository().getConfig().setString("user", null, "email", "bettergit@example.test");
            git.getRepository().getConfig().save();
        }
    }

    private static final class RecordingObserver implements OperationObserver {
        private final List<OperationEvent> events = new ArrayList<>();

        @Override
        public void onEvent(OperationEvent event) {
            events.add(event);
        }

        @Override
        public boolean approve(String question, boolean defaultApproval) {
            return defaultApproval;
        }
    }
}
