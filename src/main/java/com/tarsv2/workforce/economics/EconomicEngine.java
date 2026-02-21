package com.tarsv2.workforce.economics;

import com.tarsv2.workforce.persistence.LedgerRepository;
import com.tarsv2.workforce.task.Task;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class EconomicEngine {

    private final CostEstimator costEstimator;
    private final ProfitabilityGate profitabilityGate;
    private final ModelUsageTracker usageTracker;
    private final LedgerRepository ledgerRepository;

    public EconomicEngine(CostEstimator costEstimator,
                          ProfitabilityGate profitabilityGate,
                          ModelUsageTracker usageTracker,
                          LedgerRepository ledgerRepository) {
        this.costEstimator = Objects.requireNonNull(costEstimator);
        this.profitabilityGate = Objects.requireNonNull(profitabilityGate);
        this.usageTracker = Objects.requireNonNull(usageTracker);
        this.ledgerRepository = Objects.requireNonNull(ledgerRepository);
    }

    public boolean approveExecution(Task task) {
        CostEstimator.Estimate estimate = costEstimator.estimate(task.assignedRole(), task);
        return profitabilityGate.isViable(task, estimate);
    }

    public void recordCost(UUID taskId, ModelUsageTracker.Usage usage) {
        usageTracker.record(taskId, usage);
        double cost = costEstimator.calculateActualCost(usage.modelId(), usage.inputTokens(), usage.outputTokens());
        ledgerRepository.save(new CostLedger(UUID.randomUUID(), taskId, usage.modelId(), providerFor(usage.modelId()),
                usage.inputTokens(), usage.outputTokens(), usage.apiCalls(), cost, Instant.now()));
    }

    public void recordRevenue(UUID taskId, String source, String description, double amountUsd) {
        ledgerRepository.save(new RevenueLedger(UUID.randomUUID(), taskId, source, description, amountUsd, false, Instant.now(), null));
    }

    public EconomicSnapshot getSnapshot() {
        double totalCost = ledgerRepository.totalCost();
        double totalRevenue = ledgerRepository.totalRevenue();
        Map<String, Double> costByModel = new HashMap<>();
        ledgerRepository.findAllCosts().forEach(c -> costByModel.merge(c.modelId(), c.costUsd(), Double::sum));
        return new EconomicSnapshot(totalCost, totalRevenue, totalRevenue - totalCost,
                ledgerRepository.findAllCosts().size(), Map.copyOf(costByModel));
    }

    private String providerFor(String modelId) {
        if (modelId.startsWith("MiniMax") || modelId.equals("m2.5")) return "MiniMax";
        if (modelId.startsWith("GLM")) return "Zhipu";
        return "Unknown";
    }

    public record EconomicSnapshot(double totalCostUsd,
                                   double totalRevenueUsd,
                                   double profitMarginUsd,
                                   int totalTasksExecuted,
                                   Map<String, Double> costByModel) {
    }
}
