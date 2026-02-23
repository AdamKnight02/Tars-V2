package com.tarsv2.workforce.planning;

import com.tarsv2.connector.DevCapability;
import com.tarsv2.connector.DevPermissionModel;
import com.tarsv2.environment.EnvironmentDispatcher;
import com.tarsv2.environment.EnvironmentFactory;
import com.tarsv2.environment.EnvironmentRegistry;
import com.tarsv2.openclaw.IntentResult;
import com.tarsv2.openclaw.OpenClawClient;
import com.tarsv2.personality.DialogueStyle;
import com.tarsv2.personality.PersonalityProfile;
import com.tarsv2.workforce.economics.*;
import com.tarsv2.workforce.persistence.LedgerRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class PlanningEngineTest {

    @Test
    void parsesKnownJsonIntoTasks() {
        OpenClawClient openClaw = openClawWithContent("[{\"title\":\"Implement\",\"description\":\"Update code\",\"role\":\"ENGINEER\",\"priority\":\"HIGH\",\"estimatedRevenue\":100.0}]");

        PlanningEngine engine = new PlanningEngine(openClaw, PlanningConstraints.defaults(), new CostEstimator());
        GoalDecomposition result = engine.plan("ship feature");

        assertEquals(1, result.tasks().size());
        assertEquals("Implement", result.tasks().get(0).title());
        assertTrue(result.tasks().get(0).estimatedCostUsd() > 0.0d);
    }

    @Test
    void enforcesPlanningCallLimit() {
        OpenClawClient openClaw = openClawWithContent("");
        PlanningEngine engine = new PlanningEngine(openClaw, new PlanningConstraints(3, 20, 1), new CostEstimator());
        GoalDecomposition result = engine.plan("anything");
        assertEquals(1, result.planningCallsUsed());
    }

    private OpenClawClient openClawWithContent(String content) {
        EnvironmentRegistry registry = new EnvironmentRegistry();
        EnvironmentDispatcher dispatcher = (env, intent) -> IntentResult.success(
                intent.getId(),
                Map.of("content", content),
                "ok",
                1
        );
        registry.register(EnvironmentFactory.codingSandbox(dispatcher));

        EconomicEngine economicEngine = new EconomicEngine(
                new CostEstimator(),
                new ProfitabilityGate(),
                new ModelUsageTracker(),
                new InMemoryLedgerRepository()
        );

        return new OpenClawClient(
                registry,
                DevPermissionModel.withCapabilities(DevCapability.READ_CODE, DevCapability.PROPOSE_CHANGES, DevCapability.OPEN_PR),
                new DialogueStyle(PersonalityProfile.defaultProfile()),
                economicEngine
        );
    }

    private static final class InMemoryLedgerRepository implements LedgerRepository {
        private final List<CostLedger> costs = new ArrayList<>();
        private final List<RevenueLedger> revenues = new ArrayList<>();

        @Override public void save(CostLedger entity) { costs.add(entity); }
        @Override public void save(RevenueLedger entity) { revenues.add(entity); }
        @Override public Optional<CostLedger> findCostById(UUID id) { return costs.stream().filter(c -> c.id().equals(id)).findFirst(); }
        @Override public Optional<RevenueLedger> findRevenueById(UUID id) { return revenues.stream().filter(r -> r.id().equals(id)).findFirst(); }
        @Override public List<CostLedger> findAllCosts() { return List.copyOf(costs); }
        @Override public List<RevenueLedger> findAllRevenue() { return List.copyOf(revenues); }
        @Override public List<CostLedger> findCostsByTaskId(UUID taskId) { return costs.stream().filter(c -> Objects.equals(c.taskId(), taskId)).toList(); }
        @Override public List<RevenueLedger> findRevenuesByTaskId(UUID taskId) { return revenues.stream().filter(r -> Objects.equals(r.taskId(), taskId)).toList(); }
        @Override public void update(CostLedger entity) { }
        @Override public void update(RevenueLedger entity) { }
        @Override public void deleteCost(UUID id) { }
        @Override public void deleteRevenue(UUID id) { }
        @Override public double totalCost() { return costs.stream().mapToDouble(CostLedger::costUsd).sum(); }
        @Override public double totalRevenue() { return revenues.stream().mapToDouble(RevenueLedger::amountUsd).sum(); }
    }
}
