package com.tarsv2.chunk;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Scores chunks based on outcome signals.
 *
 * <p>Scoring signals:</p>
 * <ul>
 *   <li>+success (CI pass, approval)</li>
 *   <li>-rejection (human rejected)</li>
 *   <li>-cost penalty (excessive token/compute usage)</li>
 * </ul>
 *
 * <p>No RL training — statistical reinforcement only.</p>
 */
public final class ChunkScorer {

    private static final Logger log = LoggerFactory.getLogger(ChunkScorer.class);

    /** Base reward for successful CI pass. */
    private static final double CI_SUCCESS_REWARD = 0.4;
    /** Base reward for human approval. */
    private static final double APPROVAL_REWARD = 0.5;
    /** Penalty for rejection. */
    private static final double REJECTION_PENALTY = -0.6;
    /** Penalty per unit of estimated cost. */
    private static final double COST_PENALTY_FACTOR = -0.1;
    /** Bonus for small diffs (< 50 lines). */
    private static final double SMALL_DIFF_BONUS = 0.1;
    /** Bonus for test-first approach. */
    private static final double TEST_FIRST_BONUS = 0.15;

    /**
     * Scores a chunk based on its outcomes and characteristics.
     */
    public double score(Chunk chunk) {
        double s = 0.0;

        // CI outcome
        if (chunk.getCiOutcome() == Chunk.ChunkOutcome.SUCCESS) {
            s += CI_SUCCESS_REWARD;
        } else if (chunk.getCiOutcome() == Chunk.ChunkOutcome.FAILURE) {
            s += REJECTION_PENALTY * 0.5;
        }

        // Approval outcome
        if (chunk.getApprovalOutcome() == Chunk.ChunkOutcome.SUCCESS) {
            s += APPROVAL_REWARD;
        } else if (chunk.getApprovalOutcome() == Chunk.ChunkOutcome.FAILURE) {
            s += REJECTION_PENALTY;
        }

        // Cost penalty
        double totalCost = chunk.getActions().stream()
                .mapToDouble(Chunk.ChunkAction::estimatedCost)
                .sum();
        s += totalCost * COST_PENALTY_FACTOR;

        // Small diff bonus
        int totalLines = chunk.getActions().stream()
                .mapToInt(Chunk.ChunkAction::linesChanged)
                .sum();
        if (totalLines > 0 && totalLines < 50) {
            s += SMALL_DIFF_BONUS;
        }

        // Test-first bonus
        boolean hasTestFirst = chunk.getActions().stream()
                .anyMatch(a -> a.type().contains("TEST"));
        if (hasTestFirst) {
            s += TEST_FIRST_BONUS;
        }

        // Clamp to [-1, 1]
        s = Math.max(-1.0, Math.min(1.0, s));

        chunk.setScore(s);
        log.debug("Chunk {} scored: {}", chunk.getId(), s);
        return s;
    }
}
