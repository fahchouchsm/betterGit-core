# BetterGit Interactive Init Menu Report

**Report date:** 2026-08-15

## Requested improvement

Replace the basic `y`/`n` initialization prompts with a polished terminal menu that supports arrow-key navigation, similar to interactive Node.js and `npx` command-line tools.

## Root cause of the missing prompts

The tested `ignore` directory contains no Maven or Gradle build file and no Java source directory. BetterGit therefore detected it as a non-Java project and the previous implementation deliberately skipped all three Java-only configuration questions.

## Implemented behavior

### Java projects

Running `bettergit init` in a Java project now displays a checkbox menu:

```text
? Select the Java features to enable  (↑/↓ move, Space select, Enter confirm)
❯ ◯ Save a class diagram on each commit
  ◯ Track test duration on each commit
  ◯ Generate SonarQube documentation
```

- Up and Down move the cursor.
- Space toggles the current feature.
- Enter confirms all selections.
- The selected values are persisted in `.bettergit/config.json`.

### Non-Java projects

Limited mode cannot provide the Java-only features, so BetterGit now asks whether to continue instead of silently proceeding:

```text
? Continue with BetterGit limited mode?  (↑/↓ move, Enter confirm)
❯   Continue
    Cancel initialization
```

Cancelling returns before BetterGit writes files or initializes Git.

### Non-interactive use

- `-y` and `--yes` still skip every menu and apply safe defaults.
- Redirected input and environments without an attached terminal use compatible text prompts.
- Invalid text answers are rejected and requested again.

## Technical implementation

- Added JLine 4.1.3 for terminal raw mode, portable key sequence handling, and safe terminal restoration.
- Added generic confirmation and multi-selection operations to the existing console boundary.
- Kept initialization policy separate from terminal rendering.
- Preserved the existing uncommitted short-option changes in `BetterGitCommand`, `InitCommand`, their tests, and the current progress report.
- Rebuilt the executable shaded JAR at `target/betterGit-core-1.0.jar`.

## Verification

Automated verification:

```bash
mvn -q clean package
```

Result:

- 50 tests passed.
- 0 failures.
- 0 errors.
- 0 skipped.
- `git diff --check` passed.

Real pseudo-terminal verification covered:

- Moving through the Java menu with Down-arrow input.
- Selecting the first and third options with Space.
- Persisting enabled, disabled, enabled in the generated JSON configuration.
- Navigating to `Cancel initialization` in limited mode.
- Confirming that cancellation created no files and did not initialize Git.

## Clean-code guard

The production diff was reviewed in `clean-code-guard` live mode. The pass verified small focused functions, intent-revealing names, terminal state restoration in a `finally` block, specific error propagation, no swallowed failures, no dead production paths, and use of a terminal library instead of maintaining custom raw-terminal parsing.

Guard result: 2 issues fixed, 0 flagged for author.

- `InteractiveMenu` — split the initial input loop into focused state, rendering, input, and submission operations.
- `InteractiveMenu` — selected JLine's Unix execution provider to avoid native-access warnings in the supported Linux runtime.
