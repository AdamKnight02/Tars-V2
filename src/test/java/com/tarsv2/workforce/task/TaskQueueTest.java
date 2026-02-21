package com.tarsv2.workforce.task;

import com.tarsv2.workforce.agent.AgentRole;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TaskQueueTest {

    @Test
    void dequeuesByRoiDescending() {
        TaskQueue queue = new TaskQueue();
        Task low = Task.create("low", "desc", AgentRole.SALES, TaskPriority.NORMAL, "g", 10, 20).withStatus(TaskStatus.QUEUED);
        Task high = Task.create("high", "desc", AgentRole.SALES, TaskPriority.NORMAL, "g", 5, 30).withStatus(TaskStatus.QUEUED);

        queue.enqueue(low);
        queue.enqueue(high);

        assertEquals("high", queue.dequeue().orElseThrow().title());
        assertEquals("low", queue.dequeue().orElseThrow().title());
    }
}
