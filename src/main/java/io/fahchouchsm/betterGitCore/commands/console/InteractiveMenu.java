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
    static final int PREVIOUS = 1;
    static final int NEXT = 2;
    static final int TOGGLE = 3;
    static final int SUBMIT = 4;
    private static final int IGNORE = 5;

    boolean confirm(String question, ConfirmationDefault defaultChoice) throws IOException {
        int initialCursor = defaultChoice == ConfirmationDefault.YES ? 0 : 1;
        List<Boolean> selection = run(
                new MenuView(question, List.of("Continue", "Cancel initialization"), SelectionMode.SINGLE),
                initialCursor);
        return selection.getFirst();
    }

    int chooseOne(String question, List<String> choices, int defaultChoice) throws IOException {
        List<Boolean> selection = run(
                new MenuView(question, choices, SelectionMode.SINGLE), defaultChoice);
        return selection.indexOf(true);
    }

    List<Boolean> chooseMany(String question, List<String> choices) throws IOException {
        List<Boolean> selectedRows = run(multipleSelectionMenu(question, choices), 0);
        return List.copyOf(selectedRows.subList(0, choices.size()));
    }

    static MenuView multipleSelectionMenu(String question, List<String> choices) {
        List<String> menuRows = new ArrayList<>(choices);
        menuRows.add("Continue");
        return new MenuView(question, List.copyOf(menuRows), SelectionMode.MULTIPLE);
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
            if (submitsSelection(action, menu, state)) {
                terminal.writer().println();
                return state.submittedSelection(menu.mode());
            }
            state.apply(selectionAction(action, menu.mode()), menu);
        }
    }

    static boolean submitsSelection(int action, MenuView menu, MenuState state) {
        return action == SUBMIT
                && (menu.mode() == SelectionMode.SINGLE || menu.isSubmitRow(state.cursor()));
    }

    static int selectionAction(int action, SelectionMode mode) {
        return action == SUBMIT && mode == SelectionMode.MULTIPLE ? TOGGLE : action;
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
        IndexWindow window = visibleWindow(terminal, menu, state);
        if (window.first() > 0) {
            terminal.writer().println("    ↑ " + window.first() + " more");
        }
        for (int index = window.first(); index < window.lastExclusive(); index++) {
            terminal.writer().println("  " + choiceLine(
                    menu.choices().get(index), state, index, menu));
        }
        if (window.lastExclusive() < menu.choices().size()) {
            terminal.writer().println("    ↓ " + (menu.choices().size() - window.lastExclusive()) + " more");
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
        return Math.max(20, Math.min(60, terminal.getSize().getColumns() - 4));
    }

    private static IndexWindow visibleWindow(Terminal terminal, MenuView menu, MenuState state) {
        int availableRows = Math.max(3, terminal.getSize().getRows() - 10);
        int visibleChoices = Math.min(availableRows, menu.choices().size());
        int first = Math.max(0, state.cursor() - visibleChoices / 2);
        first = Math.min(first, menu.choices().size() - visibleChoices);
        return new IndexWindow(first, first + visibleChoices);
    }

    private static String instruction(SelectionMode mode) {
        return mode == SelectionMode.MULTIPLE
                ? "↑/↓ Navigate   Enter Select   Space Toggle   [ Continue ] Finish"
                : "↑/↓ Navigate   Enter Confirm";
    }

    private static String choiceLine(String choice, MenuState state, int index, MenuView menu) {
        String pointer = state.cursorAt(index) ? "❯" : " ";
        if (menu.isSubmitRow(index)) {
            return "%s   [ %s ]".formatted(pointer, choice);
        }
        String marker = menu.mode() == SelectionMode.MULTIPLE
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

    record MenuView(String question, List<String> choices, SelectionMode mode) {
        boolean isSubmitRow(int index) {
            return mode == SelectionMode.MULTIPLE && index == choices.size() - 1;
        }
    }

    private record IndexWindow(int first, int lastExclusive) {
    }

    enum SelectionMode {
        SINGLE,
        MULTIPLE
    }

    static final class MenuState {
        private final boolean[] selected;
        private int cursor;

        MenuState(int choiceCount, int initialCursor) {
            selected = new boolean[choiceCount];
            cursor = initialCursor;
        }

        void apply(int action, MenuView menu) {
            cursor = switch (action) {
                case PREVIOUS -> Math.floorMod(cursor - 1, selected.length);
                case NEXT -> (cursor + 1) % selected.length;
                default -> cursor;
            };
            if (menu.mode() == SelectionMode.MULTIPLE
                    && action == TOGGLE
                    && !menu.isSubmitRow(cursor)) {
                selected[cursor] = !selected[cursor];
            }
        }

        List<Boolean> submittedSelection(SelectionMode mode) {
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

        int cursor() {
            return cursor;
        }
    }
}
