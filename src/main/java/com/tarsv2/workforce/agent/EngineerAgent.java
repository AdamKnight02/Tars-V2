package com.tarsv2.workforce.agent;

import com.tarsv2.openclaw.Intent;
import com.tarsv2.openclaw.IntentContext;
import com.tarsv2.openclaw.IntentPayload;
import com.tarsv2.openclaw.IntentType;
import com.tarsv2.workforce.economics.CostEstimator;
import com.tarsv2.workforce.task.Task;

import java.util.List;
import java.util.Objects;

public final class EngineerAgent implements WorkforceAgent {

    private final CostEstimator costEstimator;

    public EngineerAgent(CostEstimator costEstimator) {
        this.costEstimator = Objects.requireNonNull(costEstimator);
    }

    @Override public String getName() { return "EngineerAgent"; }
    @Override public AgentRole getRole() { return AgentRole.ENGINEER; }
    @Override public String getDescription() { return "Produces code-analysis intents for OpenClaw execution"; }
    @Override public CostEstimator.Estimate estimateCost(Task task) { return costEstimator.estimate(getRole(), task); }

    @Override
    public List<Intent> plan(Task task) {
        CostEstimator.Estimate estimate = estimateCost(task);
        Intent analyze = new Intent(
                IntentType.ANALYZE_CODE,
                IntentPayload.builder()
                        .put("prompt", "Analyze code task and propose safe deterministic changes: " + task.description())
                        .put("taskId", task.id().toString())
                        .build(),
                new IntentContext("coding-sandbox", "Tars-V2", estimate.estimatedCostUsd(), 0.70, java.util.Set.of())
        );
        return List.of(analyze);
    }

    @Override
    public boolean canHandle(Task task) {
        String lower = task.description().toLowerCase();
        return lower.contains("code") || lower.contains("refactor") || lower.contains("java");
    }
}
