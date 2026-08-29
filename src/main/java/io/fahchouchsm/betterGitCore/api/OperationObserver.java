package io.fahchouchsm.betterGitCore.api;

import java.io.IOException;

public interface OperationObserver {
    void onEvent(OperationEvent event);

    boolean approve(String question, boolean defaultApproval) throws IOException;
}
