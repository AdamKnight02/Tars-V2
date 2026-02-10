package com.tarsv2.approval;

/**
 * Outcome of a human approval decision.
 *
 * <p>Every proposed change must receive one of these statuses
 * before it can proceed (or be discarded).</p>
 */
public enum ApprovalStatus {

    /** The proposal is waiting for human review. */
    PENDING,

    /** The human approved the change — it may be merged. */
    APPROVED,

    /** The human rejected the change — it must be discarded. */
    REJECTED,

    /** The proposal expired without a decision (configurable timeout). */
    EXPIRED
}
