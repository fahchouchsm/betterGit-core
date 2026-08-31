package io.fahchouchsm.betterGitCore.commitreport;

import io.fahchouchsm.betterGitCore.configuration.AtomicFileWriter;
import io.fahchouchsm.betterGitCore.configuration.BetterGitDirectories;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public final class CommitReportStore {
    private static final DateTimeFormatter FILE_TIMESTAMP = DateTimeFormatter
            .ofPattern("uuuuMMdd-HHmmss-SSS")
            .withZone(ZoneOffset.UTC);

    public Path savePending(Path projectPath, Instant createdAt, String markdown) throws IOException {
        Path reportsDirectory = BetterGitDirectories.child(projectPath, "reports");
        Path report = uniqueReportPath(reportsDirectory, createdAt);
        AtomicFileWriter.write(report, markdown);
        return report;
    }

    public Path saveFailureDiagnostic(
            Path projectPath, Instant createdAt, String reason, String redactedResponse) throws IOException {
        Path errorsDirectory = failureDirectory(projectPath);
        Path diagnostic = uniqueFailurePath(errorsDirectory, createdAt);
        AtomicFileWriter.writeOwnerOnly(
                diagnostic, failureDiagnosticContent(createdAt, reason, redactedResponse));
        return diagnostic;
    }

    private static String failureDiagnosticContent(
            Instant createdAt, String reason, String redactedResponse) {
        return """
                # BetterGit AI report failure

                **Time:** %s

                ## Reason

                %s

                ## Expected response format

                    Concise commit description (maximum 160 characters).

                    ## Changes
                    - One to five change bullets

                    ## Validation
                    One validation paragraph (maximum 240 characters).

                ## Redacted AI response

                %s
                """.formatted(createdAt, indented(reason), indented(redactedResponse));
    }

    private static Path failureDirectory(Path projectPath) throws IOException {
        Path errorsDirectory = BetterGitDirectories.child(projectPath, "reports").resolve("errors");
        if (Files.isSymbolicLink(errorsDirectory)) {
            throw new IOException("Refusing to write through symbolic BetterGit errors directory: "
                    + errorsDirectory);
        }
        Files.createDirectories(errorsDirectory);
        return errorsDirectory;
    }

    public Path finalizePending(Path projectPath, Path pendingReport, String commitHash) throws IOException {
        if (!commitHash.matches("[0-9a-f]{40}")) {
            throw new IllegalArgumentException("Commit hash must contain 40 lowercase hexadecimal characters.");
        }
        Path reportsDirectory = reportsDirectory(projectPath);
        Path source = pendingReport(reportsDirectory, pendingReport);
        Path finalizedReport = reportsDirectory.resolve(commitHash + ".md");
        if (Files.exists(finalizedReport)) {
            throw new IOException("A report already exists for commit " + commitHash + ".");
        }
        move(source, finalizedReport);
        return finalizedReport;
    }

    public String readPending(Path projectPath, Path pendingReport) throws IOException {
        Path source = pendingReport(reportsDirectory(projectPath), pendingReport);
        return Files.readString(source, StandardCharsets.UTF_8);
    }

    public void discardPending(Path projectPath, Path pendingReport) throws IOException {
        Path source = pendingReport(reportsDirectory(projectPath), pendingReport);
        Files.delete(source);
    }

    private static Path reportsDirectory(Path projectPath) throws IOException {
        return BetterGitDirectories.child(projectPath, "reports").toAbsolutePath().normalize();
    }

    private static Path pendingReport(Path reportsDirectory, Path candidate) throws IOException {
        Path source = candidate.toAbsolutePath().normalize();
        if (!reportsDirectory.equals(source.getParent())
                || Files.isSymbolicLink(source)
                || !Files.isRegularFile(source)) {
            throw new IOException("Pending report is not a regular BetterGit report file.");
        }
        return source;
    }

    private static Path uniqueReportPath(Path reportsDirectory, Instant createdAt) {
        String baseName = "pending-" + FILE_TIMESTAMP.format(createdAt);
        Path report = reportsDirectory.resolve(baseName + ".md");
        int suffix = 2;
        while (Files.exists(report)) {
            report = reportsDirectory.resolve(baseName + "-" + suffix + ".md");
            suffix++;
        }
        return report;
    }

    private static Path uniqueFailurePath(Path errorsDirectory, Instant createdAt) {
        String baseName = "ai-report-failure-" + FILE_TIMESTAMP.format(createdAt);
        Path diagnostic = errorsDirectory.resolve(baseName + ".md");
        int suffix = 2;
        while (Files.exists(diagnostic)) {
            diagnostic = errorsDirectory.resolve(baseName + "-" + suffix + ".md");
            suffix++;
        }
        return diagnostic;
    }

    private static String indented(String text) {
        String safeText = text == null || text.isBlank() ? "[No response body was available.]" : text.strip();
        return safeText.lines()
                .map(line -> "    " + line)
                .reduce((left, right) -> left + System.lineSeparator() + right)
                .orElse("    [No response body was available.]");
    }

    private static void move(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, destination);
        }
    }
}
