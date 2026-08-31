package io.fahchouchsm.betterGitCore.sonarqube;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class BuildToolSonarScanner implements SonarScanner {
    private static final String MAVEN_GOAL =
            "org.sonarsource.scanner.maven:sonar-maven-plugin:5.5.0.6356:sonar";

    @Override
    public int run(SonarAnalysisRequest request) throws IOException, InterruptedException {
        List<String> command = command(request);
        ProcessBuilder processBuilder = new ProcessBuilder(command)
                .directory(request.projectPath().toFile())
                .inheritIO();
        processBuilder.environment().put("SONAR_TOKEN", request.configuration().token());
        processBuilder.environment().put("SONAR_HOST_URL", request.configuration().serverUrl());
        try {
            return await(processBuilder.start());
        } catch (IOException exception) {
            throw new IOException("Could not start SonarQube analysis. Ensure the project build tool is installed.",
                    exception);
        }
    }

    private static int await(Process analysisProcess) throws InterruptedException {
        try {
            return analysisProcess.waitFor();
        } catch (InterruptedException exception) {
            analysisProcess.destroyForcibly();
            throw exception;
        }
    }

    private static List<String> command(SonarAnalysisRequest request) throws IOException {
        List<String> arguments = buildToolArguments(request.projectPath());
        arguments.add("-Dsonar.projectKey=" + request.configuration().projectKey());
        arguments.add("-Dsonar.qualitygate.wait=true");
        arguments.add("-Dsonar.qualitygate.timeout=300");
        arguments.add("-Dsonar.scanner.metadataFilePath=" + request.metadataFile());
        return List.copyOf(arguments);
    }

    private static List<String> buildToolArguments(Path projectPath) throws IOException {
        if (Files.isRegularFile(projectPath.resolve("pom.xml"))) {
            Path wrapper = projectPath.resolve("mvnw");
            return new ArrayList<>(List.of(
                    Files.isRegularFile(wrapper) ? wrapper.toString() : "mvn", "verify", MAVEN_GOAL));
        }
        if (Files.isRegularFile(projectPath.resolve("build.gradle"))
                || Files.isRegularFile(projectPath.resolve("build.gradle.kts"))) {
            Path wrapper = projectPath.resolve("gradlew");
            return new ArrayList<>(List.of(
                    Files.isRegularFile(wrapper) ? wrapper.toString() : "gradle", "sonar"));
        }
        throw new IOException("SonarQube analysis requires a Maven or Gradle build file.");
    }
}
