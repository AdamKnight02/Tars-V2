package com.tarsv2.workforce.agent;

import com.tarsv2.model.ModelRequest;
import com.tarsv2.model.router.ModelRouter;
import com.tarsv2.model.router.RoutingMode;
import com.tarsv2.workforce.economics.CostEstimator;
import com.tarsv2.workforce.economics.EconomicEngine;
import com.tarsv2.workforce.task.Task;
import com.tarsv2.workforce.task.TaskResult;

import java.time.Instant;
import java.util.Objects;

public final class FinanceAgent implements WorkforceAgent {

    private final ModelRouter modelRouter;
    private final EconomicEngine economicEngine;
    private final CostEstimator costEstimator;

    public FinanceAgent(ModelRouter modelRouter, EconomicEngine economicEngine, CostEstimator costEstimator) {
        this.modelRouter = Objects.requireNonNull(modelRouter);
        this.economicEngine = Objects.requireNonNull(economicEngine);
        this.costEstimator = Objects.requireNonNull(costEstimator);
    }

    @Override public String getName() { return "FinanceAgent"; }
    @Override public AgentRole getRole() { return AgentRole.FINANCE; }
    @Override public String getDescription() { return "Analyzes costs/revenue and recommends optimization"; }
    @Override public CostEstimator.Estimate estimateCost(Task task) { return costEstimator.estimate(getRole(), task); }

    @Override
    public TaskResult execute(Task task) {
        var snapshot = economicEngine.getSnapshot();
        var response = modelRouter.route(RoutingMode.RESEARCH, new ModelRequest(
                "You are a finance analyst.",
                "Analyze this task with current economics and return JSON recommendations. Task: " + task.description() + " Snapshot: " + snapshot,
                true));
        return new TaskResult(task.id(), response.status().name().equals("OK"), response.content(), response.message(), Instant.now(), 0, 0, "GLM-5");
    }

    @Override
    public boolean canHandle(Task task) {
        String lower = task.description().toLowerCase();
        return lower.contains("financial") || lower.contains("cost") || lower.contains("revenue") || lower.contains("profit");
    }
}
