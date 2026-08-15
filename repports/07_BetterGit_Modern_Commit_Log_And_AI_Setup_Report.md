# 07 — BetterGit Modern Commit, Log, and AI Setup Report

**Report date:** 2026-08-15

**Feature branch:** `feature/modern-bettergit-workflow`

## Outcome

BetterGit now provides a complete modern workflow instead of a report-only utility:

- `bettergit commit` creates the real Git commit, optionally generates an AI report, and finalizes that report with the resulting commit hash.
- `bettergit log` presents a compact information-rich timeline by default and exposes detailed, filtered, and JSON views.
- `bettergit ai setup` securely guides the user through API key, model, and endpoint configuration.
- `bettergit init` offers the same guided AI setup immediately when AI reports are enabled and configuration is incomplete.

The previous `commit-report` command was removed because a command named `commit` must perform the commit rather than only describe it.

## `bettergit commit`

```bash
bettergit commit [DIRECTORY] [-m MESSAGE] [--no-ai]
```

Behavior:

1. Resolves the current or supplied repository.
2. Generates an AI report when the project enabled the feature and `--no-ai` was not supplied.
3. Uses `-m/--message` when present; otherwise it uses the validated AI-suggested conventional commit message.
4. Commits only staged Git changes through JGit.
5. Prints the short hash and final message.
6. Renames `pending-<timestamp>.md` to `.bettergit/reports/<40-character-commit-hash>.md`.
7. Replaces the matching `pending` entry in recent history with the final hash.

An AI provider failure, invalid AI response, incomplete configuration, or unreadable local AI context does not block a commit that has an explicit `-m` message. Git errors and empty staged changes are not swallowed.

## `bettergit log`

```bash
bettergit log [DIRECTORY] [OPTIONS]
```

The default timeline displays:

- repository name, current branch or detached HEAD, repository state, and clean/dirty counts;
- staged, unstaged, untracked, and conflicted file counts;
- upstream branch with ahead/behind counts;
- BetterGit initialization state and finalized AI report count;
- short commit hash, local/remote branch and tag decorations, subject, author, relative time;
- changed-file count, additions, deletions, and binary-file count;
- timeline markers that distinguish regular and merge commits;
- a `✦` marker when a commit has a finalized BetterGit AI report.

Available views and filters:

- `-n, --limit 1..1000`
- `--all`
- `--author TEXT`
- `--grep TEXT`
- `--since WHEN`
- `--until WHEN`
- `--path PATH`
- `--reverse`
- `-d, --details`
- `--files`
- `--full-hash`
- `--no-graph`
- `--no-stats`
- `--json`

Date filters accept ISO-8601 instants, `YYYY-MM-DD`, `today`, `yesterday`, and relative expressions such as `30m`, `12h`, `7d`, or `2w`.

The detailed view adds:

- complete author and committer identities;
- exact author date and timezone;
- parent hashes and root/merge information;
- tree hash and commit encoding;
- GPG signature presence, explicitly marked as not cryptographically verified;
- complete commit message body and parsed footer lines;
- rename-aware file paths and per-file line statistics;
- finalized BetterGit report path.

JSON emits the complete structured repository and commit model, including full hashes, identities, timestamps, parents, tree, encoding, footers, refs, signature presence, BetterGit report path, statistics, and file changes.

## Guided AI setup

```bash
bettergit ai setup [DIRECTORY]
```

When setup is needed, BetterGit:

1. Links directly to Google AI Studio’s API-key page.
2. Reads the key with hidden terminal input.
3. Offers the stable `gemini-2.5-flash` model as the default.
4. Offers the official Gemini `generateContent` REST endpoint template as the default.
5. Adds `.env` to `.gitignore` before credential persistence.
6. Atomically writes local AI settings and applies owner read/write permissions (`0600`) on POSIX filesystems.
7. Preserves unrelated `.env` entries and supported AI entries that were not replaced.
8. Rejects newline injection and refuses symbolic `.env` files.
9. Keeps the API key out of `.bettergit/config.json`, logs, generated reports, command output, and diagnostic representations.

Environment variables still take precedence over the local `.env` mechanism:

- `AI_API_KEY`
- `AI_API_MODEL`
- `AI_API_URL`

The non-secret selected model is synchronized to `.bettergit/config.json` when the project was already initialized.

## Architecture

- `history`: immutable repository/commit information models, JGit extraction, relative/calendar date parsing, text rendering, and JSON rendering.
- `commands`: real commit orchestration, log options, nested AI setup command, and composition-root wiring.
- `configuration`: guided setup service, safe credential persistence, bounded/atomic storage reuse, and non-secret model synchronization.
- `commitreport`: pending report finalization and recent-history hash finalization.
- `JGitManager`: staged commit execution with specific empty-commit and Git failure handling.

The implementation uses the existing JGit, Picocli, JLine, Gson, AI generator, configuration loader, and commit-report components. No new dependency was added.

## Verification

Automated coverage includes:

- real temporary JGit repositories with multiple authors, dates, tags, file changes, path filters, reverse order, and empty history;
- repository state, refs, statistics, BetterGit state, and finalized report discovery;
- rich text, detailed metadata, JSON serialization, footers, and flexible date parsing;
- credential preservation, atomic owner-only storage, multiline injection rejection, and symbolic-file rejection;
- guided CLI setup without secret output;
- explicit-message commits when AI is disabled or the provider fails;
- AI-suggested commits with report and recent-history finalization;
- actual staged-only JGit commits.

Commands used:

```bash
mvn -q test
mvn -q clean package
java -jar target/betterGit-core-1.0.jar --help
java -jar target/betterGit-core-1.0.jar log --help
java -jar target/betterGit-core-1.0.jar ai setup --help
java -jar target/betterGit-core-1.0.jar commit --help
```

Result: `78` tests, `0` failures, `0` errors, `0` skipped.

An end-to-end disposable-repository smoke test additionally verified guided AI setup, `0600` `.env` permissions, absence of the test credential from output, a real staged commit, rich history, and parseable JSON.

## Clean-code guard

The guard pass:

- split Git extraction, rendering, date parsing, credential persistence, setup, and CLI orchestration into focused responsibilities;
- replaced the report-only command with truthful commit semantics;
- extracted typed dependency and query records instead of parameter-heavy calls;
- verified every new JGit API against the installed 7.3 library and reused the established console boundary;
- fixed Java `Path` JSON serialization without illegal reflective access;
- fixed multiline credential validation order and temporary-file cleanup;
- avoided calculating commit diffs when all change views are explicitly disabled;
- preserved existing Git, AI, `.env`, configuration, and report abstractions.

No unresolved production-code finding remains in this feature.
