package com.tarsv2.agent;

import java.time.Instant;
import java.util.Objects;

/**
 * Captures the outcome of an agent execution.
 *
 * @param agentName  which agent produced this result
 * @param success    whether the task completed successfully
 * @param output     the result data (parsed resume, analysis, etc.)
 * @param summary    human-readable summary
 * @param timestamp  when the result was produced
 */
public record AgentResult(
        String agentName,
        boolean success,
        String output,
        String summary,
        Instant timestamp
) {
    public AgentResult {
        Objects.requireNonNull(agentName);
        Objects.requireNonNull(output);
        Objects.requireNonNull(summary);
        if (timestamp == null) {
            timestamp = Instant.now();
        }
    }

    /**
     * Convenience factory for a successful result.
     */
    public static AgentResult success(String agentName, String output, String summary) {
        return new AgentResult(agentName, true, output, summary, Instant.now());
    }

    /**
     * Convenience factory for a failed result.
     */
    public static AgentResult failure(String agentName, String error) {
        return new AgentResult(agentName, false, "", error, Instant.now());
    }
}
