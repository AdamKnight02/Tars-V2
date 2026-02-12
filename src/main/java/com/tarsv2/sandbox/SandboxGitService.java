package com.tarsv2.sandbox;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class SandboxGitService {

    private static final Logger log = LoggerFactory.getLogger(SandboxGitService.class);
    private final Path repositoryRoot;

    public SandboxGitService(Path repositoryRoot) {
        this.repositoryRoot = repositoryRoot;
    }

    /**
     * Applies a unified diff patch using JGit, stages all modified files,
     * commits with a proposal message, and returns the commit hash.
     *
     * @param diff       the unified diff string
     * @param proposalId the proposal identifier for the commit message
     * @return the full commit hash
     * @throws RuntimeException if the patch cannot be applied
     */
    public String applyPatch(String diff, String proposalId) {
        Path patchFile = null;
        try (Git git = Git.open(repositoryRoot.toFile())) {
            // 1. Write the unified diff to a temporary .patch file
            patchFile = Files.createTempFile("tars-patch-", ".patch");
            Files.writeString(patchFile, diff, StandardCharsets.UTF_8);

            // 2. Apply the patch using JGit
            try (InputStream patchStream = new ByteArrayInputStream(
                    diff.getBytes(StandardCharsets.UTF_8))) {
                git.apply().setPatch(patchStream).call();
            }

            // 3. Stage all modified files
            git.add().addFilepattern(".").call();

            // 4. Commit with the proposal message
            RevCommit commit = git.commit()
                    .setMessage("TARS applied approved proposal " + proposalId)
                    .call();

            // 5. Return the real commit hash
            String commitHash = commit.getId().getName();
            log.info("Applied patch for proposal {} — commit {}", proposalId, commitHash);
            return commitHash;

        } catch (IOException | GitAPIException e) {
            throw new RuntimeException("Failed to apply patch for proposal " + proposalId, e);
        } finally {
            if (patchFile != null) {
                try {
                    Files.deleteIfExists(patchFile);
                } catch (IOException ignored) {
                    // best-effort cleanup
                }
            }
        }
    }

    /**
     * Backward-compatible overload that applies a patch without committing metadata.
     * Delegates to the JGit-based implementation with a generic proposal ID.
     */
    public boolean applyPatch(String diff) {
        try {
            applyPatch(diff, "unknown");
            return true;
        } catch (RuntimeException e) {
            log.warn("Failed to apply patch", e);
            return false;
        }
    }

    public boolean runTests() {
        try {
            int code = run(List.of("mvn", "test"));
            if (code != 0) {
                rollback();
                return false;
            }
            return true;
        } catch (Exception e) {
            rollback();
            return false;
        }
    }

    public void rollback() {
        try {
            run(List.of("git", "reset", "--hard", "HEAD"));
            run(List.of("git", "clean", "-fd"));
        } catch (Exception e) {
            log.warn("Rollback failed", e);
        }
    }

    public void commit(String message) {
        try {
            run(List.of("git", "add", "-A"));
            run(List.of("git", "commit", "-m", message));
        } catch (Exception e) {
            throw new IllegalStateException("Commit failed", e);
        }
    }

    private int run(List<String> command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command)
                .directory(repositoryRoot.toFile())
                .redirectErrorStream(true)
                .start();
        process.getInputStream().transferTo(System.out);
        return process.waitFor();
    }
}
