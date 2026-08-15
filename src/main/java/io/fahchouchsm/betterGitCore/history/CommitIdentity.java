package io.fahchouchsm.betterGitCore.history;

import java.time.Instant;

public record CommitIdentity(String name, String email, Instant time, String zoneOffset) {
}
