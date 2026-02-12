package com.tarsv2.memory;

/**
 * Types of memory entries in the TARS memory system.
 *
 * <p>Each type has different decay characteristics:</p>
 * <ul>
 *   <li>EPISODIC — fast decay, summarize → archive → delete</li>
 *   <li>PROCEDURAL — decays when contradicted</li>
 *   <li>SEMANTIC — versioned replacement only, no time-based decay</li>
 *   <li>PREFERENCE — medium decay rate</li>
 * </ul>
 */
public enum MemoryType {

    /** Event-based memory: what happened, when, and context. Fast decay. */
    EPISODIC(0.05, "Fast decay → summarize → archive → delete"),

    /** How-to knowledge: patterns, workflows, procedures. Decays on contradiction. */
    PROCEDURAL(0.01, "Decays when contradicted by new evidence"),

    /** Factual knowledge: versioned, replaced not decayed. */
    SEMANTIC(0.0, "Versioned replacement only — no time-based decay"),

    /** User/system preferences. Medium decay rate. */
    PREFERENCE(0.02, "Medium decay rate");

    private final double decayRate;
    private final String decayRule;

    MemoryType(double decayRate, String decayRule) {
        this.decayRate = decayRate;
        this.decayRule = decayRule;
    }

    public double getDecayRate() { return decayRate; }
    public String getDecayRule() { return decayRule; }
}
