package com.tarsv2.workforce.scheduler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExecutionGuardTest {

    @Test
    void tripsAndResetsAfterCooldown() throws Exception {
        ExecutionGuard guard = new ExecutionGuard(2, 1);
        guard.recordFailure();
        guard.recordFailure();
        assertFalse(guard.canExecute());
        Thread.sleep(1100);
        assertTrue(guard.canExecute());
    }
}
