Accepts and normalizes every standard Markdown bullet marker in AI commit reports.

## Changes

- AI report validation now accepts `-`, `*`, and `+` change bullets.
- Bullet spacing is normalized before the report is saved.
- A regression test covers the exact asterisk format returned by Gemini.

## Validation

The validator regression test and complete BetterGit test suite pass with the rebuilt executable JAR.
