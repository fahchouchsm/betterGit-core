Generates a Java class-diagram SVG for every successful commit when the feature is enabled.

## Changes

- BetterGit invokes Java2Diagram automatically after a successful commit.
- Diagrams are saved as `.bettergit/diagrams/<commit-hash>.svg` and ignored by Git.
- Initialization prepares the diagram directory when the feature is selected.
- Generation failures preserve the successful commit and produce an actionable warning.

## Validation

The real Java2Diagram CLI produced an SVG, and the focused and complete BetterGit test suites passed.
