package io.fahchouchsm.betterGitCore.commands.console;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

/** Terminal boundary used by commands and initialization workflows. */
public interface ConsolePort {
    String readLine(String prompt) throws IOException;

    String readSecret(String prompt) throws IOException;

    boolean confirm(String question, ConfirmationDefault defaultChoice) throws IOException;

    int chooseOne(String question, List<String> choices, int defaultChoice) throws IOException;

    List<Boolean> chooseMany(String question, List<String> choices) throws IOException;

    PrintWriter out();

    PrintWriter err();

    void configure(ConsoleSettings settings);

    void info(String message);

    void success(String message);

    void warning(String message);

    void failure(String message);

    void diagnostic(String message);

    boolean isVerbose();
}
