package com.tarsv2.sandbox;

import com.tarsv2.approval.ApprovalGate;
import com.tarsv2.approval.ChangeProposal;
import com.tarsv2.personality.DialogueStyle;
import com.tarsv2.security.ChangeControl;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Real JGit-based sandbox operations for staging proposed changes.
 *
 * <p>Implements Git branching, diffing, and revert logic using JGit.
 * All writes go to proposal branches only — never to protected branches.
 * Integrates with {@link ChangeControl} to enforce branch write rules
 * and with {@link ApprovalGate} for the approval flow.</p>
 */
public final class JGitSandboxService {

    private static final Logger log = LoggerFactory.getLogger(JGitSandboxService.class);

    private final SandboxEnvironment sandbox;
    private final ApprovalGate approvalGate;
    private final DialogueStyle dialogue;
    private Git git;

    public JGitSandboxService(SandboxEnvironment sandbox, ApprovalGate approvalGate, DialogueStyle dialogue) {
        this.sandbox = Objects.requireNonNull(sandbox);
        this.approvalGate = Objects.requireNonNull(approvalGate);
        this.dialogue = Objects.requireNonNull(dialogue);
    }

    /**
     * Initializes or opens a Git repository in the sandbox root.
     */
    public void initialize() throws IOException, GitAPIException {
        Path root = sandbox.getRoot();
        Path gitDir = root.resolve(".git");

        if (Files.exists(gitDir)) {
            git = Git.open(root.toFile());
            dialogue.say("Opened existing Git repo in sandbox.");
            log.info("Opened JGit repository at {}", root);
        } else {
            git = Git.init().setDirectory(root.toFile()).call();
            // Create initial commit so we have a HEAD to branch from
            Files.writeString(root.resolve(".tars-init"), "TARS sandbox initialized\n");
            git.add().addFilepattern(".tars-init").call();
            git.commit().setMessage("Initial sandbox commit").call();
            dialogue.say("Initialized new Git repo in sandbox.");
            log.info("Initialized JGit repository at {}", root);
        }
    }

    /**
     * Creates a proposal branch from the current HEAD, writes file content,
     * commits, generates a diff, and submits a ChangeProposal.
     *
     * @param relativePath path relative to sandbox root
     * @param content      the new file content
     * @param rationale    why this change is being proposed
     * @return the proposal ID
     */
    public String createProposalBranch(String relativePath, String content, String rationale)
            throws IOException, GitAPIException {

        String proposalId = java.util.UUID.randomUUID().toString().substring(0, 8);
        String branchName = ChangeControl.proposalBranchName(proposalId);

        // Validate branch is writable
        if (!ChangeControl.isWritableByTars(branchName)) {
            throw new SecurityException("TARS cannot write to branch: " + branchName);
        }

        dialogue.say("Creating proposal branch: " + branchName);

        // Record HEAD before changes
        RevCommit baseCommit = git.log().setMaxCount(1).call().iterator().next();

        // Create and checkout proposal branch
        git.checkout().setCreateBranch(true).setName(branchName).call();

        // Write the file
        Path fullPath = sandbox.getRoot().resolve(relativePath);
        Files.createDirectories(fullPath.getParent());
        Files.writeString(fullPath, content);

        // Stage and commit
        git.add().addFilepattern(relativePath).call();
        RevCommit proposalCommit = git.commit()
                .setMessage("TARS proposal: " + rationale)
                .call();

        // Generate diff
        String diff = generateDiff(baseCommit, proposalCommit);
        log.info(dialogue.narrate("Generated diff for proposal " + proposalId + " (" + diff.length() + " chars)"));

        // Switch back to main branch
        git.checkout().setName(getDefaultBranch()).call();

        // Submit proposal
        ChangeProposal proposal = new ChangeProposal(
                "Modify " + relativePath,
                diff,
                rationale
        );
        String id = approvalGate.submit(proposal);

        dialogue.say("Proposal " + id + " created on branch " + branchName);
        return id;
    }

    /**
     * Generates a unified diff between two commits.
     */
    public String generateDiff(RevCommit oldCommit, RevCommit newCommit) throws IOException {
        Repository repo = git.getRepository();
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try (ObjectReader reader = repo.newObjectReader();
             DiffFormatter formatter = new DiffFormatter(out)) {

            formatter.setRepository(repo);

            CanonicalTreeParser oldTree = new CanonicalTreeParser();
            oldTree.reset(reader, oldCommit.getTree());

            CanonicalTreeParser newTree = new CanonicalTreeParser();
            newTree.reset(reader, newCommit.getTree());

            List<DiffEntry> diffs = formatter.scan(oldTree, newTree);
            for (DiffEntry entry : diffs) {
                formatter.format(entry);
            }
            formatter.flush();
        }

        return out.toString();
    }

    /**
     * Reverts a proposal branch by deleting it.
     *
     * @param proposalId the proposal to revert
     */
    public void revertProposal(String proposalId) throws GitAPIException, IOException {
        String branchName = ChangeControl.proposalBranchName(proposalId);
        String currentBranch = git.getRepository().getBranch();

        // Don't delete the branch we're on
        if (branchName.equals(currentBranch)) {
            git.checkout().setName(getDefaultBranch()).call();
        }

        git.branchDelete().setBranchNames(branchName).setForce(true).call();
        dialogue.say("Reverted proposal " + proposalId + " — branch " + branchName + " deleted.");
        log.info("Deleted proposal branch: {}", branchName);
    }

    /**
     * Merges an approved proposal branch into the default branch.
     * Only proceeds if ApprovalGate confirms approval.
     */
    public boolean mergeIfApproved(String proposalId) throws IOException, GitAPIException {
        if (!approvalGate.isApproved(proposalId)) {
            log.warn("Cannot merge proposal {} — not approved", proposalId);
            dialogue.say("Merge blocked — proposal " + proposalId + " is not approved. I'll wait.");
            return false;
        }

        String branchName = ChangeControl.proposalBranchName(proposalId);

        // Ensure we're on the default branch
        git.checkout().setName(getDefaultBranch()).call();

        // Merge the proposal branch
        git.merge()
                .include(git.getRepository().resolve(branchName))
                .setMessage("Merge approved TARS proposal: " + proposalId)
                .call();

        // Clean up the proposal branch
        git.branchDelete().setBranchNames(branchName).setForce(true).call();

        dialogue.say("Proposal " + proposalId + " merged. Another successful collaboration.");
        log.info("Merged proposal {} from branch {}", proposalId, branchName);
        return true;
    }

    /**
     * Lists all current proposal branches.
     */
    public List<String> listProposalBranches() throws GitAPIException {
        return git.branchList().call().stream()
                .map(Ref::getName)
                .filter(name -> name.contains("tars/proposal-"))
                .toList();
    }

    private String getDefaultBranch() {
        try {
            Ref head = git.getRepository().findRef("main");
            if (head != null) return "main";
        } catch (IOException ignored) {}
        return "master";
    }

    public Git getGit() { return git; }
}
