package com.tarsv2.workforce.task;

import com.tarsv2.workforce.persistence.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.PriorityBlockingQueue;

public final class TaskQueue {

    private static final Logger log = LoggerFactory.getLogger(TaskQueue.class);

    private final PriorityBlockingQueue<Task> queue = new PriorityBlockingQueue<>(32, Comparator
            .comparingDouble(TaskQueue::roiScore)
            .reversed()
            .thenComparing(Task::createdAt));
    private final TaskRepository taskRepository;

    public TaskQueue() {
        this.taskRepository = null;
    }

    public TaskQueue(TaskRepository taskRepository) {
        this.taskRepository = Objects.requireNonNull(taskRepository);
    }

    public void enqueue(Task task) {
        Task queued = task.status() == TaskStatus.QUEUED ? task : task.withStatus(TaskStatus.QUEUED);
        queue.offer(queued);
        if (taskRepository != null) {
            taskRepository.findById(queued.id()).ifPresentOrElse(t -> taskRepository.update(queued), () -> taskRepository.save(queued));
        }
        log.info("Task enqueued: {} [{}]", queued.id(), queued.status());
    }

    public Optional<Task> dequeue() {
        Task next = queue.poll();
        while (next != null && next.status() != TaskStatus.QUEUED) {
            next = queue.poll();
        }
        return Optional.ofNullable(next);
    }

    public Optional<Task> peek() {
        return Optional.ofNullable(queue.peek());
    }

    public List<Task> snapshot() {
        return List.copyOf(new ArrayList<>(queue));
    }

    public TaskQueueStats getStats() {
        List<Task> tasks = snapshot();
        int queued = 0;
        int executing = 0;
        int completed = 0;
        int failed = 0;
        for (Task task : tasks) {
            if (task.status() == TaskStatus.QUEUED) queued++;
            if (task.status() == TaskStatus.EXECUTING) executing++;
            if (task.status() == TaskStatus.COMPLETED) completed++;
            if (task.status() == TaskStatus.FAILED) failed++;
        }
        return new TaskQueueStats(tasks.size(), queued, executing, completed, failed);
    }

    private static double roiScore(Task task) {
        if (task.estimatedCostUsd() <= 0.0d) {
            return task.expectedRevenueUsd() > 0.0d ? Double.MAX_VALUE : 0.0d;
        }
        return task.expectedRevenueUsd() / task.estimatedCostUsd();
    }

    public record TaskQueueStats(int total, int queued, int executing, int completed, int failed) {
    }
}
