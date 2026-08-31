package io.fahchouchsm.betterGitCore.commands.console;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SystemConsoleAdapterTest {
    @Test
    void redirectedInputUsesTextPromptsForMultipleChoices() throws Exception {
        ConsoleFixture fixture = consoleWithInput("yes\nno\n\n");

        List<Boolean> selections = fixture.console().chooseMany(
                "Choose features", List.of("First", "Second", "Third"));

        assertEquals(List.of(true, false, false), selections);
        assertTrue(fixture.output().toString().contains("First [y/N]:"));
    }

    @Test
    void redirectedConfirmationRejectsInvalidAnswers() throws Exception {
        ConsoleFixture fixture = consoleWithInput("later\ny\n");

        boolean confirmed = fixture.console().confirm("Continue?", ConfirmationDefault.NO);

        assertTrue(confirmed);
        assertTrue(fixture.output().toString().contains("Please answer y, yes, n, or no."));
    }

    @Test
    void redirectedConfirmationUsesTheDisplayedDefault() throws Exception {
        ConsoleFixture yesByDefault = consoleWithInput("\n");
        ConsoleFixture noByDefault = consoleWithInput("\n");

        assertTrue(yesByDefault.console().confirm("Continue?", ConfirmationDefault.YES));
        assertFalse(noByDefault.console().confirm("Continue?", ConfirmationDefault.NO));
    }

    @Test
    void redirectedFeatureSelectionPreservesCurrentSettingsByDefault() throws Exception {
        ConsoleFixture fixture = consoleWithInput("\n\n\n");

        List<Boolean> selections = fixture.console().chooseMany(
                "Choose features",
                List.of("First", "Second", "Third"),
                List.of(true, false, true));

        assertEquals(List.of(true, false, true), selections);
        assertTrue(fixture.output().toString().contains("First [Y/n]:"));
        assertTrue(fixture.output().toString().contains("Second [y/N]:"));
    }

    private static ConsoleFixture consoleWithInput(String answers) {
        ByteArrayInputStream input = new ByteArrayInputStream(answers.getBytes(StandardCharsets.UTF_8));
        StringWriter output = new StringWriter();
        SystemConsoleAdapter console = new SystemConsoleAdapter(
                input, new PrintWriter(output, true), new PrintWriter(new StringWriter(), true));
        return new ConsoleFixture(console, output);
    }

    private record ConsoleFixture(SystemConsoleAdapter console, StringWriter output) {
    }
}
