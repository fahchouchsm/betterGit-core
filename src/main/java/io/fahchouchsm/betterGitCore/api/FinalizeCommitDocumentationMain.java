package io.fahchouchsm.betterGitCore.api;

import com.google.gson.Gson;

import java.nio.file.Path;

public final class FinalizeCommitDocumentationMain {
    private FinalizeCommitDocumentationMain() {
    }

    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 3) {
            throw new IllegalArgumentException("Expected repository, pending report, and commit hash.");
        }
        CommitDocumentationFinalization finalization = new BetterGitService()
                .finalizeCommitDocumentation(
                        Path.of(arguments[0]), Path.of(arguments[1]), arguments[2]);
        System.out.println(new Gson().toJson(finalization));
    }
}
