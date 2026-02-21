package com.tarsv2.workforce.scheduler;

import com.tarsv2.workforce.economics.EconomicEngine;
import com.tarsv2.workforce.economics.ModelUsageTracker;
import com.tarsv2.workforce.orchestration.AgentDispatcher;
import com.tarsv2.workforce.persistence.TaskRepository;
import com.tarsv2.workforce.task.Task;
import com.tarsv2.workforce.task.TaskQueue;
import com.tarsv2.workforce.task.TaskResult;
import com.tarsv2.workforce.task.TaskStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class WorkforceScheduler {

    private final TaskQueue taskQueue;
    private final AgentDispatcher dispatcher;
    private final EconomicEngine economicEngine;
    private final TaskRepository taskRepository;
    private final ExecutionGuard guard;
    private final SchedulerConfig config;
    private final ExecutorService executor;
    private final AtomicInteger inFlight = new AtomicInteger(0);
    private final AtomicInteger totalExecuted = new AtomicInteger(0);
    private final AtomicInteger totalFailed = new AtomicInteger(0);
    private volatile boolean running;
    private Thread loopThread;

    public WorkforceScheduler(TaskQueue taskQueue,
                              AgentDispatcher dispatcher,
                              EconomicEngine economicEngine,
                              TaskRepository taskRepository,
                              ExecutionGuard guard,
                              SchedulerConfig config) {
        this.taskQueue = Objects.requireNonNull(taskQueue);
        this.dispatcher = Objects.requireNonNull(dispatcher);
        this.economicEngine = Objects.requireNonNull(economicEngine);
        this.taskRepository = Objects.requireNonNull(taskRepository);
        this.guard = Objects.requireNonNull(guard);
        this.config = Objects.requireNonNull(config);
        this.executor = Executors.newFixedThreadPool(config.maxConcurrentTasks());
    }

    public void start() {
        if (running) return;
        running = true;
        loopThread = Thread.ofPlatform().daemon(true).name("workforce-scheduler").start(this::runLoop);
    }

    public void stop() {
        running = false;
        executor.shutdown();
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    public boolean isRunning() { return running; }

    public SchedulerHealth getHealth() {
        return new SchedulerHealth(running, !guard.canExecute(), inFlight.get(), totalExecuted.get(), totalFailed.get());
    }

    private void runLoop() {
        while (running) {
            try {
                if (!guard.canExecute()) {
                    Thread.sleep(config.pollIntervalSeconds() * 1000L);
                    continue;
                }
                List<Task> batch = new ArrayList<>();
                for (int i = 0; i < config.maxConcurrentTasks(); i++) {
                    taskQueue.dequeue().ifPresent(batch::add);
                }
                for (Task task : batch) {
                    Task review = task.withStatus(TaskStatus.ECONOMIC_REVIEW);
                    taskRepository.update(review);
                    if (!economicEngine.approveExecution(task)) {
                        taskRepository.update(review.withStatus(TaskStatus.ECONOMIC_REJECTED));
                        continue;
                    }
                    submitTask(task);
                }
                Thread.sleep(config.pollIntervalSeconds() * 1000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void submitTask(Task task) {
        inFlight.incrementAndGet();
        executor.submit(() -> {
            try {
                Task dispatched = task.withStatus(TaskStatus.DISPATCHED);
                taskRepository.update(dispatched);
                Task executing = dispatched.withStatus(TaskStatus.EXECUTING);
                taskRepository.update(executing);
                TaskResult result = dispatcher.dispatch(executing);
                if (result.success()) {
                    ModelUsageTracker.Usage usage = new ModelUsageTracker.Usage(result.modelUsed(), result.inputTokensUsed(), result.outputTokensUsed(), 1);
                    economicEngine.recordCost(task.id(), usage);
                    TaskStatus successStatus = result.output().contains("awaiting approval") ? TaskStatus.AWAITING_APPROVAL : TaskStatus.COMPLETED;
                    taskRepository.update(executing.withStatus(successStatus));
                    totalExecuted.incrementAndGet();
                    guard.recordSuccess();
                } else {
                    totalFailed.incrementAndGet();
                    guard.recordFailure();
                    Task retried = executing.withRetryIncrement();
                    if (retried.retryCount() < retried.maxRetries()) {
                        taskQueue.enqueue(retried.withStatus(TaskStatus.QUEUED));
                    } else {
                        taskRepository.update(retried.withStatus(TaskStatus.FAILED));
                    }
                }
            } finally {
                inFlight.decrementAndGet();
            }
        });
    }

    public record SchedulerHealth(boolean running, boolean circuitBreakerOpen, int tasksInFlight, int totalExecuted, int totalFailed) {
    }
}
