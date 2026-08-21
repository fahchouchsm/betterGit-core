package io.fahchouchsm.betterGitCore.ai;

import java.net.URI;
import java.time.Duration;

record AiApiRequest(URI endpoint, String apiKey, String model, String prompt, Duration timeout) {
}
