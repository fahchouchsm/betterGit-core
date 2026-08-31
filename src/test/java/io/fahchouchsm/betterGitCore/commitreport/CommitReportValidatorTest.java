package io.fahchouchsm.betterGitCore.commitreport;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void usesFirstOpeningParagraphAndRejectsAdditionalHeadings() {
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

        assertEquals("First paragraph.", validator.validate(multipleParagraphs).commitMessage());
        assertThrows(IllegalArgumentException.class, () -> validator.validate(additionalHeading));
    }

    @Test
    void rejectsBlankChangesValidationAndOverlongDescriptions() {
        String emptyChanges = "Description.\n\n## Changes\n\n## Validation\nNot run.\n";
        String emptyValidation = "Description.\n\n## Changes\n- A change.\n\n## Validation\n";
        String overlongDescription = "x".repeat(161)
                + "\n\n## Changes\n- A change.\n\n## Validation\nNot run.\n";
        String multipleValidationParagraphs = "Description.\n\n## Changes\n- A change.\n\n"
                + "## Validation\nTests passed.\n\nManual review passed.\n";

        assertThrows(IllegalArgumentException.class, () -> validator.validate(emptyChanges));
        assertThrows(IllegalArgumentException.class, () -> validator.validate(emptyValidation));
        assertThrows(IllegalArgumentException.class, () -> validator.validate(overlongDescription));
        assertThrows(IllegalArgumentException.class, () -> validator.validate(multipleValidationParagraphs));
    }

    @Test
    void keepsOnlyTheFirstFiveChangeBullets() {
        String response = """
                Description.

                ## Changes
                - Change 1.
                - Change 2.
                - Change 3.
                - Change 4.
                - Change 5.
                - Change 6.

                ## Validation
                Not run.
                """;

        ValidatedCommitReport report = validator.validate(response);

        assertEquals(5, report.changedAreas().size());
        assertTrue(report.markdown().contains("- Change 5."));
        assertFalse(report.markdown().contains("- Change 6."));
    }

    @Test
    void acceptsAndNormalizesStandardMarkdownBulletMarkers() {
        String response = """
                Refactor Blackjack game structure, add game controller and view.

                ## Changes
                *   Added a game controller.
                + Added a console view.
                - Moved card classes into the model package.

                ## Validation
                Validation was not run or provided.
                """;

        ValidatedCommitReport report = validator.validate(response);

        assertEquals(List.of(
                "Added a game controller.",
                "Added a console view.",
                "Moved card classes into the model package."), report.changedAreas());
        assertTrue(report.markdown().contains("- Added a game controller."));
        assertFalse(report.markdown().contains("*   Added"));
        assertFalse(report.markdown().contains("+ Added"));
    }
}
