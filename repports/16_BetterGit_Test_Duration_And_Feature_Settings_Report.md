Adds configurable feature settings and records test-suite duration for each enabled commit.

## Changes

- Adds `bettergit features` with current settings preselected in the existing multi-select menu.
- Runs Maven or Gradle tests against the exact committed project snapshot.
- Saves pass/fail status, elapsed time, and the test command under `.bettergit/test-durations/`.
- Preserves failed-test timing reports and warns without misreporting the successful Git commit.

## Validation

Focused, complete, and end-to-end Maven validation covered feature selection and committed-snapshot timing.
