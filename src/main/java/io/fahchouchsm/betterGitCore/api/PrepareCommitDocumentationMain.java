package io.fahchouchsm.betterGitCore.api;

import com.google.gson.Gson;

import java.nio.file.Path;

public final class PrepareCommitDocumentationMain {
    private PrepareCommitDocumentationMain() {
    }

    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 1) {
            throw new IllegalArgumentException("Expected one repository directory.");
        }
        CommitDocumentation documentation = new BetterGitService()
                .prepareCommitDocumentation(Path.of(arguments[0]), System.getenv());
        System.out.println(new Gson().toJson(documentation));
    }
}
