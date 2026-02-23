package com.tarsv2.openclaw;

/**
 * Enumerates all intent types TARS may produce for OpenClaw execution.
 *
 * <p>TARS never calls tools directly — it emits structured Intents,
 * and OpenClaw is the sole execution layer.</p>
 */
public enum IntentType {

    /** Perform reasoning/planning using LLM backends. */
    REASON,

    /** Evaluate a task/opportunity using LLM backends. */
    EVALUATE,

    /** Analyze code or architecture using LLM backends. */
    ANALYZE_CODE,

    /** Read a file from the working environment. */
    READ_FILE,

    /** Write or overwrite a file (requires approval). */
    WRITE_FILE,

    /** Generate a unified diff between two file states. */
    GENERATE_DIFF,

    /** Apply a patch to a file (requires approval). */
    APPLY_PATCH,

    /** Run a shell command inside the sandbox. */
    RUN_COMMAND,

    /** Run tests inside the sandbox. */
    RUN_TESTS,

    /** Create a Git branch. */
    CREATE_BRANCH,

    /** Stage and commit files. */
    COMMIT,

    /** Open a pull request. */
    OPEN_PR,

    /** Comment on a pull request. */
    COMMENT_PR,

    /** Query CI status for a branch or PR. */
    QUERY_CI,

    /** Read memory from the memory system. */
    MEMORY_READ,

    /** Write memory to the memory system. */
    MEMORY_WRITE
}
