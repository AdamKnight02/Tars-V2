package com.tarsv2.ci;

import java.time.Instant;
import java.util.List;

/**
 * Structured representation of CI pipeline status for an agent PR.
 *
 * <p>Raw CI logs are NEVER passed to the LLM. This class provides
 * structured, summarized data only.</p>
 */
public record CIStatus(
        String ref,
        OverallState overallState,
        List<CheckResult> checks,
        Instant queriedAt
) {
    public enum OverallState {
        PASSING, FAILING, PENDING, UNKNOWN
    }

    /**
     * A single CI check result (structured, not raw logs).
     */
    public record CheckResult(
            String name,
            CheckState state,
            FailureCategory failureCategory,
            String summary
    ) {
        public enum CheckState { SUCCESS, FAILURE, PENDING, SKIPPED }
    }

    /**
     * Returns a human-readable summary suitable for display.
     */
    public String toSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("CI Status for ").append(ref).append(": ").append(overallState).append("\n");
        for (CheckResult check : checks) {
            sb.append(String.format("  [%s] %s — %s",
                    check.state(), check.name(), check.summary()));
            if (check.failureCategory() != null) {
                sb.append(" (").append(check.failureCategory()).append(")");
            }
            sb.append("\n");
        }
        return sb.toString();
    }
}
