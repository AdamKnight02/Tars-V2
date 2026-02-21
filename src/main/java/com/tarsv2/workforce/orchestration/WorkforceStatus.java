package com.tarsv2.workforce.orchestration;

import com.tarsv2.workforce.economics.EconomicEngine;
import com.tarsv2.workforce.scheduler.WorkforceScheduler;
import com.tarsv2.workforce.task.TaskQueue;

public record WorkforceStatus(boolean schedulerRunning,
                              TaskQueue.TaskQueueStats queueStats,
                              WorkforceScheduler.SchedulerHealth schedulerHealth,
                              EconomicEngine.EconomicSnapshot economicSnapshot,
                              int registeredAgents) {
    @Override
    public String toString() {
        return "WorkforceStatus{" +
                "schedulerRunning=" + schedulerRunning +
                ", queue=" + queueStats +
                ", health=" + schedulerHealth +
                ", economics=" + economicSnapshot +
                ", registeredAgents=" + registeredAgents +
                '}';
    }
}
