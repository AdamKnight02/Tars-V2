package com.tarsv2.workforce.integration;

import com.tarsv2.workforce.agent.AgentRole;
import com.tarsv2.workforce.persistence.DatabaseManager;
import com.tarsv2.workforce.persistence.h2.H2TaskRepository;
import com.tarsv2.workforce.task.Task;
import com.tarsv2.workforce.task.TaskPriority;
import com.tarsv2.workforce.task.TaskStatus;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskLifecycleIT {

    @Test
    void taskPersistsThroughH2RoundTrip() {
        DatabaseManager db = new DatabaseManager();
        db.initialize();
        H2TaskRepository repo = new H2TaskRepository(db.getDataSource());

        Task task = Task.create("Test", "description", AgentRole.RESEARCHER, TaskPriority.NORMAL, "test-goal", 0.01, 1.0);
        repo.save(task);

        Optional<Task> loaded = repo.findById(task.id());
        assertTrue(loaded.isPresent());
        assertEquals("Test", loaded.get().title());
        assertEquals(TaskStatus.PLANNED, loaded.get().status());

        Task updated = loaded.get().withStatus(TaskStatus.QUEUED);
        repo.update(updated);

        Optional<Task> reloaded = repo.findById(task.id());
        assertEquals(TaskStatus.QUEUED, reloaded.orElseThrow().status());
    }
}
