package com.tarsv2.chunk;

import com.tarsv2.personality.DialogueStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * ALE-style chunk learning engine.
 *
 * <p>Processes complete chunks (plan → actions → CI → approval),
 * scores them, updates strategy weights, and logs trajectories
 * for future training use.</p>
 *
 * <p>No RL training occurs here — only statistical reinforcement
 * based on observed outcomes.</p>
 */
public final class ChunkLearningEngine {

    private static final Logger log = LoggerFactory.getLogger(ChunkLearningEngine.class);

    private final ChunkScorer scorer;
    private final StrategyWeights weights;
    private final TrajectoryLogger trajectoryLogger;
    private final DialogueStyle dialogue;

    public ChunkLearningEngine(DialogueStyle dialogue, Path trajectoryLogPath) {
        this.scorer = new ChunkScorer();
        this.weights = new StrategyWeights();
        this.trajectoryLogger = new TrajectoryLogger(trajectoryLogPath);
        this.dialogue = Objects.requireNonNull(dialogue);
    }

    /**
     * Processes a completed chunk: score, update weights, log trajectory.
     */
    public double processChunk(Chunk chunk) {
        // 1. Score the chunk
        double score = scorer.score(chunk);

        // 2. Update strategy weights based on score
        weights.update(chunk);

        // 3. Log the full trajectory
        trajectoryLogger.record(chunk);

        dialogue.say(String.format("Chunk %s processed: score=%.3f | strategies updated",
                chunk.getId(), score));

        log.info("Chunk learning complete: {} score={}", chunk.getId(), score);
        return score;
    }

    /**
     * Returns current strategy weights for decision-making.
     */
    public StrategyWeights getWeights() {
        return weights;
    }

    /**
     * Returns recent trajectories for analysis.
     */
    public List<Chunk> getRecentTrajectories(int count) {
        return trajectoryLogger.getRecent(count);
    }

    /**
     * Returns a learning summary.
     */
    public String getSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Chunk Learning Engine ===\n");
        sb.append(String.format("Trajectories logged: %d\n", trajectoryLogger.getCount()));
        sb.append(weights.getSummary());
        return sb.toString();
    }
}
