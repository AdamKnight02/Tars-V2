package com.tarsv2.sandbox;

import com.tarsv2.approval.ApprovalGate;
import com.tarsv2.approval.ApprovalStatus;
import com.tarsv2.approval.ChangeProposal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Manages a Git-based staging workflow for proposed changes.
 *
 * <p>Flow:</p>
 * <ol>
 *   <li>Agent writes changes into the sandbox</li>
 *   <li>GitStagingService creates a diff and packages it as a {@link ChangeProposal}</li>
 *   <li>Proposal is submitted to the {@link ApprovalGate}</li>
 *   <li>If approved, changes are merged into the target branch</li>
 *   <li>If rejected, the staging branch is deleted</li>
 * </ol>
 *
 * <p>TODO: Integrate with JGit for real Git operations. Current implementation
 * uses file-based diff simulation for the scaffold.</p>
 */
public final class GitStagingService {

    private static final Logger log = LoggerFactory.getLogger(GitStagingService.class);

    private final SandboxEnvironment sandbox;
    private final ApprovalGate approvalGate;

    /**
     * @param sandbox      the sandbox where changes are staged
     * @param approvalGate the approval gate for submitting proposals
     */
    public GitStagingService(SandboxEnvironment sandbox, ApprovalGate approvalGate) {
        this.sandbox = Objects.requireNonNull(sandbox);
        this.approvalGate = Objects.requireNonNull(approvalGate);
    }

    /**
     * Stages a file change and submits it for approval.
     *
     * @param relativePath path relative to sandbox root
     * @param content      the new file content
     * @param rationale    why this change is being proposed
     * @return the proposal ID for tracking
     * @throws IOException if file writing fails
     */
    public String stageAndPropose(String relativePath, String content, String rationale)
            throws IOException {

        Path fullPath = sandbox.getRoot().resolve(relativePath);
        Files.createDirectories(fullPath.getParent());

        // Capture existing content as "before" for diff
        String before = Files.exists(fullPath) ? Files.readString(fullPath) : "<new file>";
        Files.writeString(fullPath, content);
        String after = content;

        // TODO: Generate proper unified diff using JGit
        String diff = String.format("--- a/%s\n+++ b/%s\n@@ change @@\n-%s\n+%s",
                relativePath, relativePath,
                truncate(before, 200), truncate(after, 200));

        ChangeProposal proposal = new ChangeProposal(
                "Modify " + relativePath,
                diff,
                rationale
        );

        String id = approvalGate.submit(proposal);
        log.info("Staged change for '{}', proposal ID: {}", relativePath, id);
        return id;
    }

    /**
     * Merges an approved proposal's changes. Only works if the proposal
     * has been approved through the {@link ApprovalGate}.
     *
     * @param proposalId the approved proposal's ID
     * @return true if merge succeeded
     */
    public boolean mergeIfApproved(String proposalId) {
        if (!approvalGate.isApproved(proposalId)) {
            log.warn("Cannot merge proposal {} — not approved (status: {})",
                    proposalId,
                    approvalGate.getProposal(proposalId)
                            .map(p -> p.getStatus().toString())
                            .orElse("NOT_FOUND"));
            return false;
        }

        // TODO: Perform actual git merge using JGit
        log.info("Proposal {} approved — merging changes into target branch.", proposalId);
        return true;
    }

    private static String truncate(String s, int maxLen) {
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}
