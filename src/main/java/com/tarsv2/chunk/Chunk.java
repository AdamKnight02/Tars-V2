package com.tarsv2.chunk;

import java.time.Instant;
import java.util.*;

/**
 * An ALE-style learning chunk: (plan → actions → CI → approval).
 *
 * <p>Each chunk captures a complete trajectory from planning through
 * execution to outcome. Chunks are scored and used to update strategy
 * weights — no RL training, only statistical reinforcement.</p>
 */
public final class Chunk {

    private final String id;
    private final String plan;
    private final List<ChunkAction> actions;
    private final ChunkOutcome ciOutcome;
    private final ChunkOutcome approvalOutcome;
    private final Instant createdAt;
    private double score;

    public Chunk(String plan, List<ChunkAction> actions,
                 ChunkOutcome ciOutcome, ChunkOutcome approvalOutcome) {
        this.id = UUID.randomUUID().toString().substring(0, 10);
        this.plan = Objects.requireNonNull(plan);
        this.actions = List.copyOf(actions);
        this.ciOutcome = Objects.requireNonNull(ciOutcome);
        this.approvalOutcome = Objects.requireNonNull(approvalOutcome);
        this.createdAt = Instant.now();
        this.score = 0.0;
    }

    public String getId() { return id; }
    public String getPlan() { return plan; }
    public List<ChunkAction> getActions() { return actions; }
    public ChunkOutcome getCiOutcome() { return ciOutcome; }
    public ChunkOutcome getApprovalOutcome() { return approvalOutcome; }
    public Instant getCreatedAt() { return createdAt; }
    public double getScore() { return score; }
    void setScore(double score) { this.score = score; }

    /**
     * Returns a compact trajectory description for logging.
     */
    public String toTrajectory() {
        StringBuilder sb = new StringBuilder();
        sb.append("Chunk[").append(id).append("]\n");
        sb.append("  Plan: ").append(truncate(plan, 80)).append("\n");
        sb.append("  Actions: ").append(actions.size()).append("\n");
        for (ChunkAction a : actions) {
            sb.append("    - ").append(a.type()).append(": ")
                    .append(truncate(a.description(), 60)).append("\n");
        }
        sb.append("  CI: ").append(ciOutcome).append("\n");
        sb.append("  Approval: ").append(approvalOutcome).append("\n");
        sb.append("  Score: ").append(String.format("%.3f", score)).append("\n");
        return sb.toString();
    }

    private String truncate(String s, int max) {
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }

    /**
     * A single action within a chunk trajectory.
     */
    public record ChunkAction(
            String type,
            String description,
            int filesChanged,
            int linesChanged,
            double estimatedCost
    ) {}

    /**
     * Outcome of a chunk phase (CI or approval).
     */
    public enum ChunkOutcome {
        SUCCESS, FAILURE, PENDING, SKIPPED
    }
}
