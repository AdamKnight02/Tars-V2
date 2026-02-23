package com.tarsv2.environment;

import com.tarsv2.openclaw.IntentResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;

/**
 * Factory for the three standard TARS execution environments.
 *
 * <p>Each environment has a pinned tool set, resource limits,
 * and a dispatcher. All execution is sandbox-only.</p>
 */
public final class EnvironmentFactory {

    private static final Logger log = LoggerFactory.getLogger(EnvironmentFactory.class);

    private EnvironmentFactory() {}

    /**
     * Creates the coding-sandbox environment.
     * Tools: file I/O, diffs, patches, tests, git, CI queries.
     */
    public static ExecutionEnvironment codingSandbox(EnvironmentDispatcher dispatcher) {
        return new ExecutionEnvironment(
                "coding-sandbox",
                "General-purpose coding sandbox with file, git, and test tools",
                Set.of("REASON", "EVALUATE", "ANALYZE_CODE",
                        "READ_FILE", "WRITE_FILE", "GENERATE_DIFF", "APPLY_PATCH",
                        "RUN_COMMAND", "RUN_TESTS", "CREATE_BRANCH", "COMMIT",
                        "OPEN_PR", "COMMENT_PR", "QUERY_CI", "MEMORY_READ", "MEMORY_WRITE"),
                Map.of("sandbox.type", "coding", "git.enabled", "true"),
                ResourceLimits.standard(),
                dispatcher
        );
    }

    /**
     * Creates the resume-sandbox environment.
     * Tools: file read, command execution only. No git operations.
     */
    public static ExecutionEnvironment resumeSandbox(EnvironmentDispatcher dispatcher) {
        return new ExecutionEnvironment(
                "resume-sandbox",
                "Restricted sandbox for resume parsing — no git, no network writes",
                Set.of("READ_FILE", "RUN_COMMAND", "MEMORY_READ", "MEMORY_WRITE"),
                Map.of("sandbox.type", "resume", "git.enabled", "false"),
                ResourceLimits.restricted(),
                dispatcher
        );
    }

    /**
     * Creates the depop-sandbox environment.
     * Tools: command execution (scraping), file read/write, memory.
     */
    public static ExecutionEnvironment depopSandbox(EnvironmentDispatcher dispatcher) {
        return new ExecutionEnvironment(
                "depop-sandbox",
                "Restricted sandbox for Depop scraping — container-only execution",
                Set.of("READ_FILE", "WRITE_FILE", "RUN_COMMAND", "MEMORY_READ", "MEMORY_WRITE"),
                Map.of("sandbox.type", "depop", "git.enabled", "false"),
                ResourceLimits.restricted(),
                dispatcher
        );
    }


    /**
     * Creates a read-only research-sandbox environment.
     */
    public static ExecutionEnvironment researchSandbox(EnvironmentDispatcher dispatcher) {
        return new ExecutionEnvironment(
                "research-sandbox",
                "Read-only research sandbox for controlled exploration and analysis",
                Set.of("REASON", "EVALUATE", "ANALYZE_CODE", "READ_FILE", "MEMORY_READ"),
                Map.of("sandbox.type", "research", "git.enabled", "false", "write.enabled", "false"),
                ResourceLimits.restricted(),
                dispatcher
        );
    }

    /**
     * Registers all standard environments into the given registry.
     */
    public static void registerAll(EnvironmentRegistry registry, EnvironmentDispatcher dispatcher) {
        registry.register(codingSandbox(dispatcher));
        registry.register(resumeSandbox(dispatcher));
        registry.register(depopSandbox(dispatcher));
        registry.register(researchSandbox(dispatcher));
        log.info("Registered {} standard environments", registry.size());
    }
}
