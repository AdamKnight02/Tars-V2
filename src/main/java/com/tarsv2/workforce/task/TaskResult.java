package com.tarsv2.workforce.task;

import java.time.Instant;
import java.util.UUID;

public record TaskResult(
        UUID taskId,
        boolean success,
        String output,
        String errorMessage,
        Instant completedAt,
        int inputTokensUsed,
        int outputTokensUsed,
        String modelUsed
) {
}
