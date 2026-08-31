package io.fahchouchsm.betterGitCore.history;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class HistoryDateParser {
    private static final Pattern RELATIVE = Pattern.compile("^(\\d+)([mhdw])$");
    private final Clock clock;

    public HistoryDateParser(Clock clock) {
        this.clock = clock;
    }

    public Instant parse(String expression) {
        if (expression == null || expression.isBlank()) {
            return null;
        }
        String normalized = expression.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "now" -> clock.instant();
            case "today" -> startOfDay(LocalDate.now(clock));
            case "yesterday" -> startOfDay(LocalDate.now(clock).minusDays(1));
            default -> parseDateOrRelative(normalized);
        };
    }

    private Instant parseDateOrRelative(String expression) {
        Matcher relative = RELATIVE.matcher(expression);
        if (relative.matches()) {
            return clock.instant().minus(duration(relative));
        }
        try {
            return Instant.parse(expression);
        } catch (DateTimeParseException ignored) {
            try {
                return startOfDay(LocalDate.parse(expression));
            } catch (DateTimeParseException exception) {
                throw new IllegalArgumentException(
                        "Invalid date '" + expression + "'. Use ISO-8601, YYYY-MM-DD, today, yesterday, or 7d.",
                        exception);
            }
        }
    }

    private static Duration duration(Matcher relative) {
        long amount = Long.parseLong(relative.group(1));
        return switch (relative.group(2)) {
            case "m" -> Duration.ofMinutes(amount);
            case "h" -> Duration.ofHours(amount);
            case "d" -> Duration.ofDays(amount);
            case "w" -> Duration.ofDays(Math.multiplyExact(amount, 7));
            default -> throw new IllegalStateException("Unsupported relative date unit.");
        };
    }

    private Instant startOfDay(LocalDate date) {
        ZoneId zone = clock.getZone();
        return date.atStartOfDay(zone).toInstant();
    }
}
