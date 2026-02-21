package com.tarsv2.workforce.economics;

import java.time.Instant;
import java.util.UUID;

public record RevenueLedger(
        UUID id,
        UUID taskId,
        String revenueSource,
        String description,
        double amountUsd,
        boolean confirmed,
        Instant recordedAt,
        Instant confirmedAt
) {
}
