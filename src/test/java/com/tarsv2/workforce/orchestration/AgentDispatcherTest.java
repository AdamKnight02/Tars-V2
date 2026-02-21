package com.tarsv2.workforce.orchestration;

import com.tarsv2.workforce.agent.AgentRole;
import com.tarsv2.workforce.agent.WorkforceAgent;
import com.tarsv2.workforce.economics.CostEstimator;
import com.tarsv2.workforce.task.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class AgentDispatcherTest {

    @Test
    void dispatchesByAssignedRole() {
        AgentDispatcher dispatcher = new AgentDispatcher();
        dispatcher.register(new StubAgent());

        Task task = Task.create("t", "research analysis", AgentRole.RESEARCHER, TaskPriority.NORMAL, "g", 1, 2)
                .withStatus(TaskStatus.QUEUED);

        TaskResult result = dispatcher.dispatch(task);
        assertTrue(result.success());
        assertEquals("ok", result.output());
    }

    private static final class StubAgent implements WorkforceAgent {
        @Override public String getName() { return "stub"; }
        @Override public AgentRole getRole() { return AgentRole.RESEARCHER; }
        @Override public String getDescription() { return "stub"; }
        @Override public CostEstimator.Estimate estimateCost(Task task) { return new CostEstimator.Estimate("GLM-5",1,1,0.1); }
        @Override public TaskResult execute(Task task) { return new TaskResult(task.id(), true, "ok", null, Instant.now(), 0, 0, "GLM-5"); }
        @Override public boolean canHandle(Task task) { return true; }
    }
}
