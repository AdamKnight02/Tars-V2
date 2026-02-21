package com.tarsv2.workforce.revenue;

import java.time.Instant;
import java.util.UUID;

public record Opportunity(
        UUID id,
        String title,
        String description,
        String source,
        OpportunityStatus status,
        double estimatedValue,
        double estimatedCost,
        Instant createdAt,
        Instant updatedAt
) {
}
