package com.tarsv2.chunk;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Strategy weights updated by chunk scoring.
 *
 * <p>Weights influence TARS's approach to future tasks:</p>
 * <ul>
 *   <li>test-first bias — prefer writing tests before fixes</li>
 *   <li>small-diff bias — prefer smaller, focused changes</li>
 *   <li>avoid fragile modules — reduce changes to historically failing areas</li>
 *   <li>reduce noise — avoid unnecessary file touches</li>
 * </ul>
 *
 * <p>No RL training. Pure statistical reinforcement based on chunk scores.</p>
 */
public final class StrategyWeights {

    private static final Logger log = LoggerFactory.getLogger(StrategyWeights.class);

    /** Learning rate for weight updates. */
    private static final double LEARNING_RATE = 0.05;

    private final Map<String, Double> weights = new ConcurrentHashMap<>();

    public StrategyWeights() {
        // Initialize default weights
        weights.put("test-first-bias", 0.5);
        weights.put("small-diff-bias", 0.5);
        weights.put("avoid-fragile-modules", 0.5);
        weights.put("reduce-noise", 0.5);
    }

    /**
     * Returns the current weight for a strategy.
     */
    public double getWeight(String strategy) {
        return weights.getOrDefault(strategy, 0.5);
    }

    /**
     * Updates weights based on a scored chunk.
     *
     * <p>Positive chunk scores reinforce the strategies used in that chunk.
     * Negative scores dampen them.</p>
     */
    public void update(Chunk chunk) {
        double score = chunk.getScore();

        // Test-first bias: reinforce if chunk had tests
        boolean hadTests = chunk.getActions().stream()
                .anyMatch(a -> a.type().contains("TEST"));
        if (hadTests) {
            adjust("test-first-bias", score);
        }

        // Small-diff bias: reinforce if diff was small
        int totalLines = chunk.getActions().stream()
                .mapToInt(Chunk.ChunkAction::linesChanged)
                .sum();
        if (totalLines < 50) {
            adjust("small-diff-bias", score);
        }

        // Avoid-fragile-modules: penalize if chunk touched many files and failed
        int filesChanged = chunk.getActions().stream()
                .mapToInt(Chunk.ChunkAction::filesChanged)
                .sum();
        if (filesChanged > 5 && score < 0) {
            adjust("avoid-fragile-modules", Math.abs(score));
        }

        // Reduce noise: penalize if many files changed for little benefit
        if (filesChanged > 3 && score < 0.2) {
            adjust("reduce-noise", -0.1);
        }

        log.info("Strategy weights updated from chunk {}: {}", chunk.getId(), weights);
    }

    /**
     * Returns all current weights.
     */
    public Map<String, Double> getAll() {
        return Map.copyOf(weights);
    }

    /**
     * Returns a human-readable summary.
     */
    public String getSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Strategy Weights ===\n");
        weights.forEach((k, v) ->
                sb.append(String.format("  %s: %.3f\n", k, v)));
        return sb.toString();
    }

    private void adjust(String strategy, double delta) {
        weights.compute(strategy, (k, v) -> {
            double current = v != null ? v : 0.5;
            double updated = current + (LEARNING_RATE * delta);
            return Math.max(0.0, Math.min(1.0, updated));
        });
    }
}
