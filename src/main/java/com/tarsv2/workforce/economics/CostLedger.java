package com.tarsv2.workforce.economics;

import java.time.Instant;
import java.util.UUID;

public record CostLedger(
        UUID id,
        UUID taskId,
        String modelId,
        String provider,
        int inputTokens,
        int outputTokens,
        int apiCalls,
        double costUsd,
        Instant recordedAt
) {
}
