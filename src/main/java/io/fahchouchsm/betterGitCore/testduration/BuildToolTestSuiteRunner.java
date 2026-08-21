package io.fahchouchsm.betterGitCore.testduration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

public final class BuildToolTestSuiteRunner implements TestSuiteRunner {
    @Override
    public TestRun run(Path committedProject) throws IOException, InterruptedException {
        TestCommand testCommand = testCommand(committedProject);
        long startedAt = System.nanoTime();
        Process testProcess;
        try {
            testProcess = new ProcessBuilder(testCommand.arguments())
                    .directory(committedProject.toFile())
                    .inheritIO()
                    .start();
        } catch (IOException exception) {
            throw new IOException("Could not start '" + testCommand.displayName()
                    + "'. Ensure the project build tool is installed.", exception);
        }
        return await(testProcess, testCommand, startedAt);
    }

    private static TestRun await(Process testProcess, TestCommand testCommand, long startedAt)
            throws InterruptedException {
        try {
            int exitCode = testProcess.waitFor();
            return new TestRun(
                    testCommand.displayName(), Duration.ofNanos(System.nanoTime() - startedAt), exitCode);
        } catch (InterruptedException exception) {
            testProcess.destroyForcibly();
            throw exception;
        }
    }

    private static TestCommand testCommand(Path projectPath) throws IOException {
        if (Files.isRegularFile(projectPath.resolve("pom.xml"))) {
            return mavenCommand(projectPath);
        }
        if (Files.isRegularFile(projectPath.resolve("build.gradle"))
                || Files.isRegularFile(projectPath.resolve("build.gradle.kts"))) {
            return gradleCommand(projectPath);
        }
        throw new IOException("No Maven or Gradle build file exists in the committed project.");
    }

    private static TestCommand mavenCommand(Path projectPath) {
        Path wrapper = projectPath.resolve("mvnw");
        return Files.isRegularFile(wrapper)
                ? new TestCommand(List.of(wrapper.toString(), "test"), "./mvnw test")
                : new TestCommand(List.of("mvn", "test"), "mvn test");
    }

    private static TestCommand gradleCommand(Path projectPath) {
        Path wrapper = projectPath.resolve("gradlew");
        return Files.isRegularFile(wrapper)
                ? new TestCommand(List.of(wrapper.toString(), "test"), "./gradlew test")
                : new TestCommand(List.of("gradle", "test"), "gradle test");
    }

    private record TestCommand(List<String> arguments, String displayName) {
    }
}
