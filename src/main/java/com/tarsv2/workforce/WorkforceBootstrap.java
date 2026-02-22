package com.tarsv2.workforce;

import com.tarsv2.approval.ApprovalGate;
import com.tarsv2.approval.ProposalRegistry;
import com.tarsv2.codex.CodexOrchestrator;
import com.tarsv2.codex.PatchValidator;
import com.tarsv2.llm.ChatOrchestrator;
import com.tarsv2.llm.ResearchOrchestrator;
import com.tarsv2.model.router.ModelRouter;
import com.tarsv2.workforce.agent.*;
import com.tarsv2.workforce.economics.*;
import com.tarsv2.workforce.orchestration.AgentDispatcher;
import com.tarsv2.workforce.orchestration.WorkforceManager;
import com.tarsv2.workforce.persistence.DatabaseManager;
import com.tarsv2.workforce.persistence.h2.H2LedgerRepository;
import com.tarsv2.workforce.persistence.h2.H2OpportunityRepository;
import com.tarsv2.workforce.persistence.h2.H2TaskRepository;
import com.tarsv2.workforce.planning.PlanningConstraints;
import com.tarsv2.workforce.planning.PlanningEngine;
import com.tarsv2.workforce.revenue.OpportunityScanner;
import com.tarsv2.workforce.scheduler.*;
import com.tarsv2.workforce.task.TaskQueue;

public final class WorkforceBootstrap {

    private WorkforceBootstrap() {
    }

    public static WorkforceManager initialize(ModelRouter modelRouter,
                                              CodexOrchestrator codexOrchestrator,
                                              ResearchOrchestrator researchOrchestrator,
                                              ChatOrchestrator chatOrchestrator,
                                              ApprovalGate approvalGate,
                                              ProposalRegistry proposalRegistry,
                                              PatchValidator patchValidator) {
        DatabaseManager db = new DatabaseManager();
        db.initialize();

        H2TaskRepository taskRepository = new H2TaskRepository(db.getDataSource());
        H2LedgerRepository ledgerRepository = new H2LedgerRepository(db.getDataSource());
        H2OpportunityRepository opportunityRepository = new H2OpportunityRepository(db.getDataSource());

        CostEstimator costEstimator = new CostEstimator();
        ProfitabilityGate profitabilityGate = new ProfitabilityGate();
        ModelUsageTracker usageTracker = new ModelUsageTracker();
        EconomicEngine economicEngine = new EconomicEngine(costEstimator, profitabilityGate, usageTracker, ledgerRepository);

        TaskQueue queue = new TaskQueue(taskRepository);

        EngineerAgent engineerAgent = new EngineerAgent(codexOrchestrator, patchValidator, proposalRegistry, costEstimator);
        ResearchAgent researchAgent = new ResearchAgent(researchOrchestrator, costEstimator);
        SalesAgent salesAgent = new SalesAgent(modelRouter, costEstimator);
        FinanceAgent financeAgent = new FinanceAgent(modelRouter, economicEngine, costEstimator);

        AgentDispatcher dispatcher = new AgentDispatcher();
        dispatcher.register(engineerAgent);
        dispatcher.register(researchAgent);
        dispatcher.register(salesAgent);
        dispatcher.register(financeAgent);

        PlanningEngine planningEngine = new PlanningEngine(modelRouter, PlanningConstraints.defaults(), costEstimator);

        ExecutionGuard guard = new ExecutionGuard();
        SchedulerConfig schedulerConfig = SchedulerConfig.defaults();
        WorkforceScheduler scheduler = new WorkforceScheduler(queue, dispatcher, economicEngine, taskRepository, guard, schedulerConfig);
        OpportunityScanner opportunityScanner = new OpportunityScanner(modelRouter);

        return new WorkforceManager(queue, dispatcher, planningEngine, economicEngine, scheduler, opportunityScanner);
    }
}
