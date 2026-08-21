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

BetterGit asks which service to use, links to that service's API-key page, and hides the key during entry. Built-in choices are:

- OpenAI
- Google Gemini
- Anthropic Claude
- OpenRouter
- Groq
- A custom OpenAI-compatible API

For built-in services, BetterGit uses the official API endpoint and can retrieve the available models for interactive selection. If discovery is unavailable or declined, enter the model manually. A custom service requires its chat-completions URL and supports model discovery when the URL ends in `/chat/completions`.

## Storage and precedence

Guided setup saves the provider, key, model, and endpoint in the target project's `.env`. BetterGit adds `.env` to `.gitignore`, writes it atomically, and uses owner-only permissions where supported. The raw key is not stored in `.bettergit/config.json`.

For example, the equivalent Gemini environment-variable setup is:

```bash
export AI_API_PROVIDER='gemini'
export AI_API_KEY='your-key'
export AI_API_MODEL='gemini-2.5-flash'
export AI_API_URL='https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent'
```

Environment variables take precedence over values in the project `.env`.

`AI_API_PROVIDER` accepts `gemini`, `anthropic`, or `openai-compatible`. For backward compatibility, BetterGit infers the provider from `AI_API_URL` when the provider setting is absent.

## Usage

After initialization has AI commit reports enabled, stage meaningful changes and run:

```bash
java -jar /path/to/betterGit-core-1.0.jar commit
```

If the `bettergit` launcher is installed on `PATH`, use `bettergit ai setup` and `bettergit commit` instead.
