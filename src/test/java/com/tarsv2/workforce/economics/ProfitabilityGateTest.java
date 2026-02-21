package com.tarsv2.workforce.economics;

import com.tarsv2.workforce.agent.AgentRole;
import com.tarsv2.workforce.task.Task;
import com.tarsv2.workforce.task.TaskPriority;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProfitabilityGateTest {

    @Test
    void rejectsUnprofitableTask() {
        ProfitabilityGate gate = new ProfitabilityGate();
        Task task = Task.create("t", "d", AgentRole.FINANCE, TaskPriority.NORMAL, "goal", 10.0, 5.0);
        assertFalse(gate.isViable(task));
    }

    @Test
    void allowsOverride() {
        ProfitabilityGate gate = new ProfitabilityGate();
        Task task = Task.create("t", "d", AgentRole.FINANCE, TaskPriority.NORMAL, "goal", 10.0, 5.0);
        assertTrue(gate.isViableWithOverride(task, true));
    }
}
