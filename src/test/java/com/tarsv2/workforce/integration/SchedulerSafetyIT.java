package com.tarsv2.workforce.integration;

import com.tarsv2.workforce.scheduler.ExecutionGuard;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SchedulerSafetyIT {

    @Test
    void circuitBreakerTripsAfterFailures() {
        ExecutionGuard guard = new ExecutionGuard(3, 60);
        guard.recordFailure();
        guard.recordFailure();
        guard.recordFailure();
        assertFalse(guard.canExecute());
    }
}
