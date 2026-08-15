# 06 — BetterGit AI Commit Report Generator Report

**Report date:** 2026-08-15

## Outcome

BetterGit now supports opt-in, evidence-based AI commit reports for staged Git changes. Initialization stores only non-secret settings, prepares compact local project memory when requested, and excludes generated reports from Git by default. Report generation is provider-independent through the existing `AiTextGenerator` abstraction and never blocks a Git workflow when AI is unavailable or returns invalid content.

## CLI behavior

### Initialization

`bettergit init [DIRECTORY]` adds these interactive questions:

1. `Enable AI commit report generator?` — default `no`.
2. If enabled and no API key is available, BetterGit prints a non-blocking `AI_API_KEY` configuration warning.
3. If the current environment, `.env`, or stored BetterGit settings do not define a model, BetterGit asks for the model. A blank answer defers configuration.
4. `Maintain local BetterGit AI memory/context?` — default `yes`.

`bettergit init --yes` keeps the feature disabled because its safe default is `no`.

### Report generation

```bash
bettergit commit [DIRECTORY] [-m MESSAGE]
```

The command reads the staged diff, branch, staged file statuses, and available lightweight BetterGit context. It creates the real Git commit, then finalizes `.bettergit/reports/<commit-hash>.md` and prints the hash, report path, and selected message.

Disabled features, incomplete AI configuration, empty or fully filtered staged changes, provider failures, timeouts, and invalid AI Markdown are safe skips with exit code `0`. There is no `bettergit commit` orchestration in the current project, so the report remains `pending-*`; no final commit hash is available for automatic renaming.

## Persisted configuration

The schema version is now `2`. The non-secret settings are:

```json
{
  "ai": {
    "commitReportEnabled": true,
    "memoryEnabled": true,
    "model": "configured-model"
  }
}
```

Raw API keys are never written to `.bettergit/config.json`, memory, reports, console diagnostics, or exception output.

Environment and existing AI settings:

- `AI_API_KEY` — provider credential; required for generation.
- `AI_API_MODEL` — takes precedence over the stored non-secret model.
- `AI_API_URL` — existing provider endpoint configuration.
- `BETTERGIT_AI_MAX_INPUT_CHARS` — optional prompt limit; default `60000`, environment values clamped to `8000..500000`.

The existing project `.env` loading mechanism remains supported. `.env` is added to `.gitignore` when present.

## BetterGit memory

When reports and memory are enabled, BetterGit safely creates or reuses:

```text
.bettergit/
  config.json
  general.md
  context/
    project-map.json
    recent-history.md
  reports/
```

- Existing `general.md` content is preserved. A missing file receives a compact local summary based on project metadata.
- `project-map.json` contains Maven modules, source roots, packages, Java types, and test locations. It excludes build, dependency, Git, and BetterGit directories and is rewritten only when its generated content changes.
- `recent-history.md` stores at most the latest 10 deduplicated summaries. A prompt receives at most the configured recent subset and never full historical diffs.
- Missing memory files are created or treated as empty without preventing generation from the current staged diff.

## Prompt safety and context selection

The staged diff is the primary evidence. BetterGit adds only changed-file metadata, statistics, branch, available validation status, compact memory, and selective Java declarations/imports plus changed hunk regions.

The context builder:

- excludes binary files, private-key and credential filenames, `.env` variants, dependency directories, and generated/build folders;
- excludes a rename when either its old or new path is sensitive;
- redacts configured credentials, bearer tokens, private-key blocks, and common secret assignments from diffs, memory, branch/file metadata, selective source, and generated reports;
- bounds memory reads, project scanning, Java source size, selective source context, and final prompt length;
- marks a trimmed prompt with `[TRUNCATED BY BETTERGIT]`.

## Main production changes

- `commands`: added real commit orchestration, reusable directory resolution, runtime wiring, and init prompts.
- `configuration`: added schema-v2 AI settings, config loading, bounded UTF-8 reads, safe BetterGit directories, and reusable atomic writes.
- `commitreport`: added staged Git adaptation, memory management, project mapping, source selection, filtering, context and prompt construction, response validation, report persistence, and failure-safe orchestration.
- `JGitManager`: added current-branch lookup while reusing the existing staged change and diff extraction.

No new AI provider implementation was added and no working Git diff extraction was replaced.

## Tests and verification

Focused JUnit 5 coverage includes:

- disabled feature without Git or AI calls;
- missing API key or model as a safe skip;
- diff, memory, file-status, and selective Java context composition;
- missing-memory fallback;
- secret, credential-file, and generated/dependency filtering;
- input trimming with an explicit marker;
- 10-entry recent-history retention;
- successful Markdown persistence and suggested-message extraction;
- provider failure without a failing Git workflow;
- init persistence, model reuse, safe defaults, and report-directory ignore behavior;
- CLI registration, help, target-directory behavior, and disabled-feature execution.

Verified commands:

```bash
mvn -q test
mvn -q clean package
java -jar target/betterGit-core-1.0.jar --help
java -jar target/betterGit-core-1.0.jar commit --help
```

Result: `65` tests, `0` failures, `0` errors, `0` skipped. A temporary-repository smoke test also verified interactive initialization, secret-free schema-v2 JSON, memory file creation, `.gitignore`, and the safe missing-AI skip.

## Assumptions

- Reports are generated before the commit and finalized by hash only after JGit returns a successful commit.
- Test/build results are not currently exposed by the Git/commit runtime, so reports explicitly receive `Validation was not run or provided.`
- Model choices are provider-specific and BetterGit has no provider model registry, so a missing model is entered as text instead of using a hard-coded list.
- Existing `.env` support is the project’s configured local AI mechanism; credentials remain outside `.bettergit/`.

## Clean-code guard

The final guard pass extracted configuration and persistence responsibilities, replaced behavior booleans with explicit menu/default types, bounded file reads, split report generation into focused collaborators, removed parameter-heavy methods, preserved the existing AI/Git abstractions, and fixed secret exposure through selective source context and rename metadata.

No unresolved clean-code finding remains in the AI commit report implementation.
