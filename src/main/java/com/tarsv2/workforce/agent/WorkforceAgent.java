package com.tarsv2.workforce.agent;

import com.tarsv2.openclaw.Intent;
import com.tarsv2.workforce.economics.CostEstimator;
import com.tarsv2.workforce.task.Task;

import java.util.List;

public interface WorkforceAgent {
    String getName();
    AgentRole getRole();
    String getDescription();
    CostEstimator.Estimate estimateCost(Task task);
    List<Intent> plan(Task task);
    boolean canHandle(Task task);
}
