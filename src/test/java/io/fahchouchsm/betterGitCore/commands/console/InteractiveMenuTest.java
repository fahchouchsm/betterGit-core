package io.fahchouchsm.betterGitCore.commands.console;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InteractiveMenuTest {
    @Test
    void enterTogglesMultipleFeaturesBeforeContinueSubmitsThem() {
        InteractiveMenu.MenuView menu = InteractiveMenu.multipleSelectionMenu(
                "Choose features", List.of("First", "Second"));
        InteractiveMenu.MenuState state = new InteractiveMenu.MenuState(3, 0);

        state.apply(InteractiveMenu.selectionAction(
                InteractiveMenu.SUBMIT, InteractiveMenu.SelectionMode.MULTIPLE), menu);
        state.apply(InteractiveMenu.NEXT, menu);
        state.apply(InteractiveMenu.selectionAction(
                InteractiveMenu.SUBMIT, InteractiveMenu.SelectionMode.MULTIPLE), menu);

        assertEquals(List.of(true, true, false),
                state.submittedSelection(InteractiveMenu.SelectionMode.MULTIPLE));
        assertFalse(InteractiveMenu.submitsSelection(InteractiveMenu.SUBMIT, menu, state));

        state.apply(InteractiveMenu.NEXT, menu);

        assertTrue(InteractiveMenu.submitsSelection(InteractiveMenu.SUBMIT, menu, state));
    }

    @Test
    void continueCanSubmitAnEmptyFeatureList() {
        InteractiveMenu.MenuView menu = InteractiveMenu.multipleSelectionMenu(
                "Choose features", List.of());
        InteractiveMenu.MenuState state = new InteractiveMenu.MenuState(1, 0);

        assertTrue(InteractiveMenu.submitsSelection(InteractiveMenu.SUBMIT, menu, state));
        assertEquals(List.of(false),
                state.submittedSelection(InteractiveMenu.SelectionMode.MULTIPLE));
    }

    @Test
    void multipleSelectionStartsFromTheCurrentSettings() {
        InteractiveMenu.MenuView menu = InteractiveMenu.multipleSelectionMenu(
                "Choose features", List.of("First", "Second", "Third"));
        InteractiveMenu.MenuState state = new InteractiveMenu.MenuState(
                4, 0, List.of(true, false, true));

        assertEquals(List.of(true, false, true, false),
                state.submittedSelection(InteractiveMenu.SelectionMode.MULTIPLE));
    }

}
