package io.fahchouchsm.betterGitCore.commitreport;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommitReportValidatorTest {
    private final CommitReportValidator validator = new CommitReportValidator();

    @Test
    void normalizesTheOpeningParagraphAsTheCommitMessage() {
        ValidatedCommitReport report = validator.validate("""
                Adds concise documentation for
                the staged configuration changes.

                ## Changes
                - Updates the commit workflow.

                ## Validation
                The automated tests passed.
                """);

        String expectedMessage = "Adds concise documentation for the staged configuration changes.";
        assertEquals(expectedMessage, report.commitMessage());
        assertTrue(report.markdown().startsWith(expectedMessage + "\n\n## Changes"));
    }

    @Test
    void rejectsMultipleOpeningParagraphsAndAdditionalHeadings() {
        String multipleParagraphs = """
                First paragraph.

                Second paragraph.

                ## Changes
                - A change.

                ## Validation
                Not run.
                """;
        String additionalHeading = """
                Concise description.

                ## Changes
                - A change.

                ## Validation
                Not run.

                ## Extra details
                Too much detail.
                """;

        assertThrows(IllegalArgumentException.class, () -> validator.validate(multipleParagraphs));
        assertThrows(IllegalArgumentException.class, () -> validator.validate(additionalHeading));
    }

    @Test
    void rejectsBlankChangesValidationAndOverlongDescriptions() {
        String emptyChanges = "Description.\n\n## Changes\n\n## Validation\nNot run.\n";
        String emptyValidation = "Description.\n\n## Changes\n- A change.\n\n## Validation\n";
        String overlongDescription = "x".repeat(161)
                + "\n\n## Changes\n- A change.\n\n## Validation\nNot run.\n";
        String tooManyChanges = "Description.\n\n## Changes\n"
                + "- A change.\n".repeat(6) + "\n## Validation\nNot run.\n";
        String multipleValidationParagraphs = "Description.\n\n## Changes\n- A change.\n\n"
                + "## Validation\nTests passed.\n\nManual review passed.\n";

        assertThrows(IllegalArgumentException.class, () -> validator.validate(emptyChanges));
        assertThrows(IllegalArgumentException.class, () -> validator.validate(emptyValidation));
        assertThrows(IllegalArgumentException.class, () -> validator.validate(overlongDescription));
        assertThrows(IllegalArgumentException.class, () -> validator.validate(tooManyChanges));
        assertThrows(IllegalArgumentException.class, () -> validator.validate(multipleValidationParagraphs));
    }
}
