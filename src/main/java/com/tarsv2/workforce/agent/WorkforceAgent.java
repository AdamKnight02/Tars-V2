package com.tarsv2.workforce.agent;

import com.tarsv2.workforce.economics.CostEstimator;
import com.tarsv2.workforce.task.Task;
import com.tarsv2.workforce.task.TaskResult;

public interface WorkforceAgent {
    String getName();
    AgentRole getRole();
    String getDescription();
    CostEstimator.Estimate estimateCost(Task task);
    TaskResult execute(Task task);
    boolean canHandle(Task task);
}
