package com.tarsv2.workforce.economics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CostEstimatorTest {

    @Test
    void calculatesKnownMiniMaxCost() {
        CostEstimator estimator = new CostEstimator();
        double cost = estimator.calculateActualCost("MiniMax-M2.5", 1_000_000, 1_000_000);
        assertEquals(1.35d, cost, 0.00001);
    }

    @Test
    void calculatesKnownGlmCost() {
        CostEstimator estimator = new CostEstimator();
        double cost = estimator.calculateActualCost("GLM-5", 2_000_000, 500_000);
        assertEquals(3.60d, cost, 0.00001);
    }
}
