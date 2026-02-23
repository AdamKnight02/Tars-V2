package com.tarsv2.workforce.economics;

import com.tarsv2.workforce.task.Task;

public final class ProfitabilityGate {

    public boolean isViable(Task task, CostEstimator.Estimate estimate) {
        // Allow low-cost tasks (research, planning, coordination) to run
        if (estimate.estimatedCostUsd() < 0.05) {
            return true;
        }
        return estimate.estimatedCostUsd() <= task.expectedRevenueUsd();
    }

    public boolean isViable(Task task) {
        // Allow low-cost tasks (research, planning, coordination) to run
        if (task.estimatedCostUsd() < 0.05) {
            return true;
        }
        return task.estimatedCostUsd() <= task.expectedRevenueUsd();
    }

    public boolean isViableWithOverride(Task task, boolean humanOverride) {
        return humanOverride || isViable(task);
    }
}
