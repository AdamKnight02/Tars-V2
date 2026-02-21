package com.tarsv2.workforce.scheduler;

import java.time.Instant;

public final class ExecutionGuard {

    private final int maxConsecutiveFailures;
    private final int cooldownSeconds;

    private int consecutiveFailures;
    private Instant openedAt;

    public ExecutionGuard() {
        this(5, 300);
    }

    public ExecutionGuard(int maxConsecutiveFailures, int cooldownSeconds) {
        this.maxConsecutiveFailures = maxConsecutiveFailures;
        this.cooldownSeconds = cooldownSeconds;
    }

    public synchronized boolean canExecute() {
        if (consecutiveFailures < maxConsecutiveFailures) {
            return true;
        }
        if (openedAt == null) {
            openedAt = Instant.now();
            return false;
        }
        if (Instant.now().isAfter(openedAt.plusSeconds(cooldownSeconds))) {
            reset();
            return true;
        }
        return false;
    }

    public synchronized void recordSuccess() {
        reset();
    }

    public synchronized void recordFailure() {
        consecutiveFailures++;
        if (consecutiveFailures >= maxConsecutiveFailures && openedAt == null) {
            openedAt = Instant.now();
        }
    }

    public synchronized void reset() {
        consecutiveFailures = 0;
        openedAt = null;
    }
}
