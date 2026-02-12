package com.tarsv2.ci;

/**
 * Classifies CI failures into actionable categories.
 *
 * <p>TARS uses these categories to decide next steps without
 * needing to read raw log output.</p>
 */
public enum FailureCategory {

    /** Compilation error — code doesn't build. */
    COMPILE_ERROR("Compilation failed"),

    /** Test failure — one or more tests did not pass. */
    TEST_FAILURE("Test assertions failed"),

    /** Lint/style violation — formatting or style check failed. */
    LINT_VIOLATION("Code style or lint check failed"),

    /** Dependency issue — missing or conflicting dependencies. */
    DEPENDENCY_ERROR("Dependency resolution failed"),

    /** Timeout — execution exceeded time limits. */
    TIMEOUT("Execution timed out"),

    /** Infrastructure — CI runner or environment issue (not code). */
    INFRASTRUCTURE("CI infrastructure or runner failure"),

    /** Security — security scan detected vulnerability. */
    SECURITY_SCAN("Security vulnerability detected"),

    /** Unknown — could not classify the failure. */
    UNKNOWN("Unclassified failure");

    private final String description;

    FailureCategory(String description) {
        this.description = description;
    }

    public String getDescription() { return description; }
}
