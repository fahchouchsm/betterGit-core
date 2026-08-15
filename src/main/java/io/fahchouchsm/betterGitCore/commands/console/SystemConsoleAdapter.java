package io.fahchouchsm.betterGitCore.commands.console;

import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.Help.Ansi.Style;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Real console implementation with terminal-aware ANSI styling. */
public final class SystemConsoleAdapter implements ConsolePort {
    private final BufferedReader input;
    private final PrintWriter output;
    private final PrintWriter error;
    private final TerminalInteraction terminalInteraction;
    private Ansi ansi = Ansi.AUTO;
    private boolean verbose;

    public SystemConsoleAdapter() {
        this(
                System.in,
                new PrintWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8), true),
                new PrintWriter(new OutputStreamWriter(System.err, StandardCharsets.UTF_8), true),
                System.console() == null ? TerminalInteraction.TEXT : TerminalInteraction.INTERACTIVE);
    }

    public SystemConsoleAdapter(InputStream input, PrintWriter output, PrintWriter error) {
        this(input, output, error, TerminalInteraction.TEXT);
    }

    private SystemConsoleAdapter(
            InputStream input, PrintWriter output, PrintWriter error, TerminalInteraction terminalInteraction) {
        this.input = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
        this.output = output;
        this.error = error;
        this.terminalInteraction = terminalInteraction;
    }

    @Override
    public String readLine(String prompt) throws IOException {
        output.print(prompt);
        output.flush();
        String answer = input.readLine();
        return answer == null ? "" : answer;
    }

    @Override
    public String readSecret(String prompt) throws IOException {
        if (terminalInteraction == TerminalInteraction.TEXT) {
            return readLine(prompt);
        }
        char[] secret = System.console().readPassword("%s", prompt);
        if (secret == null) {
            return "";
        }
        try {
            return new String(secret);
        } finally {
            Arrays.fill(secret, '\0');
        }
    }

    @Override
    public boolean confirm(String question, ConfirmationDefault defaultChoice) throws IOException {
        if (terminalInteraction == TerminalInteraction.INTERACTIVE) {
            return new InteractiveMenu().confirm(question, defaultChoice);
        }
        String suffix = defaultChoice == ConfirmationDefault.YES ? " [Y/n]: " : " [y/N]: ";
        return readConfirmation(question + suffix, defaultChoice);
    }

    @Override
    public List<Boolean> chooseMany(String question, List<String> choices) throws IOException {
        if (terminalInteraction == TerminalInteraction.INTERACTIVE) {
            return new InteractiveMenu().chooseMany(question, choices);
        }
        List<Boolean> selections = new ArrayList<>(choices.size());
        for (String choice : choices) {
            selections.add(readConfirmation(choice + " [y/N]: ", ConfirmationDefault.NO));
        }
        return List.copyOf(selections);
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
        ansi = settings.colorDisabled() ? Ansi.OFF : Ansi.AUTO;
        verbose = settings.verbose();
    }

    @Override
    public void info(String message) {
        output.println(message);
    }

    @Override
    public void success(String message) {
        output.println(styled(message, Style.fg_green));
    }

    @Override
    public void warning(String message) {
        output.println(styled(message, Style.fg_yellow));
    }

    @Override
    public void failure(String message) {
        error.println(styled(message, Style.fg_red));
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

    private boolean readConfirmation(String prompt, ConfirmationDefault defaultChoice) throws IOException {
        while (true) {
            String answer = readLine(prompt).trim().toLowerCase(Locale.ROOT);
            if (answer.isEmpty()) {
                return defaultChoice == ConfirmationDefault.YES;
            }
            if ("y".equals(answer) || "yes".equals(answer)) {
                return true;
            }
            if ("n".equals(answer) || "no".equals(answer)) {
                return false;
            }
            warning("Please answer y, yes, n, or no.");
        }
    }

    private String styled(String message, Style style) {
        return ansi.enabled() ? style.on() + message + style.off() : message;
    }

    private enum TerminalInteraction {
        INTERACTIVE,
        TEXT
    }
}
