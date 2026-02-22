package com.tarsv2.workforce.economics;

import com.tarsv2.workforce.agent.AgentRole;
import com.tarsv2.workforce.task.Task;

public final class CostEstimator {

    public Estimate estimate(AgentRole role, Task task) {
        int inputTokens = Math.max(250, task.description().length() * 2);
        int outputTokens = Math.max(180, inputTokens / 2);
        String modelId = modelFor(role);
        double cost = calculateActualCost(modelId, inputTokens, outputTokens);
        return new Estimate(modelId, inputTokens, outputTokens, cost);
    }

    public double calculateActualCost(String modelId, int inputTokens, int outputTokens) {
        Rate rate = switch (modelId) {
            case "MiniMax-M2.5", "m2.5" -> new Rate(0.15, 1.20);
            case "GLM-5" -> new Rate(1.00, 3.20);
            case "GLM-4.7" -> new Rate(0.60, 2.20);
            default -> new Rate(1.00, 3.20);
        };
        return ((inputTokens * rate.inputPerMillion()) + (outputTokens * rate.outputPerMillion())) / 1_000_000d;
    }

    private String modelFor(AgentRole role) {
        return switch (role) {
            case ENGINEER, SALES -> "MiniMax-M2.5";
            case RESEARCHER, FINANCE -> "GLM-5";
        };
    }

    private record Rate(double inputPerMillion, double outputPerMillion) {
    }

    public record Estimate(String modelId, int estimatedInputTokens, int estimatedOutputTokens, double estimatedCostUsd) {
    }
}
