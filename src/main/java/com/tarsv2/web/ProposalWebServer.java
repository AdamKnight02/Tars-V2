package com.tarsv2.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.tarsv2.approval.ApprovalGate;
import com.tarsv2.approval.ChangeProposal;
import com.tarsv2.personality.DialogueStyle;
import com.tarsv2.security.AuthenticationConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Minimal authenticated Web UI for reviewing and managing proposals.
 *
 * <p>Provides endpoints to:</p>
 * <ul>
 *   <li>List proposals (GET /proposals)</li>
 *   <li>View a single proposal and its diff (GET /proposals/{id})</li>
 *   <li>Approve a proposal (POST /proposals/{id}/approve)</li>
 *   <li>Reject a proposal (POST /proposals/{id}/reject)</li>
 * </ul>
 *
 * <p>Authentication uses HTTP Basic auth via the immutable
 * {@link AuthenticationConfig}. The TARS agent MUST NOT call
 * the approve/reject endpoints — they are human-only.</p>
 */
public final class ProposalWebServer {

    private static final Logger log = LoggerFactory.getLogger(ProposalWebServer.class);
    private static final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final ApprovalGate approvalGate;
    private final DialogueStyle dialogue;
    private final int port;
    private HttpServer server;

    public ProposalWebServer(ApprovalGate approvalGate, DialogueStyle dialogue, int port) {
        this.approvalGate = Objects.requireNonNull(approvalGate);
        this.dialogue = Objects.requireNonNull(dialogue);
        this.port = port;
    }

    /**
     * Starts the web server.
     */
    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext("/", this::handleRoot);
        server.createContext("/proposals", this::handleProposals);
        server.setExecutor(null);
        server.start();

