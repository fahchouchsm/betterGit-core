package io.fahchouchsm.betterGitCore.api;

public record ChangedPath(String path, ChangeKind kind) implements Comparable<ChangedPath> {
    @Override
    public int compareTo(ChangedPath other) {
        return path.compareTo(other.path);
    }
}
