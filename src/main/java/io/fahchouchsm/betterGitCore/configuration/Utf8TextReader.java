package io.fahchouchsm.betterGitCore.configuration;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class Utf8TextReader {
    private Utf8TextReader() {
    }

    public static String readPrefix(Path file, int maximumCharacters) throws IOException {
        if (maximumCharacters < 1) {
            throw new IllegalArgumentException("Maximum characters must be positive.");
        }
        StringBuilder prefix = new StringBuilder(maximumCharacters);
        char[] buffer = new char[Math.min(4_096, maximumCharacters)];
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            int charactersRead;
            while (prefix.length() < maximumCharacters
                    && (charactersRead = read(reader, buffer, maximumCharacters - prefix.length())) > 0) {
                prefix.append(buffer, 0, charactersRead);
            }
        }
        return prefix.toString();
    }

    private static int read(Reader reader, char[] buffer, int remainingCharacters) throws IOException {
        return reader.read(buffer, 0, Math.min(buffer.length, remainingCharacters));
    }
}
