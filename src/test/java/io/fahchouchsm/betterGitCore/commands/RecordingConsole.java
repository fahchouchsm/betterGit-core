package io.fahchouchsm.betterGitCore.commands;

import io.fahchouchsm.betterGitCore.commands.console.ConsolePort;
import io.fahchouchsm.betterGitCore.commands.console.ConsoleSettings;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

final class RecordingConsole implements ConsolePort {
    private final Queue<String> answers;
    private final List<String> prompts = new ArrayList<>();
    private final StringWriter outputBuffer = new StringWriter();
    private final StringWriter errorBuffer = new StringWriter();
    private final PrintWriter output = new PrintWriter(outputBuffer, true);
    private final PrintWriter error = new PrintWriter(errorBuffer, true);
    private boolean verbose;
    private boolean noColor;

    RecordingConsole(String... answers) {
        this.answers = new ArrayDeque<>(List.of(answers));
    }

    @Override
    public String readLine(String prompt) {
        prompts.add(prompt);
        return answers.isEmpty() ? "" : answers.remove();
    }

    @Override
    public PrintWriter out() {
        return output;
    }

    @Override
    public PrintWriter err() {
        return error;
    }

    @Override
    public void configure(ConsoleSettings settings) {
        noColor = settings.colorDisabled();
        verbose = settings.verbose();
    }

    @Override
    public void info(String message) {
        output.println(message);
    }

    @Override
    public void success(String message) {
        output.println(message);
    }

    @Override
    public void warning(String message) {
        output.println(message);
    }

    @Override
    public void failure(String message) {
        error.println(message);
    }

    @Override
    public void diagnostic(String message) {
        if (verbose) {
            output.println("Diagnostic: " + message);
        }
    }

    @Override
    public boolean isVerbose() {
        return verbose;
    }

    String output() {
        output.flush();
        return outputBuffer.toString();
    }

    String errors() {
        error.flush();
        return errorBuffer.toString();
    }

    List<String> prompts() {
        return List.copyOf(prompts);
    }

    boolean noColor() {
        return noColor;
    }
}
