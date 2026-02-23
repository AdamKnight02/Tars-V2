package com.tarsv2.workforce.planning;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tarsv2.openclaw.Intent;
import com.tarsv2.openclaw.IntentContext;
import com.tarsv2.openclaw.IntentPayload;
import com.tarsv2.openclaw.IntentResult;
import com.tarsv2.openclaw.IntentType;
import com.tarsv2.openclaw.OpenClawClient;
import com.tarsv2.workforce.agent.AgentRole;
import com.tarsv2.workforce.economics.CostEstimator;
import com.tarsv2.workforce.task.Task;
import com.tarsv2.workforce.task.TaskPriority;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class PlanningEngine {

    private static final Logger log = LoggerFactory.getLogger(PlanningEngine.class);

    private final OpenClawClient openClaw;
    private final PlanningConstraints constraints;
    private final CostEstimator costEstimator;
    private final ObjectMapper mapper = new ObjectMapper();

    public PlanningEngine(OpenClawClient openClaw, PlanningConstraints constraints, CostEstimator costEstimator) {
        this.openClaw = Objects.requireNonNull(openClaw);
        this.constraints = Objects.requireNonNull(constraints);
        this.costEstimator = Objects.requireNonNull(costEstimator);
    }

    public GoalDecomposition plan(String goal) {
        int calls = 0;
        List<Task> tasks = new ArrayList<>();
        while (calls < constraints.maxPlanningCallsPerGoal()) {
            calls++;
            Intent intent = new Intent(
                    IntentType.REASON,
                    IntentPayload.builder()
                            .put("prompt", "Decompose goal into JSON tasks: " + goal)
                            .build(),
                    new IntentContext("coding-sandbox", "Tars-V2", 0.02d, 0.8d, Set.of())
            );
            IntentResult response = openClaw.execute(intent);
            if (!response.success()) {
                break;
            }
            try {
                String raw = response.output().getOrDefault("content", "[]").trim();
                if (raw.isBlank()) {
                    break;
                }
                if (raw.startsWith("```")) {
                    raw = raw.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "").trim();
                }
                int start = raw.indexOf('[');
                int end = raw.lastIndexOf(']');
                if (start == -1 || end == -1 || end <= start) {
                    log.warn("No JSON array found in planning response: {}", raw.substring(0, Math.min(200, raw.length())));
                    break;
                }
                raw = raw.substring(start, end + 1);
                JsonNode root = mapper.readTree(raw);
                if (!root.isArray()) {
                    break;
                }
                for (JsonNode node : root) {
                    if (tasks.size() >= constraints.maxTasksPerGoal()) {
                        throw new IllegalStateException("maxTasksPerGoal exceeded");
                    }
                    AgentRole role;
                    try {
                        role = AgentRole.valueOf(node.path("role").asText("RESEARCHER").toUpperCase().trim());
                    } catch (IllegalArgumentException ex) {
                        role = AgentRole.RESEARCHER;
                    }
                    TaskPriority priority;
                    try {
                        priority = TaskPriority.valueOf(node.path("priority").asText("NORMAL").toUpperCase().trim());
                    } catch (IllegalArgumentException ex) {
                        priority = TaskPriority.NORMAL;
                    }
                    String revStr = node.path("estimatedRevenue").asText("0");
                    double expectedRevenue;
                    try {
                        expectedRevenue = Double.parseDouble(revStr.replaceAll("[^0-9.]", ""));
                    } catch (NumberFormatException ex) {
                        expectedRevenue = 0.0;
                    }
                    Task seed = Task.create(node.path("title").asText("Untitled"), node.path("description").asText(""),
                            role, priority, goal, 0.0d, expectedRevenue);
                    CostEstimator.Estimate estimate = costEstimator.estimate(role, seed);
                    tasks.add(new Task(seed.id(), seed.title(), seed.description(), seed.assignedRole(), seed.status(),
                            seed.priority(), seed.parentTaskId(), seed.goalOrigin(), estimate.estimatedCostUsd(),
                            seed.expectedRevenueUsd(), seed.createdAt(), seed.updatedAt(), seed.completedAt(), seed.retryCount(), seed.maxRetries()));
                }
                break;
            } catch (Exception e) {
                log.error("Failed to parse planning response", e);
                return new GoalDecomposition(goal, List.of(), Instant.now(), calls);
            }
        }
        return new GoalDecomposition(goal, List.copyOf(tasks), Instant.now(), calls);
    }
}
