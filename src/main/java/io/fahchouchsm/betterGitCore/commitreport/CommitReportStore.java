package io.fahchouchsm.betterGitCore.commitreport;

import io.fahchouchsm.betterGitCore.configuration.AtomicFileWriter;
import io.fahchouchsm.betterGitCore.configuration.BetterGitDirectories;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.AtomicMoveNotSupportedException;
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

    public Path finalizePending(Path projectPath, Path pendingReport, String commitHash) throws IOException {
        if (!commitHash.matches("[0-9a-f]{40}")) {
            throw new IllegalArgumentException("Commit hash must contain 40 lowercase hexadecimal characters.");
        }
        Path reportsDirectory = BetterGitDirectories.child(projectPath, "reports").toAbsolutePath().normalize();
        Path source = pendingReport.toAbsolutePath().normalize();
        if (!source.getParent().equals(reportsDirectory) || !Files.isRegularFile(source)) {
            throw new IOException("Pending report is not a regular BetterGit report file.");
        }
        Path finalizedReport = reportsDirectory.resolve(commitHash + ".md");
        if (Files.exists(finalizedReport)) {
            throw new IOException("A report already exists for commit " + commitHash + ".");
        }
        move(source, finalizedReport);
        return finalizedReport;
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

    private static void move(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, destination);
        }
    }
}
