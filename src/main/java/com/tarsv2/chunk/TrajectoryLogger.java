package com.tarsv2.chunk;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Logs full chunk trajectories for future training use.
 *
 * <p>Each trajectory is a complete record of plan → actions → CI → approval.
 * Stored as JSON-lines for easy batch processing.</p>
 */
public final class TrajectoryLogger {

    private static final Logger log = LoggerFactory.getLogger(TrajectoryLogger.class);
    private static final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private static final int MAX_IN_MEMORY = 200;

    private final Deque<Chunk> trajectories = new ConcurrentLinkedDeque<>();
    private final Path logPath;

    public TrajectoryLogger(Path logPath) {
        this.logPath = logPath;
    }

    /**
     * Records a scored chunk trajectory.
     */
    public void record(Chunk chunk) {
        trajectories.addLast(chunk);
        while (trajectories.size() > MAX_IN_MEMORY) {
            trajectories.removeFirst();
        }

        // Append to disk as JSON-line
        if (logPath != null) {
            try {
                Files.createDirectories(logPath.getParent());
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("id", chunk.getId());
                entry.put("plan", chunk.getPlan());
                entry.put("actions", chunk.getActions());
                entry.put("ciOutcome", chunk.getCiOutcome());
                entry.put("approvalOutcome", chunk.getApprovalOutcome());
                entry.put("score", chunk.getScore());
                entry.put("timestamp", chunk.getCreatedAt());

                String json = mapper.writeValueAsString(entry) + "\n";
                Files.writeString(logPath, json,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e) {
                log.warn("Failed to persist trajectory: {}", e.getMessage());
            }
        }

        log.info("Trajectory recorded: {} (score={})", chunk.getId(), chunk.getScore());
    }

    /**
     * Returns recent trajectories.
     */
    public List<Chunk> getRecent(int count) {
        List<Chunk> all = new ArrayList<>(trajectories);
        int start = Math.max(0, all.size() - count);
        return all.subList(start, all.size());
    }

    /**
     * Returns total trajectory count.
     */
    public int getCount() {
        return trajectories.size();
    }
}
