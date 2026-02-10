package com.tarsv2.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * ╔══════════════════════════════════════════════════════════════════╗
 * ║                    IMMUTABLE — DO NOT MODIFY                     ║
 * ║                                                                  ║
 * ║  Enforces rules about which branches and paths may be written.   ║
 * ║  Production branches are ALWAYS read-only to TARS.               ║
 * ╚══════════════════════════════════════════════════════════════════╝
 */
public final class ChangeControl {

    private static final Logger log = LoggerFactory.getLogger(ChangeControl.class);

    private static final Set<String> PROTECTED_BRANCHES = Set.of(
            "main", "master", "production", "release"
    );

    private static final String PROPOSAL_BRANCH_PREFIX = "tars/proposal-";

    private ChangeControl() {}

    /**
     * Checks whether TARS is allowed to write to a given branch.
     *
     * @param branchName the branch name to validate
     * @return true if TARS may create/write to this branch
     */
    public static boolean isWritableByTars(String branchName) {
        if (branchName == null || branchName.isBlank()) {
            return false;
        }
        if (PROTECTED_BRANCHES.contains(branchName.toLowerCase())) {
            log.warn("BLOCKED: Write attempt to protected branch '{}'", branchName);
            return false;
        }
        return branchName.startsWith(PROPOSAL_BRANCH_PREFIX);
    }

    /**
     * Generates a valid proposal branch name.
     *
     * @param proposalId the proposal identifier
     * @return a branch name safe for TARS to write to
     */
    public static String proposalBranchName(String proposalId) {
        return PROPOSAL_BRANCH_PREFIX + proposalId;
    }

    /**
     * Returns the set of protected branch names.
     */
    public static Set<String> getProtectedBranches() {
        return PROTECTED_BRANCHES;
    }
}
