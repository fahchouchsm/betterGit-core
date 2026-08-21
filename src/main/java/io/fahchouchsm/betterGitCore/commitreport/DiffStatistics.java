package io.fahchouchsm.betterGitCore.commitreport;

public record DiffStatistics(int filesChanged, int additions, int deletions) {
    public static DiffStatistics fromDiff(int filesChanged, String diff) {
        int additions = 0;
        int deletions = 0;
        for (String line : diff.lines().toList()) {
            if (line.startsWith("+") && !line.startsWith("+++")) {
                additions++;
            } else if (line.startsWith("-") && !line.startsWith("---")) {
                deletions++;
            }
        }
        return new DiffStatistics(filesChanged, additions, deletions);
    }
}
