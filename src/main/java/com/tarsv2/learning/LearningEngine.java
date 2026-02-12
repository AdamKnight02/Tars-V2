package com.tarsv2.learning;

import com.tarsv2.metrics.ObservationMetrics;
import com.tarsv2.personality.DialogueStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Non-destructive learning engine that aggregates metrics, ranks strategies,
 * and decides WHEN to propose improvements.
 *
 * <p>The LearningEngine NEVER modifies code directly. It produces
 * learning signals and improvement recommendations that feed into
 * the proposal generation pipeline via the SelfImprovementLoop.</p>
 */
public final class LearningEngine {

    private static final Logger log = LoggerFactory.getLogger(LearningEngine.class);

    /** Minimum number of data points before making recommendations. */
    private static final int MIN_SAMPLES = 5;

    /** Success rate below this triggers an improvement proposal. */
    private static final double SUCCESS_THRESHOLD = 0.7;

    /** Confidence score below this triggers a review. */
    private static final double CONFIDENCE_THRESHOLD = 0.6;

    /** Latency increase percentage that triggers optimization proposals. */
    private static final double LATENCY_SPIKE_FACTOR = 1.5;

    private final ObservationMetrics metrics;
    private final DialogueStyle dialogue;
    private final Map<String, StrategyRanking> strategyRankings = new LinkedHashMap<>();

    public LearningEngine(ObservationMetrics metrics, DialogueStyle dialogue) {
        this.metrics = Objects.requireNonNull(metrics);
        this.dialogue = Objects.requireNonNull(dialogue);
    }

    /**
     * Analyzes collected metrics and returns improvement recommendations.
     * Does NOT modify any code — only produces signal data.
     *
     * @return list of improvement recommendations (may be empty)
     */
    public List<LearningRecommendation> analyze() {
        List<LearningRecommendation> recommendations = new ArrayList<>();

        if (metrics.getTotalCount() < MIN_SAMPLES) {
            dialogue.say("Not enough data for learning yet. Need " + MIN_SAMPLES
                    + " samples, have " + metrics.getTotalCount() + ".");
            return recommendations;
        }

        dialogue.say("Analyzing " + metrics.getTotalCount() + " task metrics for learning signals...", DialogueStyle.OutputMode.CHAT);

        // Group metrics by agent
        Map<String, List<ObservationMetrics.TaskMetric>> byAgent = metrics.getAll().stream()
                .collect(Collectors.groupingBy(ObservationMetrics.TaskMetric::agentName));

        for (var entry : byAgent.entrySet()) {
            String agent = entry.getKey();
            List<ObservationMetrics.TaskMetric> agentMetrics = entry.getValue();

            // Check success rate
            double successRate = metrics.getSuccessRate(agent);
            if (successRate < SUCCESS_THRESHOLD) {
                recommendations.add(new LearningRecommendation(
                        agent,
                        RecommendationType.IMPROVE_RELIABILITY,
                        String.format("Agent '%s' has %.0f%% success rate (threshold: %.0f%%). "
                                + "Consider reviewing error patterns and adding retry logic.",
                                agent, successRate * 100, SUCCESS_THRESHOLD * 100),
                        1.0 - successRate // priority: worse rate = higher priority
                ));
            }

            // Check confidence scores
            double avgConfidence = metrics.getAverageConfidence(agent);
            if (avgConfidence < CONFIDENCE_THRESHOLD) {
                recommendations.add(new LearningRecommendation(
                        agent,
                        RecommendationType.IMPROVE_QUALITY,
                        String.format("Agent '%s' has low average confidence (%.2f). "
                                + "Consider refining prompts or adding validation steps.",
                                agent, avgConfidence),
                        CONFIDENCE_THRESHOLD - avgConfidence
                ));
            }

            // Check for latency trends (compare recent vs historical)
            checkLatencyTrend(agent, agentMetrics, recommendations);

            // Update strategy rankings
            updateStrategyRanking(agent, successRate, avgConfidence);
        }

        // Sort by priority (highest first)
        recommendations.sort(Comparator.comparingDouble(LearningRecommendation::priority).reversed());

        if (recommendations.isEmpty()) {
            dialogue.say("All agents performing within acceptable bounds. No improvements needed right now.", DialogueStyle.OutputMode.CHAT);
        } else {
            dialogue.say("Found " + recommendations.size() + " improvement opportunities.", DialogueStyle.OutputMode.CHAT);
        }

        return recommendations;
    }

