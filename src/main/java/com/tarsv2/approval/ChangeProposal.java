package com.tarsv2.approval;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * An immutable record of a proposed change awaiting human approval.
 *
 * <p>Change proposals capture what TARS wants to do, why, and the
 * diff or action description. They are created by the self-improvement
 * loop or by agent tasks that require code modifications.</p>
 *
 * <p><strong>SAFETY:</strong> Proposals are NEVER auto-approved.
 * The {@link ApprovalGate} enforces mandatory human review.</p>
 */
public final class ChangeProposal {

    private final String id;
    private final String description;
    private final String diff;
    private final String rationale;
    private final Instant createdAt;
    private volatile ApprovalStatus status;

    /**
     * @param description short human-readable summary of the change
     * @param diff        the actual diff or command to execute
     * @param rationale   why TARS is proposing this change
     */
    public ChangeProposal(String description, String diff, String rationale) {
        this(UUID.randomUUID().toString().substring(0, 8), description, diff, rationale);
    }

    /**
     * Creates a proposal with an explicit ID.
     *
     * @param id          unique proposal identifier
     * @param description short human-readable summary of the change
     * @param diff        the actual diff or command to execute
     * @param rationale   why TARS is proposing this change
     */
    public ChangeProposal(String id, String description, String diff, String rationale) {
        this.id = Objects.requireNonNull(id);
        this.description = Objects.requireNonNull(description);
        this.diff = Objects.requireNonNull(diff);
        this.rationale = Objects.requireNonNull(rationale);
        this.createdAt = Instant.now();
        this.status = ApprovalStatus.PENDING;
    }

    public String getId() { return id; }
    public String getDescription() { return description; }
    public String getDiff() { return diff; }
    public String getRationale() { return rationale; }
    public Instant getCreatedAt() { return createdAt; }
    public ApprovalStatus getStatus() { return status; }

    /**
     * Transitions the proposal to a new status.
     * <p><strong>Only {@link ApprovalGate} should call this.</strong></p>
     *
     * @param newStatus the new approval status
     */
    void setStatus(ApprovalStatus newStatus) {
        this.status = Objects.requireNonNull(newStatus);
    }

    @Override
    public String toString() {
        return String.format("ChangeProposal[%s | %s | %s] — %s", id, status, description, rationale);
    }
}
