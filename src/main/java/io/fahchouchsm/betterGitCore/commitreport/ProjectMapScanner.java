package io.fahchouchsm.betterGitCore.commitreport;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.fahchouchsm.betterGitCore.configuration.Utf8TextReader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class ProjectMapScanner {
    private static final int MAX_DEPTH = 10;
    private static final int MAX_JAVA_FILES = 2_000;
    private static final int MAX_HEADER_CHARS = 32_000;
    private static final Set<String> EXCLUDED_DIRECTORIES = Set.of(
            ".git", ".bettergit", "target", "build", "node_modules", "vendor");
    private static final Pattern PACKAGE = Pattern.compile("(?m)^\\s*package\\s+([\\w.]+)\\s*;");
    private static final Pattern TYPE = Pattern.compile(
            "(?m)^\\s*(?:public\\s+)?(?:(?:abstract|final|sealed|non-sealed)\\s+)*"
                    + "(class|interface|record|enum)\\s+(\\w+)");
    private static final Pattern MODULE = Pattern.compile("<module>\\s*([^<]+?)\\s*</module>");
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();

    public String scan(Path projectPath) throws IOException {
        List<Path> javaFiles = javaFiles(projectPath);
        ProjectMapAccumulator projectMap = new ProjectMapAccumulator();
        for (Path javaFile : javaFiles) {
            inspectJavaFile(projectPath, javaFile, projectMap);
        }
        return JSON.toJson(projectMap.snapshot(mavenModules(projectPath))) + System.lineSeparator();
    }

    private static List<Path> javaFiles(Path projectPath) throws IOException {
        try (Stream<Path> paths = Files.walk(projectPath, MAX_DEPTH)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .filter(path -> !isExcluded(projectPath.relativize(path)))
                    .sorted()
                    .limit(MAX_JAVA_FILES)
                    .toList();
        }
    }

    private static boolean isExcluded(Path relativePath) {
        for (Path segment : relativePath) {
            if (EXCLUDED_DIRECTORIES.contains(segment.toString())) {
                return true;
            }
        }
        return false;
    }

    private static void inspectJavaFile(
            Path projectPath, Path javaFile, ProjectMapAccumulator projectMap) throws IOException {
        String relativePath = projectPath.relativize(javaFile).toString().replace('\\', '/');
        String header = Utf8TextReader.readPrefix(javaFile, MAX_HEADER_CHARS);
        String sourceRoot = sourceRoot(relativePath);
        if (sourceRoot != null) {
            projectMap.addSourceRoot(sourceRoot);
        }
        Matcher packageMatcher = PACKAGE.matcher(header);
        String packageName = packageMatcher.find() ? packageMatcher.group(1) : "";
        if (!packageName.isBlank()) {
            projectMap.addPackage(packageName);
        }
        Matcher typeMatcher = TYPE.matcher(header);
        if (typeMatcher.find()) {
            projectMap.addType(new ProjectType(
                    relativePath, packageName, typeMatcher.group(1), typeMatcher.group(2)));
        }
    }

    private static String sourceRoot(String relativePath) {
        int mainSource = relativePath.indexOf("src/main/java/");
        if (mainSource >= 0) {
            return relativePath.substring(0, mainSource + "src/main/java".length());
        }
        int testSource = relativePath.indexOf("src/test/java/");
        return testSource < 0
                ? null
                : relativePath.substring(0, testSource + "src/test/java".length());
    }

    private static List<String> mavenModules(Path projectPath) throws IOException {
        Path pom = projectPath.resolve("pom.xml");
        if (!Files.isRegularFile(pom)) {
            return List.of();
        }
        List<String> modules = new ArrayList<>();
        Matcher matcher = MODULE.matcher(Utf8TextReader.readPrefix(pom, MAX_HEADER_CHARS));
        while (matcher.find()) {
            modules.add(matcher.group(1).trim());
        }
        return List.copyOf(modules);
    }

    private record ProjectMap(
            List<String> mavenModules,
            List<String> sourceRoots,
            List<String> packages,
            List<ProjectType> importantTypes,
            List<String> testLocations) {
    }

    private record ProjectType(String path, String packageName, String kind, String name) {
    }

    private static final class ProjectMapAccumulator {
        private final Set<String> sourceRoots = new TreeSet<>();
        private final Set<String> packages = new TreeSet<>();
        private final List<ProjectType> types = new ArrayList<>();

        private void addSourceRoot(String sourceRoot) {
            sourceRoots.add(sourceRoot);
        }

        private void addPackage(String packageName) {
            packages.add(packageName);
        }

        private void addType(ProjectType projectType) {
            types.add(projectType);
        }

        private ProjectMap snapshot(List<String> mavenModules) {
            return new ProjectMap(
                    mavenModules,
                    List.copyOf(sourceRoots),
                    List.copyOf(packages),
                    List.copyOf(types),
                    sourceRoots.stream().filter(root -> root.contains("src/test/java")).toList());
        }
    }
}
