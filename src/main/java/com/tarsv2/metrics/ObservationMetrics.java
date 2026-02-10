package com.tarsv2.metrics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.stream.Collectors;

/**
 * Collects and stores observation metrics for TARS task execution.
 *
 * <p>Tracks task success/failure, latency, and confidence scores.
 * Persists metrics to disk for survival across restarts.
 * Exposes aggregated data to the ReflectionAgent and LearningEngine.</p>
 */
public final class ObservationMetrics {

    private static final Logger log = LoggerFactory.getLogger(ObservationMetrics.class);
    private static final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private static final int MAX_HISTORY = 1000;

    private final Deque<TaskMetric> history = new ConcurrentLinkedDeque<>();
    private final Path persistPath;

    public ObservationMetrics(Path persistPath) {
        this.persistPath = persistPath;
        loadFromDisk();
    }

    /**
     * Records a task execution metric.
     */
    public void record(String taskName, String agentName, boolean success,
                       Duration latency, double confidenceScore) {
        TaskMetric metric = new TaskMetric(
                taskName, agentName, success, latency.toMillis(),
                confidenceScore, Instant.now()
        );
        history.addLast(metric);

        // Trim old entries
        while (history.size() > MAX_HISTORY) {
            history.removeFirst();
        }

        log.info("Metric recorded: {} [{}] success={} latency={}ms confidence={}",
                taskName, agentName, success, latency.toMillis(), confidenceScore);
        saveToDisk();
    }

    /**
     * Returns the success rate for a given agent over its recent history.
     */
    public double getSuccessRate(String agentName) {
        List<TaskMetric> agentMetrics = history.stream()
                .filter(m -> m.agentName().equals(agentName))
                .toList();
        if (agentMetrics.isEmpty()) return 0.0;
        long successes = agentMetrics.stream().filter(TaskMetric::success).count();
        return (double) successes / agentMetrics.size();
    }

    /**
     * Returns average latency in milliseconds for a given agent.
     */
    public double getAverageLatency(String agentName) {
        return history.stream()
                .filter(m -> m.agentName().equals(agentName))
                .mapToLong(TaskMetric::latencyMs)
                .average()
                .orElse(0.0);
    }

    /**
     * Returns average confidence score for a given agent.
     */
    public double getAverageConfidence(String agentName) {
        return history.stream()
                .filter(m -> m.agentName().equals(agentName))
                .mapToDouble(TaskMetric::confidenceScore)
                .average()
                .orElse(0.0);
    }

    /**
     * Returns a summary of all metrics, suitable for feeding to the ReflectionAgent.
     */
    public String getSummary() {
        if (history.isEmpty()) {
            return "No metrics collected yet.";
        }

        Map<String, List<TaskMetric>> byAgent = history.stream()
                .collect(Collectors.groupingBy(TaskMetric::agentName));

        StringBuilder sb = new StringBuilder();
        sb.append("=== TARS Observation Metrics ===\n");
        sb.append(String.format("Total tasks recorded: %d\n\n", history.size()));

        for (var entry : byAgent.entrySet()) {
            String agent = entry.getKey();
            List<TaskMetric> metrics = entry.getValue();
            long successes = metrics.stream().filter(TaskMetric::success).count();
            double avgLatency = metrics.stream().mapToLong(TaskMetric::latencyMs).average().orElse(0);
            double avgConfidence = metrics.stream().mapToDouble(TaskMetric::confidenceScore).average().orElse(0);

            sb.append(String.format("Agent: %s\n", agent));
            sb.append(String.format("  Tasks: %d | Success: %d/%d (%.1f%%)\n",
                    metrics.size(), successes, metrics.size(),
                    (double) successes / metrics.size() * 100));
            sb.append(String.format("  Avg Latency: %.0fms | Avg Confidence: %.2f\n", avgLatency, avgConfidence));
            sb.append("\n");
        }

        return sb.toString();
    }

    /**
     * Returns all metrics as an unmodifiable list.
     */
    public List<TaskMetric> getAll() {
        return List.copyOf(history);
    }

    /**
     * Returns the count of total recorded tasks.
     */
    public int getTotalCount() {
        return history.size();
    }

    private void saveToDisk() {
        if (persistPath == null) return;
        try {
            Files.createDirectories(persistPath.getParent());
            String json = mapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(List.copyOf(history));
            Files.writeString(persistPath, json,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            log.warn("Failed to persist metrics: {}", e.getMessage());
        }
    }

    private void loadFromDisk() {
        if (persistPath == null || !Files.exists(persistPath)) return;
        try {
            String json = Files.readString(persistPath);
            TaskMetric[] loaded = mapper.readValue(json, TaskMetric[].class);
            for (TaskMetric m : loaded) {
                history.addLast(m);
            }
            log.info("Loaded {} metrics from disk", loaded.length);
        } catch (IOException e) {
            log.warn("Failed to load metrics from disk: {}", e.getMessage());
        }
    }

    /**
     * A single task execution metric record.
     */
    public record TaskMetric(
            String taskName,
            String agentName,
            boolean success,
            long latencyMs,
            double confidenceScore,
            Instant timestamp
    ) {}
}
