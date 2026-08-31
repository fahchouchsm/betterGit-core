package io.fahchouchsm.betterGitCore.api;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class GitIndex {
    void stage(Path repository, List<String> paths) throws IOException, InterruptedException {
        run(repository, command("add", "--all", paths));
    }

    void unstage(Path repository, List<String> paths) throws IOException, InterruptedException {
        List<String> arguments = hasHead(repository)
                ? resetCommand(paths)
                : command("rm", "--cached", paths);
        run(repository, arguments);
    }

    private static boolean hasHead(Path repository) throws IOException, InterruptedException {
        return exitCode(repository, List.of("git", "rev-parse", "--verify", "HEAD")) == 0;
    }

    private static List<String> resetCommand(List<String> paths) {
        validate(paths);
        List<String> arguments = new ArrayList<>(paths.size() + 5);
        arguments.add("git");
        arguments.add("reset");
        arguments.add("-q");
        arguments.add("HEAD");
        arguments.add("--");
        arguments.addAll(paths);
        return List.copyOf(arguments);
    }

    private static List<String> command(String action, String option, List<String> paths) {
        validate(paths);
        List<String> arguments = new ArrayList<>(paths.size() + 4);
        arguments.add("git");
        arguments.add(action);
        arguments.add(option);
        arguments.add("--");
        arguments.addAll(paths);
        return List.copyOf(arguments);
    }

    private static void validate(List<String> paths) {
        if (paths == null || paths.isEmpty()) {
            throw new IllegalArgumentException("At least one repository path is required.");
        }
        for (String path : paths) {
            validate(path);
        }
    }

    private static void validate(String repositoryPath) {
        if (repositoryPath == null || repositoryPath.isBlank()) {
            throw new IllegalArgumentException("Repository paths must not be blank.");
        }
        Path path = Path.of(repositoryPath);
        if (path.isAbsolute() || path.normalize().startsWith("..")) {
            throw new IllegalArgumentException("Repository path escapes the project: " + repositoryPath);
        }
    }

    private static void run(Path repository, List<String> command)
            throws IOException, InterruptedException {
        Process process = process(repository, command);
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
        int exitCode = await(process);
        if (exitCode != 0) {
            throw new IOException(output.isEmpty() ? "Git index operation failed." : output);
        }
    }

    private static int exitCode(Path repository, List<String> command)
            throws IOException, InterruptedException {
        Process process = process(repository, command);
        process.getInputStream().readAllBytes();
        return await(process);
    }

    private static Process process(Path repository, List<String> command) throws IOException {
        return new ProcessBuilder(command)
                .directory(repository.toFile())
                .redirectErrorStream(true)
                .start();
    }

    private static int await(Process process) throws InterruptedException {
        try {
            return process.waitFor();
        } catch (InterruptedException exception) {
            process.destroyForcibly();
            throw exception;
        }
    }
}
