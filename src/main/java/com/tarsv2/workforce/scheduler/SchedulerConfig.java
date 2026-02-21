package com.tarsv2.workforce.scheduler;

public record SchedulerConfig(int pollIntervalSeconds, int maxConcurrentTasks, int taskTimeoutSeconds) {
    public static SchedulerConfig defaults() {
        return new SchedulerConfig(30, 3, 300);
    }
}
