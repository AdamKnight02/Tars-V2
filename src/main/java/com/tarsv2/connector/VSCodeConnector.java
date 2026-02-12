package com.tarsv2.connector;

import com.tarsv2.approval.ApprovalGate;
import com.tarsv2.approval.ChangeProposal;
import com.tarsv2.personality.DialogueStyle;
import com.tarsv2.security.ChangeControl;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * VS Code Connector — exposes a local REST interface for IDE integration.
 *
 * <p>Allows TARS to:</p>
 * <ul>
 *   <li>Read files from the workspace</li>
 *   <li>Generate and propose diffs</li>
 *   <li>Apply patches ONLY after explicit human approval via ChangeControl + ApprovalGate</li>
 * </ul>
 *
 * <p><strong>Safety:</strong> No direct writes to disk. All mutations
 * route through the ApprovalGate. All output uses DialogueStyle.</p>
 *
 * <h3>Endpoints:</h3>
 * <pre>
 *   GET  /vscode/read?path=...         — Read a file
 *   POST /vscode/diff                  — Generate a diff proposal
 *   POST /vscode/apply?proposalId=...  — Apply approved patch
 *   GET  /vscode/status                — Connector status
 * </pre>
 */
public final class VSCodeConnector {

    private static final Logger log = LoggerFactory.getLogger(VSCodeConnector.class);

    private final Path workspaceRoot;
    private final ApprovalGate approvalGate;
    private final DialogueStyle dialogue;
    private final DevPermissionModel permissions;
    private final DevAuditLog auditLog;
    private HttpServer server;
    private final int port;

    public VSCodeConnector(Path workspaceRoot, ApprovalGate approvalGate,
                           DialogueStyle dialogue, DevPermissionModel permissions,
                           DevAuditLog auditLog, int port) {
        this.workspaceRoot = Objects.requireNonNull(workspaceRoot);
        this.approvalGate = Objects.requireNonNull(approvalGate);
        this.dialogue = Objects.requireNonNull(dialogue);
        this.permissions = Objects.requireNonNull(permissions);
        this.auditLog = Objects.requireNonNull(auditLog);
        this.port = port;
    }

    /**
     * Starts the local REST server for VS Code communication.
     */
    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/vscode/read", this::handleRead);
        server.createContext("/vscode/diff", this::handleDiff);
        server.createContext("/vscode/apply", this::handleApply);
        server.createContext("/vscode/status", this::handleStatus);
        server.setExecutor(null);
        server.start();

