package com.tarsv2.workforce.planning;

import com.tarsv2.model.ModelRequest;
import com.tarsv2.model.ModelResponse;
import com.tarsv2.model.router.ModelRouter;
import com.tarsv2.model.router.RoutingMode;
import com.tarsv2.workforce.economics.CostEstimator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlanningEngineTest {

    @Test
    void parsesKnownJsonIntoTasks() {
        ModelRouter router = new ModelRouter() {
            @Override
            public ModelResponse route(RoutingMode mode, ModelRequest request) {
                return ModelResponse.ok("[{\"title\":\"Implement\",\"description\":\"Update code\",\"role\":\"ENGINEER\",\"priority\":\"HIGH\",\"estimatedRevenue\":100.0}]");
            }
        };

        PlanningEngine engine = new PlanningEngine(router, PlanningConstraints.defaults(), new CostEstimator());
        GoalDecomposition result = engine.plan("ship feature");

        assertEquals(1, result.tasks().size());
        assertEquals("Implement", result.tasks().getFirst().title());
        assertTrue(result.tasks().getFirst().estimatedCostUsd() > 0.0d);
    }

    @Test
    void enforcesPlanningCallLimit() {
        ModelRouter router = (mode, request) -> ModelResponse.unavailable("down");
        PlanningEngine engine = new PlanningEngine(router, new PlanningConstraints(3, 20, 1), new CostEstimator());
        GoalDecomposition result = engine.plan("anything");
        assertEquals(1, result.planningCallsUsed());
    }
}
