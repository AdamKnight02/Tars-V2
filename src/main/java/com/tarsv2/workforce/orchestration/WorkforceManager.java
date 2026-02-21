package com.tarsv2.workforce.orchestration;

import com.tarsv2.workforce.economics.EconomicEngine;
import com.tarsv2.workforce.planning.GoalDecomposition;
import com.tarsv2.workforce.planning.PlanningEngine;
import com.tarsv2.workforce.scheduler.WorkforceScheduler;
import com.tarsv2.workforce.task.TaskQueue;

import java.util.Objects;

public final class WorkforceManager {

    private final TaskQueue taskQueue;
    private final AgentDispatcher dispatcher;
    private final PlanningEngine planningEngine;
    private final EconomicEngine economicEngine;
    private final WorkforceScheduler scheduler;

    public WorkforceManager(TaskQueue taskQueue,
                            AgentDispatcher dispatcher,
                            PlanningEngine planningEngine,
                            EconomicEngine economicEngine,
                            WorkforceScheduler scheduler) {
        this.taskQueue = Objects.requireNonNull(taskQueue);
        this.dispatcher = Objects.requireNonNull(dispatcher);
        this.planningEngine = Objects.requireNonNull(planningEngine);
        this.economicEngine = Objects.requireNonNull(economicEngine);
        this.scheduler = Objects.requireNonNull(scheduler);
    }

    public void submitGoal(String goal) {
        GoalDecomposition decomposition = planningEngine.plan(goal);
        decomposition.tasks().forEach(taskQueue::enqueue);
    }

    public void start() {
        scheduler.start();
    }

    public void shutdown() {
        scheduler.stop();
    }

    public WorkforceStatus getStatus() {
        return new WorkforceStatus(scheduler.isRunning(), taskQueue.getStats(), scheduler.getHealth(), economicEngine.getSnapshot(), dispatcher.size());
    }

    public EconomicEngine.EconomicSnapshot getEconomicSnapshot() {
        return economicEngine.getSnapshot();
    }
}
