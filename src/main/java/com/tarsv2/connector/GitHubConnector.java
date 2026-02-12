package com.tarsv2.connector;

import com.tarsv2.personality.DialogueStyle;
import com.tarsv2.security.ChangeControl;
import com.tarsv2.security.SecretManager;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * GitHub Connector — interacts with GitHub REST API.
 *
 * <p>Allows TARS to:</p>
 * <ul>
 *   <li>Create branches (agent/* prefix only)</li>
 *   <li>Commit staged changes</li>
 *   <li>Open pull requests</li>
 *   <li>Comment on PRs with explanations</li>
 * </ul>
 *
 * <p><strong>NEVER:</strong></p>
 * <ul>
 *   <li>Merge pull requests</li>
 *   <li>Approve reviews</li>
 *   <li>Modify workflows, secrets, or branch protections</li>
 * </ul>
 *
 * <p>Branch protection is enforced in code — only agent/* branches
 * are writable. ChangeControl rules still apply.</p>
 */
public final class GitHubConnector {

    private static final Logger log = LoggerFactory.getLogger(GitHubConnector.class);
    private static final String API_BASE = "https://api.github.com";
    private static final String AGENT_BRANCH_PREFIX = "agent/";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    /**
     * Endpoints that TARS is forbidden from calling.
     */
    private static final Set<String> FORBIDDEN_ENDPOINTS = Set.of(
            "/merges",
            "/reviews",
            "/actions/secrets",
            "/actions/workflows",
            "/branches/*/protection"
    );

    private final String owner;
    private final String repo;
    private final SecretManager.SecretHandle tokenHandle;
    private final DevPermissionModel permissions;
    private final DevAuditLog auditLog;
    private final DialogueStyle dialogue;
    private final OkHttpClient httpClient;

    public GitHubConnector(String owner, String repo,
                           SecretManager.SecretHandle tokenHandle,
                           DevPermissionModel permissions,
                           DevAuditLog auditLog,
                           DialogueStyle dialogue) {
        this.owner = Objects.requireNonNull(owner);
        this.repo = Objects.requireNonNull(repo);
        this.tokenHandle = Objects.requireNonNull(tokenHandle);
        this.permissions = Objects.requireNonNull(permissions);
        this.auditLog = Objects.requireNonNull(auditLog);
        this.dialogue = Objects.requireNonNull(dialogue);
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(java.time.Duration.ofSeconds(10))
                .readTimeout(java.time.Duration.ofSeconds(30))
                .build();
    }

    /**
     * Creates a branch. ONLY agent/* branches are allowed.
     *
     * @param branchName the branch name (must start with "agent/")
     * @param baseSha    the SHA to branch from
     * @return true if created successfully
     */
    public boolean createBranch(String branchName, String baseSha) throws IOException {
        permissions.requireCapability(DevCapability.OPEN_PR);
        validateAgentBranch(branchName);

        String body = String.format("{\"ref\":\"refs/heads/%s\",\"sha\":\"%s\"}",
                branchName, baseSha);
        String url = apiUrl("/git/refs");

        Response response = post(url, body);
        boolean success = response.isSuccessful();
        response.close();

        auditLog.record("GitHub", "CREATE_BRANCH",
                "Created branch " + branchName + " from " + baseSha.substring(0, 7),
                List.of());

        if (success) {
            dialogue.say("Branch " + branchName + " created successfully.", DialogueStyle.OutputMode.CHAT);
        } else {
            dialogue.say("Failed to create branch " + branchName + ".", DialogueStyle.OutputMode.CHAT);
        }
        return success;
    }

    /**
     * Commits staged changes to an agent/* branch.
     *
     * @param branchName the target branch (must be agent/*)
     * @param message    commit message
     * @param treeSha    the tree SHA for the commit
     * @param parentSha  parent commit SHA
     * @return the commit SHA, or null on failure
     */
    public String commitChanges(String branchName, String message,
                                 String treeSha, String parentSha) throws IOException {
        permissions.requireCapability(DevCapability.PROPOSE_CHANGES);
        validateAgentBranch(branchName);

        // Create commit object
        String commitBody = String.format(
                "{\"message\":\"%s\",\"tree\":\"%s\",\"parents\":[\"%s\"]}",
                escapeJson(message), treeSha, parentSha);
        Response commitResp = post(apiUrl("/git/commits"), commitBody);

        if (!commitResp.isSuccessful()) {
            commitResp.close();
            return null;
        }

        String commitSha = extractField(commitResp.body().string(), "sha");
        commitResp.close();

        // Update branch reference
        String refBody = String.format("{\"sha\":\"%s\"}", commitSha);
        Response refResp = patch(apiUrl("/git/refs/heads/" + branchName), refBody);
        refResp.close();

        auditLog.record("GitHub", "COMMIT",
                "Committed to " + branchName + ": " + truncate(message, 60),
                List.of());

        dialogue.say("Committed to " + branchName + ": " + truncate(message, 50), DialogueStyle.OutputMode.CHAT);
        return commitSha;
    }

    /**
     * Opens a pull request from an agent/* branch.
     *
     * @param title      PR title
     * @param body       PR description
     * @param headBranch source branch (must be agent/*)
     * @param baseBranch target branch
     * @return PR number, or -1 on failure
     */
    public int openPullRequest(String title, String body,
                                String headBranch, String baseBranch) throws IOException {
        permissions.requireCapability(DevCapability.OPEN_PR);
        validateAgentBranch(headBranch);

        String prBody = String.format(
                "{\"title\":\"%s\",\"body\":\"%s\",\"head\":\"%s\",\"base\":\"%s\"}",
                escapeJson(title), escapeJson(body), headBranch, baseBranch);
        Response response = post(apiUrl("/pulls"), prBody);

        if (!response.isSuccessful()) {
            response.close();
            dialogue.say("Failed to open PR from " + headBranch + ".", DialogueStyle.OutputMode.CHAT);
            return -1;
        }

        String responseBody = response.body().string();
        response.close();
        int prNumber = Integer.parseInt(extractField(responseBody, "number"));

        auditLog.record("GitHub", "OPEN_PR",
                "Opened PR #" + prNumber + ": " + title,
                List.of());

        dialogue.say("PR #" + prNumber + " opened: " + title, DialogueStyle.OutputMode.CHAT);
        return prNumber;
    }

    /**
     * Comments on a pull request with an explanation.
     *
     * @param prNumber the PR number
     * @param comment  the comment body
     * @return true if posted successfully
     */
    public boolean commentOnPR(int prNumber, String comment) throws IOException {
        permissions.requireCapability(DevCapability.OPEN_PR);

        String body = String.format("{\"body\":\"%s\"}", escapeJson(comment));
        Response response = post(apiUrl("/issues/" + prNumber + "/comments"), body);
        boolean success = response.isSuccessful();
        response.close();

        auditLog.record("GitHub", "COMMENT_PR",
                "Commented on PR #" + prNumber,
                List.of());

        return success;
    }

    /**
     * Queries CI check status for a given ref (branch or SHA).
     *
     * @param ref the Git ref to check
     * @return raw status JSON, or error message
     */
    public String queryCIStatus(String ref) throws IOException {
        permissions.requireCapability(DevCapability.READ_CODE);

        String url = apiUrl("/commits/" + ref + "/check-runs");
        Response response = get(url);
        String result = response.body().string();
        response.close();

        auditLog.record("GitHub", "QUERY_CI",
                "Queried CI status for " + ref,
                List.of());

        return result;
    }

    // ── Safety enforcement ───────────────────────────────────────

    /**
     * Validates that a branch name starts with agent/ prefix.
     * TARS may NEVER write to non-agent branches.
     */
    private void validateAgentBranch(String branchName) {
        if (branchName == null || !branchName.startsWith(AGENT_BRANCH_PREFIX)) {
            throw new SecurityException(
                    "TARS may only operate on agent/* branches. Attempted: " + branchName);
        }
        // Also check ChangeControl's protected branches
        if (ChangeControl.getProtectedBranches().contains(branchName.toLowerCase())) {
            throw new SecurityException(
                    "Branch " + branchName + " is protected. Write denied.");
        }
    }

    /**
     * Ensures the URL doesn't target forbidden endpoints.
     */
    private void validateEndpoint(String url) {
        for (String forbidden : FORBIDDEN_ENDPOINTS) {
            if (url.contains(forbidden.replace("*", ""))) {
                throw new SecurityException(
                        "TARS is forbidden from accessing endpoint pattern: " + forbidden);
            }
        }
    }

    // ── HTTP helpers ─────────────────────────────────────────────

    private String apiUrl(String path) {
        return API_BASE + "/repos/" + owner + "/" + repo + path;
    }

    private Response get(String url) throws IOException {
        validateEndpoint(url);
        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + tokenHandle.resolve())
                .header("Accept", "application/vnd.github+json")
                .build();
        return httpClient.newCall(request).execute();
    }

    private Response post(String url, String jsonBody) throws IOException {
        validateEndpoint(url);
        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + tokenHandle.resolve())
                .header("Accept", "application/vnd.github+json")
                .post(RequestBody.create(jsonBody, JSON))
                .build();
        return httpClient.newCall(request).execute();
    }

    private Response patch(String url, String jsonBody) throws IOException {
        validateEndpoint(url);
        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + tokenHandle.resolve())
                .header("Accept", "application/vnd.github+json")
                .patch(RequestBody.create(jsonBody, JSON))
                .build();
        return httpClient.newCall(request).execute();
    }

    private String extractField(String json, String field) {
        String pattern = "\"" + field + "\":";
        int idx = json.indexOf(pattern);
        if (idx < 0) return "";
        int start = idx + pattern.length();
        // Skip whitespace
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) start++;
        if (start >= json.length()) return "";
        if (json.charAt(start) == '"') {
            int end = json.indexOf('"', start + 1);
            return end > 0 ? json.substring(start + 1, end) : "";
        }
        // Numeric value
        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) {
            end++;
        }
        return json.substring(start, end);
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    private String truncate(String s, int max) {
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }
}
