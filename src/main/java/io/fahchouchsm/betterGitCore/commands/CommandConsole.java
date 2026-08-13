package io.fahchouchsm.betterGitCore.commands;

import java.io.IOException;

/** User interaction boundary for BetterGit commands. */
public interface CommandConsole {
    String readLine(String prompt) throws IOException;

    void println(String message);
}
