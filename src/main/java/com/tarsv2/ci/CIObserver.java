package com.tarsv2.ci;

import com.tarsv2.connector.DevAuditLog;
import com.tarsv2.connector.GitHubConnector;
import com.tarsv2.personality.DialogueStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Observes CI status for agent PRs and produces structured summaries.
 *
 * <p>Key rules:</p>
 * <ul>
 *   <li>Never passes raw CI logs to the LLM</li>
 *   <li>Classifies failures into {@link FailureCategory}</li>
 *   <li>Provides only structured {@link CIStatus} objects</li>
 * </ul>
 */
public final class CIObserver {

    private static final Logger log = LoggerFactory.getLogger(CIObserver.class);

    private final GitHubConnector github;
    private final DialogueStyle dialogue;
    private final DevAuditLog auditLog;

    public CIObserver(GitHubConnector github, DialogueStyle dialogue, DevAuditLog auditLog) {
        this.github = github;
        this.dialogue = dialogue;
        this.auditLog = auditLog;
    }

    /**
     * Queries and structures CI status for a given ref.
     *
     * @param ref branch name or commit SHA
     * @return structured CI status (never raw logs)
     */
    public CIStatus observe(String ref) {
        try {
            String rawJson = github.queryCIStatus(ref);
            CIStatus status = parseCheckRuns(ref, rawJson);

            auditLog.record("CI", "OBSERVE",
                    "CI status for " + ref + ": " + status.overallState(),
                    List.of());

            dialogue.say("CI for " + ref + ": " + status.overallState()
                    + " (" + status.checks().size() + " checks)");

            return status;
        } catch (IOException e) {
            log.warn("Failed to query CI status for {}: {}", ref, e.getMessage());
            return new CIStatus(ref, CIStatus.OverallState.UNKNOWN,
                    List.of(), Instant.now());
        }
    }

    /**
     * Parses GitHub check-runs JSON into structured CIStatus.
     * Extracts only structured data — never raw log output.
     */
    CIStatus parseCheckRuns(String ref, String rawJson) {
        List<CIStatus.CheckResult> checks = new ArrayList<>();

        // Extract individual check runs
        Pattern namePattern = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"");
        Pattern statusPattern = Pattern.compile("\"status\"\\s*:\\s*\"([^\"]+)\"");
        Pattern conclusionPattern = Pattern.compile("\"conclusion\"\\s*:\\s*\"([^\"]+)\"");
        Pattern outputPattern = Pattern.compile("\"summary\"\\s*:\\s*\"([^\"]{0,200})\"");

        Matcher nameMatcher = namePattern.matcher(rawJson);
        Matcher statusMatcher = statusPattern.matcher(rawJson);
        Matcher conclusionMatcher = conclusionPattern.matcher(rawJson);

        while (nameMatcher.find()) {
            String name = nameMatcher.group(1);
            String status = statusMatcher.find() ? statusMatcher.group(1) : "unknown";
            String conclusion = conclusionMatcher.find() ? conclusionMatcher.group(1) : null;

            CIStatus.CheckResult.CheckState state = mapState(status, conclusion);
            FailureCategory category = state == CIStatus.CheckResult.CheckState.FAILURE
                    ? classifyFailure(name, conclusion) : null;
            String summary = summarizeCheck(name, state, category);

            checks.add(new CIStatus.CheckResult(name, state, category, summary));
        }

        CIStatus.OverallState overall = determineOverall(checks);
        return new CIStatus(ref, overall, checks, Instant.now());
    }

    private CIStatus.CheckResult.CheckState mapState(String status, String conclusion) {
        if ("completed".equals(status)) {
            if ("success".equals(conclusion)) return CIStatus.CheckResult.CheckState.SUCCESS;
            if ("failure".equals(conclusion)) return CIStatus.CheckResult.CheckState.FAILURE;
            if ("skipped".equals(conclusion)) return CIStatus.CheckResult.CheckState.SKIPPED;
        }
        if ("in_progress".equals(status) || "queued".equals(status)) {
            return CIStatus.CheckResult.CheckState.PENDING;
        }
        return CIStatus.CheckResult.CheckState.PENDING;
    }

    private FailureCategory classifyFailure(String checkName, String conclusion) {
        String lower = checkName.toLowerCase();
        if (lower.contains("build") || lower.contains("compile")) return FailureCategory.COMPILE_ERROR;
        if (lower.contains("test")) return FailureCategory.TEST_FAILURE;
        if (lower.contains("lint") || lower.contains("style") || lower.contains("format")) {
            return FailureCategory.LINT_VIOLATION;
        }
        if (lower.contains("security") || lower.contains("codeql") || lower.contains("snyk")) {
            return FailureCategory.SECURITY_SCAN;
        }
        if (lower.contains("dep")) return FailureCategory.DEPENDENCY_ERROR;
        return FailureCategory.UNKNOWN;
    }

    private String summarizeCheck(String name, CIStatus.CheckResult.CheckState state,
                                   FailureCategory category) {
        if (state == CIStatus.CheckResult.CheckState.SUCCESS) {
            return name + " passed";
        }
        if (state == CIStatus.CheckResult.CheckState.FAILURE && category != null) {
            return name + " failed: " + category.getDescription();
        }
        return name + " " + state.name().toLowerCase();
    }

    private CIStatus.OverallState determineOverall(List<CIStatus.CheckResult> checks) {
        if (checks.isEmpty()) return CIStatus.OverallState.UNKNOWN;
        boolean anyFailing = checks.stream()
                .anyMatch(c -> c.state() == CIStatus.CheckResult.CheckState.FAILURE);
        boolean anyPending = checks.stream()
                .anyMatch(c -> c.state() == CIStatus.CheckResult.CheckState.PENDING);
        if (anyFailing) return CIStatus.OverallState.FAILING;
        if (anyPending) return CIStatus.OverallState.PENDING;
        return CIStatus.OverallState.PASSING;
    }
}
