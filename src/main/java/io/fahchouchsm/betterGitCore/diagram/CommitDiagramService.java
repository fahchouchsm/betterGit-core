package io.fahchouchsm.betterGitCore.diagram;

import io.fahchouchsm.betterGitCore.configuration.BetterGitConfiguration;
import io.fahchouchsm.betterGitCore.configuration.BetterGitConfigurationLoader;
import io.fahchouchsm.betterGitCore.configuration.BetterGitDirectories;
import io.fahchouchsm.betterGitCore.configuration.BetterGitFileStore;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

public final class CommitDiagramService {
    private final ClassDiagramGenerator generator;
    private final BetterGitConfigurationLoader configurationLoader;
    private final BetterGitFileStore fileStore;

    public CommitDiagramService(
            ClassDiagramGenerator generator,
            BetterGitConfigurationLoader configurationLoader,
            BetterGitFileStore fileStore) {
        this.generator = generator;
        this.configurationLoader = configurationLoader;
        this.fileStore = fileStore;
    }

    public Optional<CommitDiagramPlan> planForCommit(Path projectPath, String commitHash) throws IOException {
        Optional<BetterGitConfiguration> configuration = configurationLoader.load(projectPath);
        if (configuration.isEmpty()
                || !configuration.orElseThrow().settings().classDiagramOnCommit()) {
            return Optional.empty();
        }
        Path javaSources = projectPath.resolve("src/main/java");
        if (!Files.isDirectory(javaSources)) {
            throw new IOException("Java source directory does not exist: " + javaSources);
        }
        Path outputFile = projectPath.resolve(".bettergit/diagrams/" + commitHash + ".svg");
        return Optional.of(new CommitDiagramPlan(projectPath, javaSources, outputFile));
    }

    public void generate(CommitDiagramPlan plan) throws IOException, InterruptedException {
        fileStore.ensureDiagramsIgnored(plan.projectPath());
        BetterGitDirectories.child(plan.projectPath(), "diagrams");
        Path pendingDiagram = plan.outputFile().resolveSibling(
                "pending-" + plan.outputFile().getFileName());
        refuseSymbolicLink(pendingDiagram);
        refuseSymbolicLink(plan.outputFile());
        Files.deleteIfExists(pendingDiagram);
        generator.generateSvg(plan.javaSources(), pendingDiagram);
        requireGeneratedSvg(pendingDiagram);
        moveIntoPlace(pendingDiagram, plan.outputFile());
    }

    private static void requireGeneratedSvg(Path diagram) throws IOException {
        if (!Files.isRegularFile(diagram, LinkOption.NOFOLLOW_LINKS) || Files.size(diagram) == 0) {
            throw new IOException("Java2Diagram did not create a non-empty SVG file.");
        }
    }

    private static void refuseSymbolicLink(Path diagram) throws IOException {
        if (Files.isSymbolicLink(diagram)) {
            throw new IOException("Refusing to write through symbolic diagram file: " + diagram);
        }
    }

    private static void moveIntoPlace(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination,
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
