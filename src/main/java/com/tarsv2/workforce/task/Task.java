package com.tarsv2.workforce.task;

import com.tarsv2.workforce.agent.AgentRole;

import java.time.Instant;
import java.util.UUID;

public record Task(
        UUID id,
        String title,
        String description,
        AgentRole assignedRole,
        TaskStatus status,
        TaskPriority priority,
        UUID parentTaskId,
        String goalOrigin,
        double estimatedCostUsd,
        double expectedRevenueUsd,
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt,
        int retryCount,
        int maxRetries
) {

    public static Task create(String title,
                              String description,
                              AgentRole role,
                              TaskPriority priority,
                              String goalOrigin,
                              double estimatedCost,
                              double expectedRevenue) {
        Instant now = Instant.now();
        return new Task(UUID.randomUUID(), title, description, role, TaskStatus.PLANNED, priority, null, goalOrigin,
                estimatedCost, expectedRevenue, now, now, null, 0, 2);
    }

    public Task withStatus(TaskStatus nextStatus) {
        Instant now = Instant.now();
        Instant completed = (nextStatus == TaskStatus.COMPLETED || nextStatus == TaskStatus.FAILED || nextStatus == TaskStatus.CANCELLED)
                ? now : completedAt;
        return new Task(id, title, description, assignedRole, nextStatus, priority, parentTaskId, goalOrigin,
                estimatedCostUsd, expectedRevenueUsd, createdAt, now, completed, retryCount, maxRetries);
    }

    public Task withRetryIncrement() {
        return new Task(id, title, description, assignedRole, status, priority, parentTaskId, goalOrigin,
                estimatedCostUsd, expectedRevenueUsd, createdAt, Instant.now(), completedAt, retryCount + 1, maxRetries);
    }
}
