package com.tarsv2.workforce.agent;

import com.tarsv2.openclaw.Intent;
import com.tarsv2.openclaw.IntentContext;
import com.tarsv2.openclaw.IntentPayload;
import com.tarsv2.openclaw.IntentType;
import com.tarsv2.workforce.economics.CostEstimator;
import com.tarsv2.workforce.economics.EconomicEngine;
import com.tarsv2.workforce.task.Task;

import java.util.List;
import java.util.Objects;

public final class FinanceAgent implements WorkforceAgent {

    private final EconomicEngine economicEngine;
    private final CostEstimator costEstimator;

    public FinanceAgent(EconomicEngine economicEngine, CostEstimator costEstimator) {
        this.economicEngine = Objects.requireNonNull(economicEngine);
        this.costEstimator = Objects.requireNonNull(costEstimator);
    }

    @Override public String getName() { return "FinanceAgent"; }
    @Override public AgentRole getRole() { return AgentRole.FINANCE; }
    @Override public String getDescription() { return "Produces finance-analysis intents for OpenClaw execution"; }
    @Override public CostEstimator.Estimate estimateCost(Task task) { return costEstimator.estimate(getRole(), task); }

    @Override
    public List<Intent> plan(Task task) {
        CostEstimator.Estimate estimate = estimateCost(task);
        String snapshot = economicEngine.getSnapshot().toString();
        Intent evaluate = new Intent(
                IntentType.EVALUATE,
                IntentPayload.builder()
                        .put("prompt", "Analyze financial optimization task: " + task.description() + " Snapshot: " + snapshot)
                        .put("taskId", task.id().toString())
                        .build(),
                new IntentContext("research-sandbox", "Tars-V2", estimate.estimatedCostUsd(), 0.72, java.util.Set.of())
        );
        return List.of(evaluate);
    }

    @Override
    public boolean canHandle(Task task) {
        String lower = task.description().toLowerCase();
        return lower.contains("financial") || lower.contains("cost") || lower.contains("revenue") || lower.contains("profit");
    }
}
