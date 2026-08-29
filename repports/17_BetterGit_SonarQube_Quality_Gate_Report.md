# 17 — BetterGit SonarQube Quality Gate Report

Adds an enforced SonarQube quality gate to BetterGit commit and merge workflows.

## Changes

- Configures the SonarQube server, project key, masked token, trigger, branch scope, and failure policy through `bettergit features`.
- Runs Maven or Gradle verification and SonarQube analysis before `bettergit commit` creates a commit.
- Adds `bettergit merge BRANCH`, which pauses before the merge commit, analyzes the merged tree, and aborts the merge when the gate rejects it.
- Shows failed quality-gate conditions and the SonarQube dashboard URL.
- Supports automatic cancellation or an explicit user-approval prompt for failed, unavailable, malformed, or incomplete analysis results.
- Keeps `SONAR_TOKEN` in the ignored, owner-only project `.env` file and ignores scanner metadata.
- Limits analysis to commits, merges, or both and to an optional exact list of target branches.

## Usage

Run `bettergit features`, enable the SonarQube quality gate, and enter:

- the SonarQube server URL;
- the SonarQube project key;
- the commit/merge trigger;
- `all` or a comma-separated branch list;
- approval or automatic-cancellation behavior;
- a SonarQube token with analysis and project-browse permissions.

Then use `bettergit commit` and `bettergit merge BRANCH` for gated operations. Direct `git` commands are not intercepted.
Gradle projects must apply the `org.sonarqube` plugin so the `sonar` task is available.

## Validation

Focused tests cover commit cancellation, merge approval and rollback, branch and event filtering, settings persistence, token masking, stale-result rejection, condition rendering, and disabled-feature behavior.
