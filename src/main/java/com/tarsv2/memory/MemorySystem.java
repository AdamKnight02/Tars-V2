package com.tarsv2.memory;

import com.tarsv2.personality.DialogueStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * TARS memory system with typed entries and decay.
 *
 * <p>Memory types: EPISODIC, PROCEDURAL, SEMANTIC, PREFERENCE.
 * Each type has distinct decay rules. Only ACTIVE memories are retrievable.</p>
 *
 * <p>Decay runs on schedule, on rejection events, and on CI failure events.</p>
 */
public final class MemorySystem {

    private static final Logger log = LoggerFactory.getLogger(MemorySystem.class);

    /** Below this relevance threshold, episodic memories get summarized. */
    private static final double SUMMARIZE_THRESHOLD = 0.3;
    /** Below this threshold, memories get archived. */
    private static final double ARCHIVE_THRESHOLD = 0.1;
    /** Below this threshold, memories get deleted. */
    private static final double DELETE_THRESHOLD = 0.02;

    private final Map<String, MemoryEntry> store = new ConcurrentHashMap<>();
    private final DialogueStyle dialogue;

    public MemorySystem(DialogueStyle dialogue) {
        this.dialogue = dialogue;
    }

    /**
     * Stores a new memory entry.
     */
    public String store(MemoryType type, String key, String content,
                         double relevance, double confidence) {
        MemoryEntry entry = new MemoryEntry(type, key, content, relevance, confidence);

        // For SEMANTIC type, replace existing with same key (versioned)
        if (type == MemoryType.SEMANTIC) {
            Optional<MemoryEntry> existing = findByKey(key);
            if (existing.isPresent()) {
                existing.get().transitionState(LifecycleState.ARCHIVED);
                entry.bumpVersion();
                log.info("Semantic memory '{}' replaced: v{} → v{}",
                        key, existing.get().getVersion(), entry.getVersion());
            }
        }

        store.put(entry.getId(), entry);
        log.debug("Memory stored: {}", entry);
        return entry.getId();
    }

    /**
     * Retrieves a memory by ID. Only ACTIVE memories are returned.
     */
    public Optional<MemoryEntry> retrieve(String id) {
        MemoryEntry entry = store.get(id);
        if (entry != null && entry.isRetrievable()) {
            entry.markAccessed();
            return Optional.of(entry);
        }
        return Optional.empty();
    }

    /**
     * Searches for memories by key prefix. Only ACTIVE memories returned.
     */
    public List<MemoryEntry> search(String keyPrefix) {
        return store.values().stream()
                .filter(MemoryEntry::isRetrievable)
                .filter(e -> e.getKey().startsWith(keyPrefix))
                .sorted(Comparator.comparingDouble(MemoryEntry::getRelevanceScore).reversed())
                .peek(MemoryEntry::markAccessed)
                .collect(Collectors.toList());
    }

    /**
     * Searches for memories by type. Only ACTIVE memories returned.
     */
    public List<MemoryEntry> searchByType(MemoryType type) {
        return store.values().stream()
                .filter(MemoryEntry::isRetrievable)
                .filter(e -> e.getType() == type)
                .sorted(Comparator.comparingDouble(MemoryEntry::getRelevanceScore).reversed())
                .collect(Collectors.toList());
    }

    /**
     * Reinforces a memory — increases relevance and reinforcement count.
     */
    public void reinforce(String id) {
        MemoryEntry entry = store.get(id);
        if (entry != null && entry.isRetrievable()) {
            entry.reinforce();
            log.debug("Memory reinforced: {}", entry);
        }
    }

