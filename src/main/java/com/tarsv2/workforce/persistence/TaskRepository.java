package com.tarsv2.workforce.persistence;

import com.tarsv2.workforce.task.Task;
import com.tarsv2.workforce.task.TaskStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaskRepository {
    void save(Task entity);

    Optional<Task> findById(UUID id);

    List<Task> findAll();

    List<Task> findByStatus(TaskStatus status);

    List<Task> findByGoalOrigin(String goalOrigin);

    void update(Task entity);

    void delete(UUID id);
}