    /**
     * Determines whether now is a good time to propose improvements.
     *
     * @return true if the learning engine recommends proposing improvements
     */
    public boolean shouldProposeImprovements() {
        if (metrics.getTotalCount() < MIN_SAMPLES) return false;

        List<LearningRecommendation> recs = analyze();
        // Only propose if there are high-priority recommendations
        return recs.stream().anyMatch(r -> r.priority() > 0.3);
    }

    /**
     * Returns the strategy rankings for all known agents.
     */
    public Map<String, StrategyRanking> getStrategyRankings() {
        return Collections.unmodifiableMap(strategyRankings);
    }

    /**
     * Returns a text summary of the learning engine state,
     * suitable for feeding to the ReflectionAgent.
     */
    public String getLearningReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== TARS Learning Engine Report ===\n\n");

        sb.append("Strategy Rankings:\n");
        if (strategyRankings.isEmpty()) {
            sb.append("  No rankings yet.\n");
        } else {
            strategyRankings.entrySet().stream()
                    .sorted(Comparator.comparingDouble(e -> -e.getValue().score()))
                    .forEach(e -> sb.append(String.format("  %s: score=%.2f (samples=%d)\n",
                            e.getKey(), e.getValue().score(), e.getValue().sampleCount())));
        }

        sb.append("\n").append(metrics.getSummary());
        return sb.toString();
    }

    private void checkLatencyTrend(String agent, List<ObservationMetrics.TaskMetric> agentMetrics,
                                   List<LearningRecommendation> recommendations) {
        if (agentMetrics.size() < MIN_SAMPLES) return;

        int midpoint = agentMetrics.size() / 2;
        double earlyAvg = agentMetrics.subList(0, midpoint).stream()
                .mapToLong(ObservationMetrics.TaskMetric::latencyMs).average().orElse(0);
        double recentAvg = agentMetrics.subList(midpoint, agentMetrics.size()).stream()
                .mapToLong(ObservationMetrics.TaskMetric::latencyMs).average().orElse(0);

        if (earlyAvg > 0 && recentAvg > earlyAvg * LATENCY_SPIKE_FACTOR) {
            recommendations.add(new LearningRecommendation(
                    agent,
                    RecommendationType.OPTIMIZE_PERFORMANCE,
                    String.format("Agent '%s' latency trending up: %.0fms -> %.0fms (%.0f%% increase). "
                            + "Consider optimizing prompts or container config.",
                            agent, earlyAvg, recentAvg,
                            (recentAvg - earlyAvg) / earlyAvg * 100),
                    0.5
            ));
        }
    }

    private void updateStrategyRanking(String agent, double successRate, double avgConfidence) {
        double score = (successRate * 0.6) + (avgConfidence * 0.4);
        int sampleCount = (int) metrics.getAll().stream()
                .filter(m -> m.agentName().equals(agent)).count();
        strategyRankings.put(agent, new StrategyRanking(agent, score, sampleCount));
    }

    public record LearningRecommendation(
            String agentName,
            RecommendationType type,
            String description,
            double priority
    ) {}

    public record StrategyRanking(
            String agentName,
            double score,
            int sampleCount
    ) {}

    public enum RecommendationType {
        IMPROVE_RELIABILITY,
        IMPROVE_QUALITY,
        OPTIMIZE_PERFORMANCE
    }
}
