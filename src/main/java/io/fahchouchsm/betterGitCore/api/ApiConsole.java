package io.fahchouchsm.betterGitCore.api;

import io.fahchouchsm.betterGitCore.commands.console.ConfirmationDefault;
import io.fahchouchsm.betterGitCore.commands.console.ConsolePort;
import io.fahchouchsm.betterGitCore.commands.console.ConsoleSettings;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.List;

final class ApiConsole implements ConsolePort {
    private final OperationObserver observer;
    private final PrintWriter discardedOutput = new PrintWriter(Writer.nullWriter());
    private boolean verbose;

    ApiConsole(OperationObserver observer) {
        this.observer = observer;
    }

    @Override
    public String readLine(String prompt) throws IOException {
        throw unsupportedInteractiveInput(prompt);
    }

    @Override
    public String readSecret(String prompt) throws IOException {
        throw unsupportedInteractiveInput(prompt);
    }

    @Override
    public boolean confirm(String question, ConfirmationDefault defaultChoice) throws IOException {
        return observer.approve(question, defaultChoice == ConfirmationDefault.YES);
    }

    @Override
    public int chooseOne(String question, List<String> choices, int defaultChoice) throws IOException {
        throw unsupportedInteractiveInput(question);
    }

    @Override
    public List<Boolean> chooseMany(
            String question, List<String> choices, List<Boolean> initialSelections) throws IOException {
        throw unsupportedInteractiveInput(question);
    }

    @Override
    public PrintWriter out() {
        return discardedOutput;
    }

    @Override
    public PrintWriter err() {
        return discardedOutput;
    }

    @Override
    public void configure(ConsoleSettings settings) {
        verbose = settings.verbose();
    }

    @Override
    public void info(String message) {
        emit(EventSeverity.INFORMATION, message);
    }

    @Override
    public void success(String message) {
        emit(EventSeverity.SUCCESS, message);
    }

    @Override
    public void warning(String message) {
        emit(EventSeverity.WARNING, message);
    }

    @Override
    public void failure(String message) {
        emit(EventSeverity.FAILURE, message);
    }

    @Override
    public void diagnostic(String message) {
        if (verbose) {
            emit(EventSeverity.DIAGNOSTIC, message);
        }
    }

    @Override
    public boolean isVerbose() {
        return verbose;
    }

    private void emit(EventSeverity severity, String message) {
        observer.onEvent(new OperationEvent(severity, message));
    }

    private static IOException unsupportedInteractiveInput(String prompt) {
        return new IOException("The operation requires interactive setup: " + prompt.strip());
    }
}
