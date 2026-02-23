package com.tarsv2.workforce.planning;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tarsv2.model.ModelRequest;
import com.tarsv2.model.ModelResponse;
import com.tarsv2.model.router.ModelRouter;
import com.tarsv2.model.router.RoutingMode;
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

public final class PlanningEngine {

    private static final Logger log = LoggerFactory.getLogger(PlanningEngine.class);

    private final ModelRouter modelRouter;
    private final PlanningConstraints constraints;
    private final CostEstimator costEstimator;
    private final ObjectMapper mapper = new ObjectMapper();

    public PlanningEngine(ModelRouter modelRouter, PlanningConstraints constraints, CostEstimator costEstimator) {
        this.modelRouter = Objects.requireNonNull(modelRouter);
        this.constraints = Objects.requireNonNull(constraints);
        this.costEstimator = Objects.requireNonNull(costEstimator);
    }

    public GoalDecomposition plan(String goal) {
        System.out.println("=== PLANNING ENGINE START === goal: " + goal);
        int calls = 0;
        List<Task> tasks = new ArrayList<>();
        while (calls < constraints.maxPlanningCallsPerGoal()) {
            calls++;
            ModelResponse response = modelRouter.route(RoutingMode.RESEARCH, new ModelRequest(
                    "You are a planning engine.",
                    "Decompose goal into JSON array with fields title, description, role, priority, estimatedRevenue. Goal: " + goal,
                    true));
            System.out.println("=== PLANNING RESPONSE status=" + response.status() + " contentLength=" + (response.content() == null ? "null" : response.content().length()));
            if (response.content() != null) {
                System.out.println("=== FIRST 300 CHARS: " + response.content().substring(0, Math.min(300, response.content().length())));
            }
            log.debug("Planning model response status: {}, content preview: {}",
                    response.status(), response.content() == null ? null : response.content().substring(0, Math.min(500, response.content().length())));
            if (response.status() != ModelResponse.Status.OK) {
                break;
            }
            try {
                String raw = response.content().trim();
                // Strip markdown code fences if present
                if (raw.startsWith("```")) {
                    raw = raw.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "").trim();
                }
                // Find the JSON array in the response
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
                    AgentRole role = AgentRole.valueOf(node.path("role").asText("RESEARCHER"));
                    TaskPriority priority = TaskPriority.valueOf(node.path("priority").asText("NORMAL"));
                    double expectedRevenue = node.path("estimatedRevenue").asDouble(0.0d);
                    Task seed = Task.create(node.path("title").asText("Untitled"), node.path("description").asText(""),
                            role, priority, goal, 0.0d, expectedRevenue);
                    CostEstimator.Estimate estimate = costEstimator.estimate(role, seed);
                    tasks.add(new Task(seed.id(), seed.title(), seed.description(), seed.assignedRole(), seed.status(),
                            seed.priority(), seed.parentTaskId(), seed.goalOrigin(), estimate.estimatedCostUsd(),
                            seed.expectedRevenueUsd(), seed.createdAt(), seed.updatedAt(), seed.completedAt(), seed.retryCount(), seed.maxRetries()));
                }
                break;
            } catch (Exception e) {
                System.out.println("=== PARSE FAILED: " + e.getMessage());
                log.error("Failed to parse planning response", e);
                log.error("Raw response was: {}", response.content().substring(0, Math.min(500, response.content().length())));
                return new GoalDecomposition(goal, List.of(), Instant.now(), calls);
            }
        }
        return new GoalDecomposition(goal, List.copyOf(tasks), Instant.now(), calls);
    }
}
