Saves redacted AI failure diagnostics and accepts recoverable report formatting issues.

## Changes

- Records provider and validation failures in owner-only Markdown diagnostic files.
- Redacts configured API keys before diagnostic content is persisted.
- Shows the diagnostic path when an automatic commit report cannot be generated.
- Uses the first description paragraph and limits generated change bullets to five.

## Validation

The complete Maven test suite passed after the diagnostic and validation changes.
