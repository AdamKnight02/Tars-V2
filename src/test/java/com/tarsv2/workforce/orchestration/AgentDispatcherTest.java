package com.tarsv2.workforce.orchestration;

import com.tarsv2.openclaw.Intent;
import com.tarsv2.openclaw.IntentContext;
import com.tarsv2.openclaw.IntentPayload;
import com.tarsv2.openclaw.IntentType;
import com.tarsv2.workforce.agent.AgentRole;
import com.tarsv2.workforce.agent.WorkforceAgent;
import com.tarsv2.workforce.economics.CostEstimator;
import com.tarsv2.workforce.task.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AgentDispatcherTest {

    @Test
    void dispatchesByAssignedRole() {
        AgentDispatcher dispatcher = new AgentDispatcher();
        dispatcher.register(new StubAgent());

        Task task = Task.create("t", "research analysis", AgentRole.RESEARCHER, TaskPriority.NORMAL, "g", 1, 2)
                .withStatus(TaskStatus.QUEUED);

        List<Intent> result = dispatcher.plan(task);
        assertEquals(1, result.size());
        assertEquals(IntentType.REASON, result.get(0).getType());
    }

    private static final class StubAgent implements WorkforceAgent {
        @Override public String getName() { return "stub"; }
        @Override public AgentRole getRole() { return AgentRole.RESEARCHER; }
        @Override public String getDescription() { return "stub"; }
        @Override public CostEstimator.Estimate estimateCost(Task task) { return new CostEstimator.Estimate("GLM-5",1,1,0.1); }
        @Override public List<Intent> plan(Task task) { return List.of(new Intent(IntentType.REASON, IntentPayload.builder().put("prompt", "ok").build(), new IntentContext("coding-sandbox", "repo", 0.01, 0.9, java.util.Set.of()))); }
        @Override public boolean canHandle(Task task) { return true; }
    }
}
