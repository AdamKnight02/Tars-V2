package com.tarsv2.workforce.exploration;

import java.time.Duration;

public record ExplorationConfig(
        int maxTasksPerCycle,
        double maxCycleBudgetUsd,
        double maxDailyBudgetUsd,
        Duration cooldown
) {
    public static ExplorationConfig defaults() {
        return new ExplorationConfig(3, 0.10d, 0.50d, Duration.ofMinutes(60));
    }
}
