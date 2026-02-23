package com.tarsv2.openclaw;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Structured result returned by OpenClaw after executing an Intent.
 *
 * <p>All tool outputs are normalized into this structured form —
 * raw output is never passed directly to the LLM.</p>
 */
public record IntentResult(
        String intentId,
        boolean success,
        Map<String, String> data,
        String summary,
        String errorMessage,
        long latencyMs,
        Instant completedAt
) {
    public IntentResult {
        Objects.requireNonNull(intentId);
        if (data == null) data = Map.of();
        completedAt = completedAt != null ? completedAt : Instant.now();
    }

    public static IntentResult success(String intentId, Map<String, String> data,
                                        String summary, long latencyMs) {
        return new IntentResult(intentId, true, data, summary, null, latencyMs, Instant.now());
    }

    public static IntentResult failure(String intentId, String error, long latencyMs) {
        return new IntentResult(intentId, false, Map.of(), null, error, latencyMs, Instant.now());
    }

    public String message() {
        return summary();
    }

    public String output() {
        Object content = data() != null ? data().get("content") : null;
        return content != null ? content.toString() : summary();
    }
}