        dialogue.say("VS Code connector listening on 127.0.0.1:" + port, DialogueStyle.OutputMode.CHAT);
        log.info("VSCodeConnector started on port {}", port);
    }

    /**
     * Stops the connector.
     */
    public void stop() {
        if (server != null) {
            server.stop(0);
            log.info("VSCodeConnector stopped");
        }
    }

    /**
     * Reads a file from the workspace. Requires READ_CODE capability.
     */
    public String readFile(String relativePath) throws IOException {
        permissions.requireCapability(DevCapability.READ_CODE);

        Path resolved = workspaceRoot.resolve(relativePath).normalize();
        if (!resolved.startsWith(workspaceRoot)) {
            throw new SecurityException("Path traversal blocked: " + relativePath);
        }

        String content = Files.readString(resolved);
        auditLog.record("VSCode", "READ_FILE", "Read " + relativePath,
                List.of(relativePath));
        return content;
    }

    /**
     * Generates a diff and submits it as a ChangeProposal.
     * Requires PROPOSE_CHANGES capability. No direct writes.
     */
    public String proposeDiff(String relativePath, String newContent, String rationale) {
        permissions.requireCapability(DevCapability.PROPOSE_CHANGES);

        ChangeProposal proposal = new ChangeProposal(
                "VS Code diff: " + relativePath,
                newContent,
                rationale
        );
        String proposalId = approvalGate.submit(proposal);

        auditLog.record("VSCode", "PROPOSE_DIFF",
                "Diff proposal " + proposalId + " for " + relativePath,
                List.of(relativePath));

        dialogue.say("Diff proposal " + proposalId + " submitted for " + relativePath
                + ". Awaiting human approval.");
        return proposalId;
    }

    /**
     * Applies an approved patch. ONLY proceeds if the proposal is approved
     * via the ApprovalGate.
     */
    public boolean applyApproved(String proposalId) throws IOException {
        permissions.requireCapability(DevCapability.PROPOSE_CHANGES);

        if (!approvalGate.isApproved(proposalId)) {
            dialogue.say("Cannot apply: proposal " + proposalId + " is not approved.", DialogueStyle.OutputMode.CHAT);
            auditLog.record("VSCode", "APPLY_BLOCKED",
                    "Attempted apply of unapproved proposal " + proposalId, List.of());
            return false;
        }

        var proposal = approvalGate.getProposal(proposalId);
        if (proposal.isEmpty()) {
            return false;
        }

        // Extract path from description
        String desc = proposal.get().getDescription();
        String path = desc.replace("VS Code diff: ", "");
        Path resolved = workspaceRoot.resolve(path).normalize();

        if (!resolved.startsWith(workspaceRoot)) {
            throw new SecurityException("Path traversal blocked: " + path);
        }

        Files.createDirectories(resolved.getParent());
        Files.writeString(resolved, proposal.get().getDiff());

        auditLog.record("VSCode", "APPLY_APPROVED",
                "Applied approved proposal " + proposalId,
                List.of(path));
        dialogue.say("Patch applied for " + path + " (proposal " + proposalId + ").", DialogueStyle.OutputMode.CHAT);
        return true;
    }

    // ── HTTP handlers ───────────────────────────────────────────

    private void handleRead(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }
        String query = exchange.getRequestURI().getQuery();
        String path = extractParam(query, "path");
        if (path == null) {
            sendResponse(exchange, 400, "{\"error\":\"Missing 'path' parameter\"}");
            return;
        }
        try {
            String content = readFile(path);
            sendResponse(exchange, 200, "{\"content\":" + escapeJson(content) + "}");
        } catch (SecurityException e) {
            sendResponse(exchange, 403, "{\"error\":" + escapeJson(e.getMessage()) + "}");
        } catch (IOException e) {
            sendResponse(exchange, 404, "{\"error\":" + escapeJson(e.getMessage()) + "}");
        }
    }

    private void handleDiff(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        // Simple JSON parsing for path, content, rationale
        String path = extractJsonField(body, "path");
        String content = extractJsonField(body, "content");
        String rationale = extractJsonField(body, "rationale");

        if (path == null || content == null) {
            sendResponse(exchange, 400, "{\"error\":\"Missing 'path' or 'content'\"}");
            return;
        }
        try {
            String proposalId = proposeDiff(path, content,
                    rationale != null ? rationale : "VS Code diff proposal");
            sendResponse(exchange, 200,
                    "{\"proposalId\":" + escapeJson(proposalId) + "}");
        } catch (SecurityException e) {
            sendResponse(exchange, 403, "{\"error\":" + escapeJson(e.getMessage()) + "}");
        }
    }

    private void handleApply(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }
        String query = exchange.getRequestURI().getQuery();
        String proposalId = extractParam(query, "proposalId");
        if (proposalId == null) {
            sendResponse(exchange, 400, "{\"error\":\"Missing 'proposalId' parameter\"}");
            return;
        }
        try {
            boolean applied = applyApproved(proposalId);
            sendResponse(exchange, applied ? 200 : 403,
                    "{\"applied\":" + applied + "}");
        } catch (SecurityException e) {
            sendResponse(exchange, 403, "{\"error\":" + escapeJson(e.getMessage()) + "}");
        }
    }

    private void handleStatus(HttpExchange exchange) throws IOException {
        String formatted = dialogue.format("VS Code connector operational. Workspace: " + workspaceRoot);
        sendResponse(exchange, 200,
                "{\"status\":\"ok\",\"workspace\":" + escapeJson(workspaceRoot.toString())
                + ",\"message\":" + escapeJson(formatted) + "}");
    }

    private void sendResponse(HttpExchange exchange, int code, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private String extractParam(String query, String key) {
        if (query == null) return null;
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2 && kv[0].equals(key)) {
                return java.net.URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private String extractJsonField(String json, String field) {
        // Minimal JSON field extraction — production would use Jackson
        String pattern = "\"" + field + "\"";
        int idx = json.indexOf(pattern);
        if (idx < 0) return null;
        int colonIdx = json.indexOf(':', idx + pattern.length());
        if (colonIdx < 0) return null;
        int startQuote = json.indexOf('"', colonIdx + 1);
        if (startQuote < 0) return null;
        int endQuote = json.indexOf('"', startQuote + 1);
        while (endQuote > 0 && json.charAt(endQuote - 1) == '\\') {
            endQuote = json.indexOf('"', endQuote + 1);
        }
        if (endQuote < 0) return null;
        return json.substring(startQuote + 1, endQuote);
    }

    private String escapeJson(String s) {
        return "\"" + s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t") + "\"";
    }
}
