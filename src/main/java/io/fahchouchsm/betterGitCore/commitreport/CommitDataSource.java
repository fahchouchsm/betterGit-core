package io.fahchouchsm.betterGitCore.commitreport;

import java.nio.file.Path;

public interface CommitDataSource {
    CommitSnapshot stagedSnapshot(Path projectPath);
}
