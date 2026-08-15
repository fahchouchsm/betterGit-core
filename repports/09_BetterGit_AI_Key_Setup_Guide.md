# 09 — BetterGit AI Key Setup Guide

## Recommended setup

From the BetterGit project directory, build the executable JAR and run the guided setup against the project that will use AI:

```bash
mvn clean package
java -jar target/betterGit-core-1.0.jar ai setup [DIRECTORY]
```

For the current project:

```bash
java -jar target/betterGit-core-1.0.jar ai setup
```

For another project such as `ignore/`:

```bash
java -jar target/betterGit-core-1.0.jar ai setup ignore/
```

BetterGit links to the Gemini API-key page, hides the key during entry, and offers these defaults:

- Model: `gemini-2.5-flash`
- Endpoint: `https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent`

Press Enter to accept each default.

## Storage and precedence

Guided setup saves the key as `AI_API_KEY` in the target project's `.env`. BetterGit adds `.env` to `.gitignore`, writes it atomically, and uses owner-only permissions where supported. The raw key is not stored in `.bettergit/config.json`.

The equivalent environment-variable setup is:

```bash
export AI_API_KEY='your-key'
export AI_API_MODEL='gemini-2.5-flash'
export AI_API_URL='https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent'
```

Environment variables take precedence over values in the project `.env`.

## Usage

After initialization has AI commit reports enabled, stage meaningful changes and run:

```bash
java -jar /path/to/betterGit-core-1.0.jar commit
```

If the `bettergit` launcher is installed on `PATH`, use `bettergit ai setup` and `bettergit commit` instead.
