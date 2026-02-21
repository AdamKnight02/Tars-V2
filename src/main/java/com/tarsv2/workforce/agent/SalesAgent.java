package com.tarsv2.workforce.agent;

import com.tarsv2.model.ModelRequest;
import com.tarsv2.model.router.ModelRouter;
import com.tarsv2.model.router.RoutingMode;
import com.tarsv2.workforce.economics.CostEstimator;
import com.tarsv2.workforce.task.Task;
import com.tarsv2.workforce.task.TaskResult;

import java.time.Instant;
import java.util.Objects;

public final class SalesAgent implements WorkforceAgent {

    private final ModelRouter modelRouter;
    private final CostEstimator costEstimator;

    public SalesAgent(ModelRouter modelRouter, CostEstimator costEstimator) {
        this.modelRouter = Objects.requireNonNull(modelRouter);
        this.costEstimator = Objects.requireNonNull(costEstimator);
    }

    @Override public String getName() { return "SalesAgent"; }
    @Override public AgentRole getRole() { return AgentRole.SALES; }
    @Override public String getDescription() { return "Evaluates opportunities and client-facing strategy"; }
    @Override public CostEstimator.Estimate estimateCost(Task task) { return costEstimator.estimate(getRole(), task); }

    @Override
    public TaskResult execute(Task task) {
        var response = modelRouter.route(RoutingMode.CHAT, new ModelRequest(
                "You are a sales strategist.",
                "Evaluate this opportunity and return JSON with viabilityScore(0-100), estimatedEffort, recommendedApproach: " + task.description(),
                true));
        return new TaskResult(task.id(), response.status().name().equals("OK"), response.content(), response.message(), Instant.now(), 0, 0, "GLM-4.7");
    }

    @Override
    public boolean canHandle(Task task) {
        String lower = task.description().toLowerCase();
        return lower.contains("opportunity") || lower.contains("client") || lower.contains("sales");
    }
}
