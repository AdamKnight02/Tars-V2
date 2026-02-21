package com.tarsv2.workforce.integration;

import com.tarsv2.workforce.agent.AgentRole;
import com.tarsv2.workforce.task.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TaskLifecycleIT {

    @Test
    void enqueueAndDequeueLifecycle() {
        TaskQueue queue = new TaskQueue();
        Task task = Task.create("title", "desc", AgentRole.SALES, TaskPriority.NORMAL, "goal", 1, 10).withStatus(TaskStatus.QUEUED);
        queue.enqueue(task);
        Task dequeued = queue.dequeue().orElseThrow();
        assertEquals(TaskStatus.QUEUED, dequeued.status());
    }
}
