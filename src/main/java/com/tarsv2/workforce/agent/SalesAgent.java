package com.tarsv2.workforce.agent;

import com.tarsv2.openclaw.Intent;
import com.tarsv2.openclaw.IntentContext;
import com.tarsv2.openclaw.IntentPayload;
import com.tarsv2.openclaw.IntentType;
import com.tarsv2.workforce.economics.CostEstimator;
import com.tarsv2.workforce.task.Task;

import java.util.List;
import java.util.Objects;

public final class SalesAgent implements WorkforceAgent {

    private final CostEstimator costEstimator;

    public SalesAgent(CostEstimator costEstimator) {
        this.costEstimator = Objects.requireNonNull(costEstimator);
    }

    @Override public String getName() { return "SalesAgent"; }
    @Override public AgentRole getRole() { return AgentRole.SALES; }
    @Override public String getDescription() { return "Produces sales-evaluation intents for OpenClaw execution"; }
    @Override public CostEstimator.Estimate estimateCost(Task task) { return costEstimator.estimate(getRole(), task); }

    @Override
    public List<Intent> plan(Task task) {
        CostEstimator.Estimate estimate = estimateCost(task);
        Intent evaluate = new Intent(
                IntentType.EVALUATE,
                IntentPayload.builder()
                        .put("prompt", "Evaluate sales opportunity and provide strategy: " + task.description())
                        .put("taskId", task.id().toString())
                        .build(),
                new IntentContext("research-sandbox", "Tars-V2", estimate.estimatedCostUsd(), 0.68, java.util.Set.of())
        );
        return List.of(evaluate);
    }

    @Override
    public boolean canHandle(Task task) {
        String lower = task.description().toLowerCase();
        return lower.contains("opportunity") || lower.contains("client") || lower.contains("sales");
    }
}
