# 02 — BetterGit JAR Invocation Report

**Report date:** 2026-08-15

## Reported issue

Running either of these commands from the `ignore` directory fails:

```bash
jar ../target/betterGit-core-1.0.jar init
jar ../target/betterGit-core-1.0.jar init .
```

The JDK `jar` utility interprets its arguments as archive-management options. It does not run the application's `Main-Class`, so it reports `Illegal option: .`.

## Correct command

Launch an executable JAR with `java -jar`:

```bash
java -jar ../target/betterGit-core-1.0.jar init
```

To accept the safe defaults without interactive questions:

```bash
java -jar ../target/betterGit-core-1.0.jar init --yes
```

Do not append `.`. The current implementation always initializes the current working directory and does not accept a positional path argument.

## Verification

The packaged manifest was checked and contains:

```text
Main-Class: io.fahchouchsm.betterGitCore.commands.CommandRunner
```

Both of these commands were also run successfully from the `ignore` directory without initializing it:

```bash
java -jar ../target/betterGit-core-1.0.jar --help
java -jar ../target/betterGit-core-1.0.jar init --help
```

The help output confirms that `init` targets the current project and supports `-y`/`--yes`.
