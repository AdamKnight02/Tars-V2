package com.tarsv2.workforce.planning;

public record PlanningConstraints(int maxDepth, int maxTasksPerGoal, int maxPlanningCallsPerGoal) {

    public static PlanningConstraints defaults() {
        return new PlanningConstraints(3, 20, 5);
    }
}
