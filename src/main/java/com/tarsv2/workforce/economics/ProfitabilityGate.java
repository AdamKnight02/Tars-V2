package com.tarsv2.workforce.economics;

import com.tarsv2.workforce.task.Task;

public final class ProfitabilityGate {

    public boolean isViable(Task task, CostEstimator.Estimate estimate) {
        return estimate.estimatedCostUsd() <= task.expectedRevenueUsd();
    }

    public boolean isViable(Task task) {
        return task.estimatedCostUsd() <= task.expectedRevenueUsd();
    }

    public boolean isViableWithOverride(Task task, boolean humanOverride) {
        return humanOverride || isViable(task);
    }
}
