package com.tarsv2.memory;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A single entry in the TARS memory system.
 *
 * <p>Each entry tracks relevance, confidence, reinforcement count,
 * access time, and lifecycle state for decay management.</p>
 */
public final class MemoryEntry {

    private final String id;
    private final MemoryType type;
    private final String key;
    private final String content;
    private final Instant createdAt;
    private double relevanceScore;
    private double confidenceScore;
    private int reinforcementCount;
    private Instant lastAccessed;
    private LifecycleState lifecycleState;
    private int version;

    public MemoryEntry(MemoryType type, String key, String content,
                        double relevanceScore, double confidenceScore) {
        this.id = UUID.randomUUID().toString().substring(0, 10);
        this.type = Objects.requireNonNull(type);
        this.key = Objects.requireNonNull(key);
        this.content = Objects.requireNonNull(content);
        this.createdAt = Instant.now();
        this.relevanceScore = relevanceScore;
        this.confidenceScore = confidenceScore;
        this.reinforcementCount = 0;
        this.lastAccessed = Instant.now();
        this.lifecycleState = LifecycleState.ACTIVE;
        this.version = 1;
    }

    // ── Accessors ────────────────────────────────────────────────

    public String getId() { return id; }
    public MemoryType getType() { return type; }
    public String getKey() { return key; }
    public String getContent() { return content; }
    public Instant getCreatedAt() { return createdAt; }
    public double getRelevanceScore() { return relevanceScore; }
    public double getConfidenceScore() { return confidenceScore; }
    public int getReinforcementCount() { return reinforcementCount; }
    public Instant getLastAccessed() { return lastAccessed; }
    public LifecycleState getLifecycleState() { return lifecycleState; }
    public int getVersion() { return version; }

    // ── Mutation (package-private) ───────────────────────────────

    void markAccessed() {
        this.lastAccessed = Instant.now();
    }

    void reinforce() {
        this.reinforcementCount++;
        this.relevanceScore = Math.min(1.0, this.relevanceScore + 0.05);
    }

    void decayRelevance(double amount) {
        this.relevanceScore = Math.max(0.0, this.relevanceScore - amount);
    }

    void transitionState(LifecycleState newState) {
        this.lifecycleState = newState;
    }

    void bumpVersion() {
        this.version++;
    }

    boolean isRetrievable() {
        return lifecycleState == LifecycleState.ACTIVE;
    }

    @Override
    public String toString() {
        return String.format("Memory[%s | %s | %s | rel=%.2f | conf=%.2f | v%d | %s]",
                id, type, key, relevanceScore, confidenceScore, version, lifecycleState);
    }
}
