package com.tarsv2.workforce.planning;

import com.tarsv2.workforce.task.Task;

import java.time.Instant;
import java.util.List;

public record GoalDecomposition(String originalGoal, List<Task> tasks, Instant plannedAt, int planningCallsUsed) {
}
