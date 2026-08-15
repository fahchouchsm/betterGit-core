# 08 — BetterGit Report Numbering Report

## Goal

Make the task reports sort in chronological order and clearly show which report came first.

## Changes

- Added a zero-padded numeric prefix to every report filename and title.
- Ordered the existing reports according to the task chronology.
- Updated the cross-reference in the init path report to use the new numbered filename.
- Established the `NN_Report_Name.md` convention for future reports.

## Report order

1. Current project progress
2. Executable JAR invocation
3. Interactive init menu
4. Init path argument
5. Init target and fullscreen menu
6. AI commit report generator
7. Modern commit, log, and AI setup workflow
8. Report numbering

## Verification

- Confirmed every Markdown file in `repports/` has a unique numeric prefix.
- Confirmed filenames sort in task order.
- Confirmed no repository reference uses a removed report filename.
- Maven tests were not run because this task changes documentation filenames only.
