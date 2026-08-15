# BetterGit Init Path Argument Report

**Report date:** 2026-08-15

## Reported command

```bash
java -jar target/betterGit-core-1.0.jar init ignore/
```

## Diagnosis

The build succeeded and the executable JAR is valid. The command fails afterward because `init` does not currently declare a positional directory argument. Picocli therefore treats `ignore/` as an unmatched argument and returns its usage-error exit code.

The current command contract is:

```text
Usage: bettergit init [-ChvVy]
```

BetterGit always initializes the process's current working directory.

## Working command

Enter the target directory before launching the JAR:

```bash
cd ignore
java -jar ../target/betterGit-core-1.0.jar init
```

The equivalent one-line command is:

```bash
(cd ignore && java -jar ../target/betterGit-core-1.0.jar init)
```

## Resolution

The optional positional directory was implemented after this diagnosis. The rebuilt JAR now supports both `init` for the current directory and `init <directory>` for a specified existing directory. See `BetterGit_Init_Target_And_Fullscreen_Menu_Report.md` for the implementation and verification results.
