# BetterGit Init Target and Full-Screen Menu Report

**Report date:** 2026-08-15

## Requested changes

- Make `bettergit init <directory>` initialize a specified project directory.
- Present interactive setup as a modern full-terminal menu.
- Navigate and select with keys instead of accepting typed answers.
- Preserve and restore the user's terminal after the menu closes.

## Implemented command contract

```text
bettergit init [DIRECTORY]
```

Supported forms:

```bash
java -jar target/betterGit-core-1.0.jar init
java -jar target/betterGit-core-1.0.jar init ignore/
java -jar target/betterGit-core-1.0.jar init /absolute/project/path
```

Relative directories are resolved from the directory where BetterGit is launched. Omitting the argument preserves the original current-directory behavior.

The CLI validates the target before repository detection or file writes. A missing path or a regular file returns the Picocli usage exit code and a specific error message.

## Full-screen menu

Interactive setup now uses JLine terminal capabilities to:

- Enter the terminal's alternate screen when supported.
- Clear and use the full screen for setup.
- Hide the terminal text cursor while the menu is active.
- Display a visible `❯` selection cursor.
- Navigate with Up and Down.
- Toggle Java feature checkboxes with Space.
- Confirm with Enter.
- Ignore printable text instead of accepting or displaying typed answers.
- Restore keypad mode, cursor visibility, screen state, and terminal attributes in a `finally` block.

Non-interactive environments continue to receive compatible text prompts. `-y` and `--yes` continue to bypass menus.

## Verification

The final shaded JAR was built with:

```bash
mvn -q clean package
```

Automated result:

- 53 tests passed.
- 0 failures.
- 0 errors.
- 0 skipped.
- `git diff --check` passed.

The user's exact command was then exercised in a real pseudo-terminal:

```bash
java -jar target/betterGit-core-1.0.jar init ignore/
```

Observed result:

- `ignore/` was accepted and resolved to its absolute path.
- The full-screen limited-mode menu opened.
- A stray printable key was ignored.
- Down moved the selection from Continue to Cancel initialization.
- Enter confirmed cancellation.
- The command exited normally and wrote no files during the cancelled run.

Additional automated coverage verifies current-directory initialization, relative targets, absolute targets, missing targets, regular-file targets, help output, and non-interactive defaults.

## Clean-code guard

The production changes were reviewed in `clean-code-guard` live mode.

- `ConsolePort` and implementations — replaced an ambiguous boolean confirmation default with the explicit `ConfirmationDefault.YES`/`NO` type.
- `InteractiveMenu` — replaced boolean single/multiple flags with an explicit `SelectionMode`.
- `InteractiveMenu` — made unmatched printable input an intentional no-op instead of an input-closure error.

`clean-code-guard: 3 fixed, 0 flagged for author`
