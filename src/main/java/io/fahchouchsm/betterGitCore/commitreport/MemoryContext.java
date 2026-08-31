package io.fahchouchsm.betterGitCore.commitreport;

public record MemoryContext(String general, String projectMap, String recentHistory) {
    public static MemoryContext empty() {
        return new MemoryContext("", "", "");
    }
}
