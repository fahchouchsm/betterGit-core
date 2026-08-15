package io.fahchouchsm.betterGitCore.commitreport;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SensitiveContentFilter {
    private static final String REDACTED = "[REDACTED]";
    private static final Set<String> EXCLUDED_DIRECTORIES = Set.of(
            ".git", "target", "build", "node_modules", "vendor", "dist", "generated");
    private static final Set<String> EXCLUDED_SUFFIXES = Set.of(
            ".env", ".pem", ".key", ".p12", ".pfx", ".jks", ".keystore", ".der", ".crt");
    private static final Set<String> EXCLUDED_FILE_NAMES = Set.of(
            ".npmrc", ".pypirc", "credentials.json", "secrets.json", "id_rsa", "id_dsa", "id_ecdsa", "id_ed25519");
    private static final Set<String> BINARY_SUFFIXES = Set.of(
            ".png", ".jpg", ".jpeg", ".gif", ".webp", ".pdf", ".zip", ".jar", ".class",
            ".so", ".dll", ".exe", ".woff", ".woff2", ".ttf", ".mp3", ".mp4");
    private static final Pattern DIFF_PATH = Pattern.compile("^diff --git a/(.+?) b/(.+)$");
    private static final Pattern SECRET_ASSIGNMENT = Pattern.compile(
            "(?im)(api[_-]?key|token|secret|password|authorization)(\\s*[:=]\\s*)"
                    + "(?:\"[^\"]*\"|'[^']*'|[^\\s,;]+)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern BEARER = Pattern.compile("(?i)Bearer\\s+[A-Za-z0-9._~+/-]+=*");
    private static final Pattern PRIVATE_KEY = Pattern.compile(
            "(?s)-----BEGIN [^-]*PRIVATE KEY-----.*?-----END [^-]*PRIVATE KEY-----");

    public List<ChangedFile> safeFiles(List<ChangedFile> files) {
        return files.stream().filter(this::isSafe).toList();
    }

    public String safeDiff(String diff, String configuredSecret) {
        List<String> safeSections = new ArrayList<>();
        for (String section : diff.split("(?m)(?=^diff --git )")) {
            if (!section.isBlank() && !excludedDiffSection(section)) {
                safeSections.add(redact(section, configuredSecret));
            }
        }
        return String.join("", safeSections);
    }

    public String redact(String text, String configuredSecret) {
        String redacted = PRIVATE_KEY.matcher(text).replaceAll(REDACTED);
        redacted = BEARER.matcher(redacted).replaceAll("Bearer " + REDACTED);
        redacted = replaceSecretAssignments(redacted);
        if (configuredSecret != null && !configuredSecret.isBlank()) {
            redacted = redacted.replace(configuredSecret, REDACTED);
        }
        return redacted;
    }

    public boolean isExcluded(String path) {
        String normalizedPath = path.replace('\\', '/').toLowerCase(Locale.ROOT);
        for (String segment : normalizedPath.split("/")) {
            if (EXCLUDED_DIRECTORIES.contains(segment)) {
                return true;
            }
        }
        String fileName = normalizedPath.substring(normalizedPath.lastIndexOf('/') + 1);
        return EXCLUDED_FILE_NAMES.contains(fileName)
                || EXCLUDED_SUFFIXES.stream().anyMatch(normalizedPath::endsWith)
                || BINARY_SUFFIXES.stream().anyMatch(normalizedPath::endsWith)
                || normalizedPath.contains("/.env.")
                || normalizedPath.startsWith(".env.");
    }

    private boolean isSafe(ChangedFile file) {
        return !isExcluded(file.path())
                && (file.previousPath() == null || !isExcluded(file.previousPath()));
    }

    private boolean excludedDiffSection(String section) {
        String firstLine = section.lines().findFirst().orElse("");
        Matcher matcher = DIFF_PATH.matcher(firstLine);
        return matcher.matches() && (isExcluded(matcher.group(1)) || isExcluded(matcher.group(2)));
    }

    private static String replaceSecretAssignments(String text) {
        Matcher matcher = SECRET_ASSIGNMENT.matcher(text);
        StringBuilder redacted = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(redacted,
                    Matcher.quoteReplacement(matcher.group(1) + matcher.group(2) + REDACTED));
        }
        matcher.appendTail(redacted);
        return redacted.toString();
    }
}