        dialogue.say("Web UI started on port " + port + ". Humans, you know what to do.", DialogueStyle.OutputMode.CHAT);
        log.info("ProposalWebServer started on port {}", port);
    }

    /**
     * Stops the web server.
     */
    public void stop() {
        if (server != null) {
            server.stop(1);
            log.info("ProposalWebServer stopped");
        }
    }

    private void handleRoot(HttpExchange exchange) throws IOException {
        if (!authenticate(exchange)) return;

        String html = buildDashboardHtml();
        sendResponse(exchange, 200, "text/html", html);
    }

    private void handleProposals(HttpExchange exchange) throws IOException {
        if (!authenticate(exchange)) return;

        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();

        // POST /proposals/{id}/approve or /proposals/{id}/reject
        if ("POST".equals(method) && path.matches("/proposals/[^/]+/(approve|reject)")) {
            handleProposalAction(exchange, path);
            return;
        }

        // GET /proposals/{id}
        if ("GET".equals(method) && path.matches("/proposals/[^/]+") && !path.equals("/proposals")) {
            String id = path.substring("/proposals/".length());
            handleSingleProposal(exchange, id);
            return;
        }

        // GET /proposals — list all
        if ("GET".equals(method)) {
            handleProposalList(exchange);
            return;
        }

        sendResponse(exchange, 405, "text/plain", "Method not allowed");
    }

    private void handleProposalList(HttpExchange exchange) throws IOException {
        List<ChangeProposal> pending = approvalGate.getPendingProposals();

        StringBuilder html = new StringBuilder();
        html.append(htmlHead("TARS Proposals"));
        html.append("<h1>Pending Proposals</h1>");

        if (pending.isEmpty()) {
            html.append("<p>No pending proposals. TARS is behaving.</p>");
        } else {
            html.append("<table><tr><th>ID</th><th>Description</th><th>Status</th><th>Actions</th></tr>");
            for (ChangeProposal p : pending) {
                html.append(String.format(
                        "<tr><td><a href='/proposals/%s'>%s</a></td><td>%s</td><td>%s</td>"
                        + "<td><form method='post' action='/proposals/%s/approve' style='display:inline'>"
                        + "<button type='submit'>Approve</button></form> "
                        + "<form method='post' action='/proposals/%s/reject' style='display:inline'>"
                        + "<button type='submit'>Reject</button></form></td></tr>",
                        p.getId(), p.getId(), escapeHtml(p.getDescription()),
                        p.getStatus(), p.getId(), p.getId()
                ));
            }
            html.append("</table>");
        }

        html.append("<p><a href='/'>Back to Dashboard</a></p></body></html>");
        sendResponse(exchange, 200, "text/html", html.toString());
    }

    private void handleSingleProposal(HttpExchange exchange, String id) throws IOException {
        Optional<ChangeProposal> opt = approvalGate.getProposal(id);
        if (opt.isEmpty()) {
            sendResponse(exchange, 404, "text/plain", "Proposal not found: " + id);
            return;
        }

        ChangeProposal p = opt.get();
        StringBuilder html = new StringBuilder();
        html.append(htmlHead("Proposal " + p.getId()));
        html.append(String.format("<h1>Proposal: %s</h1>", escapeHtml(p.getDescription())));
        html.append(String.format("<p><strong>Status:</strong> %s</p>", p.getStatus()));
        html.append(String.format("<p><strong>Rationale:</strong> %s</p>", escapeHtml(p.getRationale())));
        html.append(String.format("<p><strong>Created:</strong> %s</p>", p.getCreatedAt()));
        html.append("<h2>Diff</h2>");
        html.append(String.format("<pre>%s</pre>", escapeHtml(p.getDiff())));

        if ("PENDING".equals(p.getStatus().toString())) {
            html.append("<form method='post' action='/proposals/" + id + "/approve' style='display:inline'>"
                    + "<button type='submit'>Approve</button></form> ");
            html.append("<form method='post' action='/proposals/" + id + "/reject' style='display:inline'>"
                    + "<button type='submit'>Reject</button></form>");
        }

        html.append("<p><a href='/proposals'>Back to Proposals</a></p></body></html>");
        sendResponse(exchange, 200, "text/html", html.toString());
    }

    private void handleProposalAction(HttpExchange exchange, String path) throws IOException {
        // Extract ID and action from path like /proposals/{id}/approve
        String[] parts = path.split("/");
        if (parts.length < 4) {
            sendResponse(exchange, 400, "text/plain", "Invalid path");
            return;
        }
        String id = parts[2];
        String action = parts[3];

        boolean result;
        if ("approve".equals(action)) {
            result = approvalGate.approve(id);
            log.info("Web UI: Proposal {} approved by authenticated user", id);
        } else if ("reject".equals(action)) {
            result = approvalGate.reject(id);
            log.info("Web UI: Proposal {} rejected by authenticated user", id);
        } else {
            sendResponse(exchange, 400, "text/plain", "Unknown action: " + action);
            return;
        }

        if (result) {
            // Redirect back to proposals list
            exchange.getResponseHeaders().add("Location", "/proposals");
            sendResponse(exchange, 303, "text/plain", "Redirecting...");
        } else {
            sendResponse(exchange, 404, "text/plain",
                    "Proposal not found or already decided: " + id);
        }
    }

    private boolean authenticate(HttpExchange exchange) throws IOException {
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader != null && authHeader.startsWith("Basic ")) {
            try {
                String decoded = new String(Base64.getDecoder().decode(authHeader.substring(6)),
                        StandardCharsets.UTF_8);
                String[] parts = decoded.split(":", 2);
                if (parts.length == 2 && AuthenticationConfig.authenticate(parts[0], parts[1])) {
                    return true;
                }
            } catch (IllegalArgumentException ignored) {
                // fall through to 401
            }
        }

        exchange.getResponseHeaders().add("WWW-Authenticate", "Basic realm=\"TARS Proposal Review\"");
        sendResponse(exchange, 401, "text/plain", "Authentication required");
        return false;
    }

    private String buildDashboardHtml() {
        int pending = approvalGate.getPendingProposals().size();
        return htmlHead("TARS Dashboard")
                + "<h1>TARS v2 — Proposal Review Dashboard</h1>"
                + "<p>Pending proposals: <strong>" + pending + "</strong></p>"
                + "<p><a href='/proposals'>View Proposals</a></p>"
                + "</body></html>";
    }

    private void sendResponse(HttpExchange exchange, int code, String contentType, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", contentType + "; charset=utf-8");
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String htmlHead(String title) {
        return "<!DOCTYPE html><html><head><meta charset='utf-8'>"
                + "<title>" + escapeHtml(title) + "</title>"
                + "<style>"
                + "body { font-family: monospace; max-width: 900px; margin: 40px auto; padding: 0 20px; }"
                + "table { border-collapse: collapse; width: 100%; }"
                + "th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }"
                + "th { background-color: #333; color: white; }"
                + "pre { background: #f4f4f4; padding: 16px; overflow-x: auto; border: 1px solid #ddd; }"
                + "button { padding: 6px 12px; margin: 2px; cursor: pointer; }"
                + "a { color: #0066cc; }"
                + "</style></head><body>";
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
