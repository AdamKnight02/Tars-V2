package com.tarsv2.sandbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class SandboxGitService {

    private static final Logger log = LoggerFactory.getLogger(SandboxGitService.class);
    private final Path repositoryRoot;

    public SandboxGitService(Path repositoryRoot) {
        this.repositoryRoot = repositoryRoot;
    }

    public boolean applyPatch(String diff) {
        try {
            Path patchFile = Files.createTempFile("tars-patch-", ".diff");
            Files.writeString(patchFile, diff);
            int code = run(List.of("git", "apply", patchFile.toString()));
            Files.deleteIfExists(patchFile);
            return code == 0;
        } catch (Exception e) {
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
