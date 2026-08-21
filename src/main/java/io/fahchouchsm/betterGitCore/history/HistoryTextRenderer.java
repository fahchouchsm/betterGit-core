package io.fahchouchsm.betterGitCore.history;

import java.time.Clock;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class HistoryTextRenderer {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm xxx");
    private final Clock clock;
    private final RelativeTimeFormatter relativeTime;

    public HistoryTextRenderer(Clock clock, RelativeTimeFormatter relativeTime) {
        this.clock = clock;
        this.relativeTime = relativeTime;
    }

    public String render(RepositoryHistory history, HistoryView view) {
        StringBuilder output = new StringBuilder();
        appendRepository(output, history.summary());
        output.append("Showing ").append(history.commits().size()).append(" commit(s)")
                .append(System.lineSeparator()).append(System.lineSeparator());
        if (history.commits().isEmpty()) {
            return output.append("No commits matched the selected filters.")
                    .append(System.lineSeparator()).toString();
        }
        for (HistoryCommit commit : history.commits()) {
            appendCommit(output, commit, view);
        }
        return output.toString();
    }

    private static void appendRepository(StringBuilder output, RepositorySummary summary) {
        output.append("BetterGit history · ").append(summary.name()).append(System.lineSeparator());
        output.append("Branch: ").append(summary.detached() ? "detached at " : "")
                .append(summary.branch()).append(" · ")
                .append(summary.clean() ? "clean" : worktreeState(summary))
                .append(" · ").append(summary.state()).append(System.lineSeparator());
        if (summary.upstream() != null) {
            output.append("Upstream: ").append(summary.upstream())
                    .append(" · ↑").append(summary.ahead()).append(" ↓").append(summary.behind())
                    .append(System.lineSeparator());
        }
        output.append("BetterGit: ").append(summary.betterGitInitialized() ? "initialized" : "not initialized")
                .append(" · ").append(summary.betterGitReports()).append(" AI report(s)")
                .append(System.lineSeparator());
    }

    private static String worktreeState(RepositorySummary summary) {
        return "%d staged, %d unstaged, %d untracked, %d conflicted".formatted(
                summary.stagedFiles(), summary.unstagedFiles(),
                summary.untrackedFiles(), summary.conflictedFiles());
    }

    private void appendCommit(StringBuilder output, HistoryCommit commit, HistoryView view) {
        output.append(view.graph() ? commit.merge() ? "◆ " : "● " : "")
                .append(view.fullHash() ? commit.hash() : commit.hash().substring(0, 8));
        if (!commit.decorations().isEmpty()) {
            output.append("  (").append(String.join(", ", commit.decorations())).append(')');
        }
        output.append("  ").append(commit.subject());
        if (commit.betterGitReport() != null) {
            output.append("  ✦");
        }
        output.append(System.lineSeparator());
        appendIdentityLine(output, commit, view);
        if (view.statistics()) {
            appendStatistics(output, commit.statistics());
        }
        if (view.details()) {
            appendDetails(output, commit);
        }
        if (view.files() || view.details()) {
            appendFiles(output, commit.files(), view);
        }
        output.append(System.lineSeparator());
    }

    private void appendIdentityLine(StringBuilder output, HistoryCommit commit, HistoryView view) {
        CommitIdentity author = commit.author();
        output.append("  ").append(author.name()).append(" · ")
                .append(relativeTime.format(author.time(), clock.instant()));
        if (view.details()) {
            ZoneId zone = ZoneId.of(author.zoneOffset());
            output.append(" · ").append(DATE.withZone(zone).format(author.time()));
        }
        output.append(System.lineSeparator());
    }

    private static void appendStatistics(StringBuilder output, CommitStatistics statistics) {
        output.append("  ").append(statistics.filesChanged()).append(" file(s)")
                .append("  +").append(statistics.additions())
                .append(" -").append(statistics.deletions());
        if (statistics.binaryFiles() > 0) {
            output.append("  ").append(statistics.binaryFiles()).append(" binary");
        }
        output.append(System.lineSeparator());
    }

    private static void appendDetails(StringBuilder output, HistoryCommit commit) {
        output.append("  Author: ").append(identity(commit.author())).append(System.lineSeparator());
        output.append("  Committer: ").append(identity(commit.committer())).append(System.lineSeparator());
        output.append("  Parents: ").append(commit.parents().isEmpty()
                ? "none (root commit)" : shortParents(commit)).append(System.lineSeparator());
        output.append("  Tree: ").append(commit.treeHash().substring(0, 12))
                .append(" · Encoding: ").append(commit.encoding()).append(System.lineSeparator());
        output.append("  Signature: ").append(commit.signed() ? "present (not verified)" : "none")
                .append(System.lineSeparator());
        if (commit.betterGitReport() != null) {
            output.append("  BetterGit report: ").append(commit.betterGitReport()).append(System.lineSeparator());
        }
        for (CommitFooter footer : commit.footers()) {
            output.append("  Footer: ").append(footer.key()).append(": ").append(footer.value())
                    .append(System.lineSeparator());
        }
        String body = messageBody(commit);
        if (!body.isBlank()) {
            output.append("  Message:").append(System.lineSeparator());
            body.lines().forEach(line -> output.append("    ").append(line).append(System.lineSeparator()));
        }
    }

    private static String identity(CommitIdentity identity) {
        return identity.name() + " <" + identity.email() + ">";
    }

    private static String shortParents(HistoryCommit commit) {
        return commit.parents().stream().map(parent -> parent.substring(0, 8))
                .reduce((left, right) -> left + ", " + right).orElse("");
    }

    private static String messageBody(HistoryCommit commit) {
        String message = commit.message().strip();
        int firstLineEnd = message.indexOf('\n');
        return firstLineEnd < 0 ? "" : message.substring(firstLineEnd + 1).strip();
    }

    private static void appendFiles(
            StringBuilder output, java.util.List<CommitFileChange> files, HistoryView view) {
        for (CommitFileChange file : files) {
            output.append("    ").append(statusSymbol(file.status())).append(' ');
            if (file.previousPath() != null) {
                output.append(file.previousPath()).append(" → ");
            }
            output.append(file.path());
            if (view.statistics() && !file.binary()) {
                output.append("  +").append(file.additions()).append(" -").append(file.deletions());
            }
            output.append(System.lineSeparator());
        }
    }

    private static String statusSymbol(String status) {
        return switch (status) {
            case "ADD" -> "+";
            case "DELETE" -> "−";
            case "RENAME" -> "→";
            case "COPY" -> "⧉";
            default -> "~";
        };
    }
}
