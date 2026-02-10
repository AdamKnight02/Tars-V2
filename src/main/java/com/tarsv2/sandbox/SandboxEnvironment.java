package com.tarsv2.sandbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Manages an isolated working directory where TARS stages changes
 * before proposing them for approval.
 *
 * <p>All agent-generated code, scraped data, and intermediate artifacts
 * live here. Nothing from the sandbox reaches production without passing
 * through the {@link com.tarsv2.approval.ApprovalGate}.</p>
 *
 * <p>The sandbox is ephemeral — it can be wiped and recreated at any time.</p>
 */
public final class SandboxEnvironment {

    private static final Logger log = LoggerFactory.getLogger(SandboxEnvironment.class);

    private final Path sandboxRoot;

    /**
     * @param sandboxRoot the root directory for sandbox operations
     */
    public SandboxEnvironment(Path sandboxRoot) {
        this.sandboxRoot = Objects.requireNonNull(sandboxRoot, "sandboxRoot");
    }

    /**
     * Initializes the sandbox directory, creating it if absent.
     *
     * @throws IOException if directory creation fails
     */
    public void initialize() throws IOException {
        if (!Files.exists(sandboxRoot)) {
            Files.createDirectories(sandboxRoot);
            log.info("Sandbox initialized at: {}", sandboxRoot);
        } else {
            log.info("Sandbox already exists at: {}", sandboxRoot);
        }
    }

    /**
     * Returns the root path of this sandbox.
     *
     * @return sandbox root directory
     */
    public Path getRoot() {
        return sandboxRoot;
    }

    /**
     * Creates a subdirectory within the sandbox for a specific task.
     *
     * @param taskName name of the task (used as directory name)
     * @return path to the created task directory
     * @throws IOException if creation fails
     */
    public Path createTaskDirectory(String taskName) throws IOException {
        Path taskDir = sandboxRoot.resolve(sanitizeName(taskName));
        Files.createDirectories(taskDir);
        log.info("Task directory created: {}", taskDir);
        return taskDir;
    }

    /**
     * Wipes the entire sandbox. Use with care — all staged work is lost.
     *
     * @throws IOException if cleanup fails
     */
    public void clean() throws IOException {
        if (Files.exists(sandboxRoot)) {
            try (Stream<Path> walk = Files.walk(sandboxRoot)) {
                walk.sorted(Comparator.reverseOrder())
                        .forEach(path -> {
                            try {
                                Files.deleteIfExists(path);
                            } catch (IOException e) {
                                log.warn("Failed to delete: {}", path, e);
                            }
                        });
            }
            log.info("Sandbox cleaned: {}", sandboxRoot);
        }
    }

    private String sanitizeName(String name) {
        return name.replaceAll("[^a-zA-Z0-9_-]", "_");
    }
}
