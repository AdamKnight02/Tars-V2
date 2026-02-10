package com.tarsv2.approval;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ╔══════════════════════════════════════════════════════════════════╗
 * ║                    IMMUTABLE APPROVAL GATE                      ║
 * ║                                                                  ║
 * ║  This class enforces mandatory human approval for ALL changes.   ║
 * ║  It is a HARD SAFETY BOUNDARY.                                   ║
 * ║                                                                  ║
 * ║  RULES:                                                          ║
 * ║  1. No change may bypass this gate.                              ║
 * ║  2. No auto-approval logic may be added.                         ║
 * ║  3. This class must NEVER be modified by TARS itself.            ║
 * ║  4. All proposals require explicit human APPROVED status.        ║
 * ║                                                                  ║
 * ║  Modifying this class is a violation of the safety contract.     ║
 * ╚══════════════════════════════════════════════════════════════════╝
 *
 * <p>The ApprovalGate is the single checkpoint through which every
 * code change, configuration update, or self-improvement proposal
 * must pass. It stores proposals and exposes them for human review.</p>
 */
public final class ApprovalGate {

    private static final Logger log = LoggerFactory.getLogger(ApprovalGate.class);

    /**
     * In-memory proposal store. In production, this would be backed by
     * a persistent store (database, file-based queue, etc.).
     */
    private final Map<String, ChangeProposal> proposals = new ConcurrentHashMap<>();

    /**
     * Submits a new change proposal for human review.
     *
     * @param proposal the proposed change
     * @return the proposal ID for tracking
     */
    public String submit(ChangeProposal proposal) {
        proposals.put(proposal.getId(), proposal);
        log.info("Proposal submitted for review: {}", proposal);
        return proposal.getId();
    }

    /**
     * Returns a proposal by ID if it exists.
     *
     * @param proposalId the proposal identifier
     * @return the proposal, or empty if not found
     */
    public Optional<ChangeProposal> getProposal(String proposalId) {
        return Optional.ofNullable(proposals.get(proposalId));
    }

    /**
     * Returns all proposals currently awaiting review.
     *
     * @return list of pending proposals
     */
    public List<ChangeProposal> getPendingProposals() {
        return proposals.values().stream()
                .filter(p -> p.getStatus() == ApprovalStatus.PENDING)
                .toList();
    }

    /**
     * Human approves a proposal. This is the ONLY path to approval.
     *
     * <p><strong>SAFETY: This method must only be called by a human-initiated
     * action (CLI input, API call from authenticated user, etc.).
     * Automated callers must NEVER invoke this.</strong></p>
     *
     * @param proposalId the proposal to approve
     * @return true if the proposal was found and approved
     */
    public boolean approve(String proposalId) {
        return transition(proposalId, ApprovalStatus.APPROVED);
    }

    /**
     * Human rejects a proposal. The associated change is discarded.
     *
     * @param proposalId the proposal to reject
     * @return true if the proposal was found and rejected
     */
    public boolean reject(String proposalId) {
        return transition(proposalId, ApprovalStatus.REJECTED);
    }

    /**
     * Checks whether a proposal has been approved.
     *
     * <p>This is the ONLY way any downstream system should check
     * whether it is safe to proceed with a change.</p>
     *
     * @param proposalId the proposal identifier
     * @return true ONLY if the proposal exists AND is APPROVED
     */
    public boolean isApproved(String proposalId) {
        return getProposal(proposalId)
                .map(p -> p.getStatus() == ApprovalStatus.APPROVED)
                .orElse(false);
    }

    private boolean transition(String proposalId, ApprovalStatus newStatus) {
        ChangeProposal proposal = proposals.get(proposalId);
        if (proposal == null) {
            log.warn("Attempted to transition unknown proposal: {}", proposalId);
            return false;
        }
        if (proposal.getStatus() != ApprovalStatus.PENDING) {
            log.warn("Proposal {} is already {}, cannot transition to {}",
                    proposalId, proposal.getStatus(), newStatus);
            return false;
        }
        proposal.setStatus(newStatus);
        log.info("Proposal {} transitioned to {}", proposalId, newStatus);
        return true;
    }
}
