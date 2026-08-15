package io.fahchouchsm.betterGitCore.commitreport;

import io.fahchouchsm.betterGitCore.configuration.AtomicFileWriter;
import io.fahchouchsm.betterGitCore.configuration.BetterGitDirectories;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
}
