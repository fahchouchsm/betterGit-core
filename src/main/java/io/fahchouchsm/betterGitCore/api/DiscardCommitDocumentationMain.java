package io.fahchouchsm.betterGitCore.api;

import java.nio.file.Path;

public final class DiscardCommitDocumentationMain {
    private DiscardCommitDocumentationMain() {
    }

    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 2) {
            throw new IllegalArgumentException("Expected repository and pending report.");
        }
        new BetterGitService().discardCommitDocumentation(
                Path.of(arguments[0]), Path.of(arguments[1]));
        System.out.println("{}");
    }
}
