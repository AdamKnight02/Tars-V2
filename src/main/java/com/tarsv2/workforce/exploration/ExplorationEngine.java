package com.tarsv2.workforce.exploration;

import com.tarsv2.workforce.agent.AgentRole;
import com.tarsv2.workforce.economics.CostEstimator;
import com.tarsv2.workforce.economics.EconomicEngine;
import com.tarsv2.workforce.task.Task;
import com.tarsv2.workforce.task.TaskPriority;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ExplorationEngine {

    private final ExplorationConfig config;
    private final EconomicEngine economicEngine;
    private final CostEstimator costEstimator;
    private Instant lastExplorationAt;

    public ExplorationEngine(ExplorationConfig config, EconomicEngine economicEngine, CostEstimator costEstimator) {
        this.config = Objects.requireNonNull(config);
        this.economicEngine = Objects.requireNonNull(economicEngine);
        this.costEstimator = Objects.requireNonNull(costEstimator);
    }

    public boolean canExplore() {
        if (lastExplorationAt != null && Instant.now().isBefore(lastExplorationAt.plus(config.cooldown()))) {
            return false;
        }
        return economicEngine.getTodaySpent() < config.maxDailyBudgetUsd();
    }

    public List<Task> generateExplorationTasks() {
        if (!canExplore()) {
            return List.of();
        }
        List<Task> tasks = new ArrayList<>();
        double cycleBudget = 0.0d;
        for (int i = 0; i < config.maxTasksPerCycle(); i++) {
            AgentRole role = (i % 2 == 0) ? AgentRole.RESEARCHER : AgentRole.FINANCE;
            Task t = Task.create(
                    "Exploration " + (i + 1),
                    "Explore autonomous opportunity with read-only analysis; do not write files or patches",
                    role,
                    TaskPriority.LOW,
                    "exploration",
                    0.0d,
                    0.0d
            );
            double estimate = costEstimator.estimate(role, t).estimatedCostUsd();
            if (cycleBudget + estimate > config.maxCycleBudgetUsd()) {
                break;
            }
            cycleBudget += estimate;
            tasks.add(new Task(
                    t.id(), t.title(), t.description(), role, t.status(), t.priority(), t.parentTaskId(), t.goalOrigin(),
                    estimate, t.expectedRevenueUsd(), t.createdAt(), t.updatedAt(), t.completedAt(), t.retryCount(), t.maxRetries()
            ));
        }
        lastExplorationAt = tasks.isEmpty() ? lastExplorationAt : Instant.now();
        return List.copyOf(tasks);
    }
}
