package com.tarsv2.workforce.agent;

import com.tarsv2.llm.ResearchOrchestrator;
import com.tarsv2.workforce.economics.CostEstimator;
import com.tarsv2.workforce.task.Task;
import com.tarsv2.workforce.task.TaskResult;

import java.time.Instant;
import java.util.Objects;

public final class ResearchAgent implements WorkforceAgent {

    private final ResearchOrchestrator researchOrchestrator;
    private final CostEstimator costEstimator;

    public ResearchAgent(ResearchOrchestrator researchOrchestrator, CostEstimator costEstimator) {
        this.researchOrchestrator = Objects.requireNonNull(researchOrchestrator);
        this.costEstimator = Objects.requireNonNull(costEstimator);
    }

    @Override public String getName() { return "ResearchAgent"; }
    @Override public AgentRole getRole() { return AgentRole.RESEARCHER; }
    @Override public String getDescription() { return "Runs research and returns structured analysis"; }
    @Override public CostEstimator.Estimate estimateCost(Task task) { return costEstimator.estimate(getRole(), task); }

    @Override
    public TaskResult execute(Task task) {
        String result = researchOrchestrator.research(task.description());
        return new TaskResult(task.id(), true, result, null, Instant.now(), 0, 0, "GLM-5");
    }

    @Override
    public boolean canHandle(Task task) {
        String lower = task.description().toLowerCase();
        return lower.contains("research") || lower.contains("analysis") || lower.contains("investigate");
    }
}
