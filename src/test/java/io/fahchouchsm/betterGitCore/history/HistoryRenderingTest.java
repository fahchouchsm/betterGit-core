package io.fahchouchsm.betterGitCore.history;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HistoryRenderingTest {
    private static final Instant NOW = Instant.parse("2026-08-15T12:00:00Z");

    @Test
    void richTextShowsRepositoryCommitIdentityStatsAndFiles() {
        RepositoryHistory history = history();
        HistoryTextRenderer renderer = new HistoryTextRenderer(
                Clock.fixed(NOW, ZoneOffset.UTC), new RelativeTimeFormatter());

        String rendered = renderer.render(history, new HistoryView(true, true, false, true, true));

        assertTrue(rendered.contains("BetterGit history · project"));
        assertTrue(rendered.contains("Branch: main · 1 staged, 2 unstaged, 3 untracked, 0 conflicted"));
        assertTrue(rendered.contains("● 01234567  (HEAD → main, tag: v1.0.0)  feat: modern history"));
        assertTrue(rendered.contains("Simo · 2 hours ago"));
        assertTrue(rendered.contains("Author: Simo <simo@example.com>"));
        assertTrue(rendered.contains("Tree: fedcbafedcba · Encoding: UTF-8"));
        assertTrue(rendered.contains("Signature: present (not verified)"));
        assertTrue(rendered.contains("Footer: Reviewed-by: Reviewer <reviewer@example.com>"));
        assertTrue(rendered.contains("BetterGit: initialized · 1 AI report(s)"));
        assertTrue(rendered.contains("+ src/App.java  +4 -1"));
    }

    @Test
    void jsonContainsCompleteMachineReadableMetadata() {
        String json = new HistoryJsonRenderer().render(history());

        assertTrue(json.contains("\"hash\": \"0123456789012345678901234567890123456789\""));
        assertTrue(json.contains("\"email\": \"simo@example.com\""));
        assertTrue(json.contains("\"additions\": 4"));
        assertTrue(json.contains("\"treeHash\": \"fedcbafedcbafedcbafedcbafedcbafedcbafedc\""));
        assertTrue(json.contains("\"key\": \"Reviewed-by\""));
        assertTrue(json.contains("\"repository\": \"/project\""));
    }

    @Test
    void dateParserSupportsRelativeAndCalendarInputsAndRejectsUnknownFormats() {
        HistoryDateParser parser = new HistoryDateParser(Clock.fixed(NOW, ZoneOffset.UTC));

        assertEquals(NOW.minusSeconds(7 * 86_400L), parser.parse("7d"));
        assertEquals(Instant.parse("2026-08-15T00:00:00Z"), parser.parse("today"));
        assertEquals(Instant.parse("2026-08-10T00:00:00Z"), parser.parse("2026-08-10"));
        assertThrows(IllegalArgumentException.class, () -> parser.parse("last-ish-week"));
    }

    private static RepositoryHistory history() {
        CommitIdentity identity = new CommitIdentity(
                "Simo", "simo@example.com", NOW.minusSeconds(7_200), "+00:00");
        HistoryCommit commit = new HistoryCommit(
                "0123456789012345678901234567890123456789",
                "feat: modern history",
                "feat: modern history\n\nAdds a richer view.",
                identity,
                identity,
                List.of("abcdefabcdefabcdefabcdefabcdefabcdefabcd"),
                "fedcbafedcbafedcbafedcbafedcbafedcbafedc",
                "UTF-8",
                List.of(new CommitFooter("Reviewed-by", "Reviewer <reviewer@example.com>")),
                List.of("HEAD → main", "tag: v1.0.0"),
                true,
                "/project/.bettergit/reports/0123456789012345678901234567890123456789.md",
                new CommitStatistics(1, 4, 1, 0),
                List.of(new CommitFileChange("ADD", "src/App.java", null, 4, 1, false)));
        RepositorySummary summary = new RepositorySummary(
                "project", "main", false, "Safe", false, 1, 2, 3, 0,
                "origin/main", 2, 1, true, 1);
        return new RepositoryHistory(Path.of("/project"), summary, List.of(commit));
    }
}