    /**
     * Runs the decay cycle across all memories.
     *
     * <p>Decay rules:</p>
     * <ul>
     *   <li>EPISODIC: fast decay → summarize → archive → delete</li>
     *   <li>PROCEDURAL: decay when contradicted (via explicit contradiction call)</li>
     *   <li>SEMANTIC: no time-based decay (versioned replacement only)</li>
     *   <li>PREFERENCE: medium decay rate</li>
     * </ul>
     */
    public DecayReport runDecayCycle() {
        int summarized = 0, archived = 0, deleted = 0;

        for (MemoryEntry entry : store.values()) {
            if (entry.getLifecycleState() == LifecycleState.DELETED) continue;

            // SEMANTIC never time-decays
            if (entry.getType() == MemoryType.SEMANTIC) continue;

            // Apply time-based decay
            double decayRate = entry.getType().getDecayRate();
            long hoursSinceAccess = Duration.between(
                    entry.getLastAccessed(), Instant.now()).toHours();
            double decayAmount = decayRate * hoursSinceAccess;
            entry.decayRelevance(decayAmount);

            // EPISODIC lifecycle transitions
            if (entry.getType() == MemoryType.EPISODIC) {
                if (entry.getRelevanceScore() < DELETE_THRESHOLD
                        && entry.getLifecycleState() != LifecycleState.DELETED) {
                    entry.transitionState(LifecycleState.DELETED);
                    deleted++;
                } else if (entry.getRelevanceScore() < ARCHIVE_THRESHOLD
                        && entry.getLifecycleState() == LifecycleState.SUMMARIZED) {
                    entry.transitionState(LifecycleState.ARCHIVED);
                    archived++;
                } else if (entry.getRelevanceScore() < SUMMARIZE_THRESHOLD
                        && entry.getLifecycleState() == LifecycleState.ACTIVE) {
                    entry.transitionState(LifecycleState.SUMMARIZED);
                    summarized++;
                }
            }

            // PREFERENCE decay — archive if very low
            if (entry.getType() == MemoryType.PREFERENCE
                    && entry.getRelevanceScore() < ARCHIVE_THRESHOLD) {
                entry.transitionState(LifecycleState.ARCHIVED);
                archived++;
            }
        }

        DecayReport report = new DecayReport(summarized, archived, deleted, getActiveCount());
        if (summarized + archived + deleted > 0) {
            log.info("Decay cycle: {}", report);
            dialogue.say("Memory decay: " + report.toSummary(), DialogueStyle.OutputMode.CHAT);
        }
        return report;
    }

    /**
     * Triggers decay on rejection — accelerates decay for related memories.
     */
    public void onRejection(String relatedKey) {
        store.values().stream()
                .filter(MemoryEntry::isRetrievable)
                .filter(e -> e.getKey().contains(relatedKey))
                .forEach(e -> {
                    e.decayRelevance(0.15);
                    log.info("Rejection-accelerated decay for: {}", e);
                });
    }

    /**
     * Triggers decay on CI failure — reduces confidence for related memories.
     */
    public void onCIFailure(String relatedKey) {
        store.values().stream()
                .filter(MemoryEntry::isRetrievable)
                .filter(e -> e.getKey().contains(relatedKey))
                .forEach(e -> {
                    e.decayRelevance(0.10);
                    log.info("CI-failure decay for: {}", e);
                });
    }

    /**
     * Contradicts a procedural memory — marks it for decay.
     */
    public void contradict(String id) {
        MemoryEntry entry = store.get(id);
        if (entry != null && entry.getType() == MemoryType.PROCEDURAL) {
            entry.decayRelevance(0.25);
            if (entry.getRelevanceScore() < ARCHIVE_THRESHOLD) {
                entry.transitionState(LifecycleState.ARCHIVED);
            }
            log.info("Procedural memory contradicted: {}", entry);
        }
    }

    /**
     * Returns count of active (retrievable) memories.
     */
    public int getActiveCount() {
        return (int) store.values().stream()
                .filter(MemoryEntry::isRetrievable)
                .count();
    }

    /**
     * Returns a summary of the memory system state.
     */
    public String getSummary() {
        Map<MemoryType, Long> byType = store.values().stream()
                .filter(MemoryEntry::isRetrievable)
                .collect(Collectors.groupingBy(MemoryEntry::getType, Collectors.counting()));

        StringBuilder sb = new StringBuilder();
        sb.append("=== TARS Memory System ===\n");
        sb.append(String.format("Active memories: %d / %d total\n", getActiveCount(), store.size()));
        byType.forEach((type, count) ->
                sb.append(String.format("  %s: %d\n", type, count)));
        return sb.toString();
    }

    private Optional<MemoryEntry> findByKey(String key) {
        return store.values().stream()
                .filter(e -> e.getKey().equals(key) && e.isRetrievable())
                .findFirst();
    }

    /**
     * Report from a decay cycle.
     */
    public record DecayReport(int summarized, int archived, int deleted, int activeRemaining) {
        public String toSummary() {
            return String.format("summarized=%d, archived=%d, deleted=%d, active=%d",
                    summarized, archived, deleted, activeRemaining);
        }
    }
}
