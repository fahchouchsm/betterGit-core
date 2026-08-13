package io.fahchouchsm.betterGitCore.JGitManager;

import java.util.Set;

/** Read-only working-tree changes grouped by their Git state. All paths are repository-relative. */
public record GitChanges(
        Set<String> added,
        Set<String> changed,
        Set<String> removed,
        Set<String> modified,
        Set<String> missing,
        Set<String> untracked,
        Set<String> conflicting,
        Set<String> ignoredNotInIndex) {

    public GitChanges {
        added = Set.copyOf(added);
        changed = Set.copyOf(changed);
        removed = Set.copyOf(removed);
        modified = Set.copyOf(modified);
        missing = Set.copyOf(missing);
        untracked = Set.copyOf(untracked);
        conflicting = Set.copyOf(conflicting);
        ignoredNotInIndex = Set.copyOf(ignoredNotInIndex);
    }

    public boolean isClean() {
        return added.isEmpty() && changed.isEmpty() && removed.isEmpty() && modified.isEmpty()
                && missing.isEmpty() && untracked.isEmpty() && conflicting.isEmpty();
    }
}
