package io.fahchouchsm.betterGitCore.configuration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

public final class AtomicFileWriter {
    private AtomicFileWriter() {
    }

    public static void write(Path destination, String content) throws IOException {
        Path temporaryFile = Files.createTempFile(
                destination.getParent(), destination.getFileName().toString(), ".tmp");
        writeAndReplace(temporaryFile, destination, content);
    }

    public static void writeOwnerOnly(Path destination, String content) throws IOException {
        Path temporaryFile = Files.createTempFile(
                destination.getParent(), destination.getFileName().toString(), ".tmp");
        try {
            restrictToOwner(temporaryFile);
        } catch (IOException exception) {
            Files.deleteIfExists(temporaryFile);
            throw exception;
        }
        writeAndReplace(temporaryFile, destination, content);
    }

    private static void writeAndReplace(Path temporaryFile, Path destination, String content) throws IOException {
        try {
            Files.writeString(temporaryFile, content, StandardCharsets.UTF_8);
            replace(temporaryFile, destination);
        } finally {
            Files.deleteIfExists(temporaryFile);
        }
    }

    private static void restrictToOwner(Path file) throws IOException {
        try {
            Files.setPosixFilePermissions(file, Set.of(
                    PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        } catch (UnsupportedOperationException exception) {
            // Non-POSIX platforms rely on their native temporary-file permissions.
        }
    }

    private static void replace(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
