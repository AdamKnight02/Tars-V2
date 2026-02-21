package com.tarsv2.workforce.integration;

import com.tarsv2.workforce.agent.AgentRole;
import com.tarsv2.workforce.economics.CostEstimator;
import com.tarsv2.workforce.economics.ProfitabilityGate;
import com.tarsv2.workforce.task.Task;
import com.tarsv2.workforce.task.TaskPriority;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EconomicGateIT {

    @Test
    void profitablePassesUnprofitableRejected() {
        ProfitabilityGate gate = new ProfitabilityGate();
        Task good = Task.create("g", "desc", AgentRole.ENGINEER, TaskPriority.NORMAL, "goal", 1, 100);
        Task bad = Task.create("b", "desc", AgentRole.ENGINEER, TaskPriority.NORMAL, "goal", 50, 1);
        assertTrue(gate.isViable(good));
        assertFalse(gate.isViable(bad));

        CostEstimator.Estimate expensive = new CostEstimator.Estimate("GLM-5", 1, 1, 10.0);
        assertFalse(gate.isViable(good, expensive));
    }
}
