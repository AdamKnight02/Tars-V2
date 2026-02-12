package com.tarsv2.memory;

/**
 * Lifecycle state of a memory entry.
 *
 * <p>Only ACTIVE memories are retrievable. Other states exist
 * for decay management and eventual cleanup.</p>
 */
public enum LifecycleState {

    /** Memory is active and retrievable. */
    ACTIVE,

    /** Memory has been summarized (episodic only). */
    SUMMARIZED,

    /** Memory has been archived — no longer retrievable. */
    ARCHIVED,

    /** Memory has been deleted — awaiting garbage collection. */
    DELETED
}
