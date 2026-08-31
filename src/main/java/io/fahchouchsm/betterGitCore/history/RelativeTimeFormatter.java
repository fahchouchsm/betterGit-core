package io.fahchouchsm.betterGitCore.history;

import java.time.Duration;
import java.time.Instant;

public final class RelativeTimeFormatter {
    public String format(Instant timestamp, Instant now) {
        long seconds = Duration.between(timestamp, now).getSeconds();
        if (seconds < 0) {
            return "in " + quantity(-seconds);
        }
        if (seconds < 5) {
            return "just now";
        }
        return quantity(seconds) + " ago";
    }

    private static String quantity(long seconds) {
        if (seconds < 60) {
            return plural(seconds, "second");
        }
        if (seconds < 3_600) {
            return plural(seconds / 60, "minute");
        }
        if (seconds < 86_400) {
            return plural(seconds / 3_600, "hour");
        }
        if (seconds < 2_592_000) {
            return plural(seconds / 86_400, "day");
        }
        if (seconds < 31_536_000) {
            return plural(seconds / 2_592_000, "month");
        }
        return plural(seconds / 31_536_000, "year");
    }

    private static String plural(long count, String unit) {
        return count + " " + unit + (count == 1 ? "" : "s");
    }
}
