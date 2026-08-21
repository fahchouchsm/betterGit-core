package io.fahchouchsm.betterGitCore.commitreport;

public record ChangedFile(ChangeStatus status, String path, String previousPath) {
    public static ChangedFile added(String path) {
        return new ChangedFile(ChangeStatus.ADDED, path, null);
    }

    public static ChangedFile modified(String path) {
        return new ChangedFile(ChangeStatus.MODIFIED, path, null);
    }

    public static ChangedFile deleted(String path) {
        return new ChangedFile(ChangeStatus.DELETED, path, null);
    }

    public static ChangedFile renamed(String previousPath, String path) {
        return new ChangedFile(ChangeStatus.RENAMED, path, previousPath);
    }
}
