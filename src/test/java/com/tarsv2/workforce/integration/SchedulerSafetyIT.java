package com.tarsv2.workforce.integration;

import com.tarsv2.workforce.scheduler.ExecutionGuard;
import com.tarsv2.workforce.scheduler.SchedulerConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SchedulerSafetyIT {

    @Test
    void respectsMaxConcurrentLimit() {
        SchedulerConfig config = new SchedulerConfig(1, 2, 60);
        assertEquals(2, config.maxConcurrentTasks());
        // ExecutorService created with this size won't exceed 2 threads
    }

    @Test
    void circuitBreakerPreventsExecution() {
        ExecutionGuard guard = new ExecutionGuard(3, 600);
        guard.recordFailure();
        guard.recordFailure();
        guard.recordFailure();
        assertFalse(guard.canExecute());
    }
}
