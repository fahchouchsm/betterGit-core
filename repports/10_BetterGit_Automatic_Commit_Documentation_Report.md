# 10 — BetterGit Automatic Commit Documentation Report

## Outcome

AI-enabled commits now generate a concise Markdown document before Git commits the staged changes. The document's opening paragraph is normalized to one line, limited to 160 characters, and used unchanged as the Git commit message.

## Commit behavior

```bash
bettergit commit [DIRECTORY]
```

- The command reads the BetterGit configuration from the selected directory.
- If AI reporting is enabled but its credentials are incomplete, guided setup asks for the missing configuration and stores the API key in the ignored project `.env` file.
- A valid report contains one opening description paragraph, `## Changes`, and `## Validation`.
- The report must be generated successfully before an AI-enabled commit proceeds.
- After Git returns the commit hash, the report is saved as `.bettergit/reports/<commit-hash>.md`.
- `-m` remains available for projects with AI reporting disabled or when `--no-ai` is explicitly selected.

The directory argument matters: `bettergit init ignore/` configures `ignore/`, so the matching commit command is `bettergit commit ignore/`. A bare `bettergit commit` targets the current directory and now reports the missing configuration path clearly.

## Safety and validation

- The generated opening description is the exact Git commit message and the first Markdown paragraph.
- Additional headings, multiple opening paragraphs, blank change lists, blank validation, and descriptions longer than 160 characters are rejected.
- Provider failures and invalid responses cancel AI-enabled commits instead of creating undocumented commits.
- Existing secret filtering, hidden key entry, atomic credential storage, report finalization, and history finalization remain in place.

## Verification

- Missing-key setup and local persistence are covered without exposing the key in output.
- AI failure is verified to leave Git untouched.
- Explicit messages are verified not to replace generated report descriptions.
- Markdown normalization and boundary validation are covered directly.
