package io.fahchouchsm.betterGitCore.commands.console;

import org.jline.keymap.BindingReader;
import org.jline.keymap.KeyMap;
import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.InfoCmp.Capability;

import java.io.EOFException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

final class InteractiveMenu {
    private static final int PREVIOUS = 1;
    private static final int NEXT = 2;
    private static final int TOGGLE = 3;
    private static final int SUBMIT = 4;
    private static final int IGNORE = 5;

    boolean confirm(String question, ConfirmationDefault defaultChoice) throws IOException {
        int initialCursor = defaultChoice == ConfirmationDefault.YES ? 0 : 1;
        List<Boolean> selection = run(
                new MenuView(question, List.of("Continue", "Cancel initialization"), SelectionMode.SINGLE),
                initialCursor);
        return selection.getFirst();
    }

    List<Boolean> chooseMany(String question, List<String> choices) throws IOException {
        return run(new MenuView(question, choices, SelectionMode.MULTIPLE), 0);
    }

    private List<Boolean> run(MenuView menu, int initialCursor) throws IOException {
        try (Terminal terminal = TerminalBuilder.builder().system(true).provider("exec").build()) {
            return readSelection(terminal, menu, initialCursor);
        }
    }

    private List<Boolean> readSelection(Terminal terminal, MenuView menu, int initialCursor) throws IOException {
        Attributes originalAttributes = terminal.getAttributes();
        terminal.enterRawMode();
        enterMenuScreen(terminal);
        try {
            return selectionLoop(terminal, menu, initialCursor);
        } finally {
            leaveMenuScreen(terminal);
            terminal.setAttributes(originalAttributes);
            terminal.flush();
        }
    }

    private static void enterMenuScreen(Terminal terminal) {
        terminal.puts(Capability.enter_ca_mode);
        terminal.puts(Capability.cursor_invisible);
        terminal.puts(Capability.keypad_xmit);
        terminal.flush();
    }

    private static void leaveMenuScreen(Terminal terminal) {
        terminal.puts(Capability.keypad_local);
        terminal.puts(Capability.cursor_visible);
        terminal.puts(Capability.exit_ca_mode);
    }

    private List<Boolean> selectionLoop(Terminal terminal, MenuView menu, int initialCursor) throws IOException {
        MenuState state = new MenuState(menu.choices().size(), initialCursor);
        BindingReader keys = new BindingReader(terminal.reader());
        KeyMap<Integer> bindings = bindings(terminal);
        while (true) {
            render(terminal, menu, state);
            int action = readAction(keys, bindings);
            if (action == SUBMIT) {
                terminal.writer().println();
                return state.submittedSelection(menu.mode());
            }
            state.apply(action, menu.mode());
        }
    }

    private static int readAction(BindingReader keys, KeyMap<Integer> bindings) throws IOException {
        Integer action = keys.readBinding(bindings);
        if (action == null) {
            throw new EOFException("Terminal input closed during setup.");
        }
        return action;
    }

    private static KeyMap<Integer> bindings(Terminal terminal) {
        KeyMap<Integer> bindings = new KeyMap<>();
        bindArrow(bindings, PREVIOUS, terminal.getStringCapability(Capability.key_up), "\033[A", "\033OA");
        bindArrow(bindings, NEXT, terminal.getStringCapability(Capability.key_down), "\033[B", "\033OB");
        bindings.bind(TOGGLE, " ");
        bindings.bind(SUBMIT, "\r", "\n");
        bindings.setNomatch(IGNORE);
        bindings.setUnicode(IGNORE);
        return bindings;
    }

    private static void bindArrow(KeyMap<Integer> bindings, int action, String terminalKey, String... fallbacks) {
        bindings.bind(action, fallbacks);
        if (terminalKey != null) {
            bindings.bind(action, terminalKey);
        }
    }

    private static void render(Terminal terminal, MenuView menu, MenuState state) {
        clearScreen(terminal);
        terminal.writer().println("  BETTERGIT SETUP");
        terminal.writer().println("  " + "─".repeat(dividerWidth(terminal)));
        terminal.writer().println();
        terminal.writer().println("  " + menu.question());
        terminal.writer().println();
        for (int index = 0; index < menu.choices().size(); index++) {
            terminal.writer().println("  " + choiceLine(
                    menu.choices().get(index), state, index, menu.mode()));
        }
        terminal.writer().println();
        terminal.writer().println("  " + instruction(menu.mode()));
        terminal.flush();
    }

    private static void clearScreen(Terminal terminal) {
        if (!terminal.puts(Capability.clear_screen)) {
            terminal.writer().print("\033[H\033[2J");
        }
    }

    private static int dividerWidth(Terminal terminal) {
        return Math.max(20, Math.min(60, terminal.getWidth() - 4));
    }

    private static String instruction(SelectionMode mode) {
        return mode == SelectionMode.MULTIPLE
                ? "↑/↓ Navigate   Space Toggle   Enter Confirm"
                : "↑/↓ Navigate   Enter Confirm";
    }

    private static String choiceLine(String choice, MenuState state, int index, SelectionMode mode) {
        String pointer = state.cursorAt(index) ? "❯" : " ";
        String marker = mode == SelectionMode.MULTIPLE
                ? (state.selectedAt(index) ? "◉" : "◯")
                : " ";
        return "%s %s %s".formatted(pointer, marker, choice);
    }

    private static List<Boolean> immutableSelection(boolean[] selected) {
        List<Boolean> choices = new ArrayList<>(selected.length);
        for (boolean selection : selected) {
            choices.add(selection);
        }
        return List.copyOf(choices);
    }

    private record MenuView(String question, List<String> choices, SelectionMode mode) {
    }

    private enum SelectionMode {
        SINGLE,
        MULTIPLE
    }

    private static final class MenuState {
        private final boolean[] selected;
        private int cursor;

        private MenuState(int choiceCount, int initialCursor) {
            selected = new boolean[choiceCount];
            cursor = initialCursor;
        }

        private void apply(int action, SelectionMode mode) {
            cursor = switch (action) {
                case PREVIOUS -> Math.floorMod(cursor - 1, selected.length);
                case NEXT -> (cursor + 1) % selected.length;
                default -> cursor;
            };
            if (mode == SelectionMode.MULTIPLE && action == TOGGLE) {
                selected[cursor] = !selected[cursor];
            }
        }

        private List<Boolean> submittedSelection(SelectionMode mode) {
            if (mode == SelectionMode.SINGLE) {
                selected[cursor] = true;
            }
            return immutableSelection(selected);
        }

        private boolean cursorAt(int index) {
            return cursor == index;
        }

        private boolean selectedAt(int index) {
            return selected[index];
        }
    }
}
