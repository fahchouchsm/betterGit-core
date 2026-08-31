Adds native Git command passthrough and project-local repository initialization.

## Changes

- BetterGit forwards commands it does not enhance directly to Git, including `add`, `status`, `branch`, `switch`, `pull`, and `push`.
- `bettergit init` now creates a repository at the selected project boundary instead of inheriting a repository from a parent directory.
- Empty commits now direct users to `bettergit add` rather than requiring the Git executable directly.

## Validation

The command-routing, nested-initialization, staging, and existing workflow tests pass with the rebuilt executable JAR.
