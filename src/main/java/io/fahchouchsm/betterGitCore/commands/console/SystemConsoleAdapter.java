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

/** Real console implementation with terminal-aware ANSI styling. */
public final class SystemConsoleAdapter implements ConsolePort {
    private final BufferedReader input;
    private final PrintWriter output;
    private final PrintWriter error;
    private Ansi ansi = Ansi.AUTO;
    private boolean verbose;

    public SystemConsoleAdapter() {
        this(
                System.in,
                new PrintWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8), true),
                new PrintWriter(new OutputStreamWriter(System.err, StandardCharsets.UTF_8), true));
    }

    public SystemConsoleAdapter(InputStream input, PrintWriter output, PrintWriter error) {
        this.input = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
        this.output = output;
        this.error = error;
    }

    @Override
    public String readLine(String prompt) throws IOException {
        output.print(prompt);
        output.flush();
        String answer = input.readLine();
        return answer == null ? "" : answer;
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

    private String styled(String message, Style style) {
        return ansi.enabled() ? style.on() + message + style.off() : message;
    }
}
