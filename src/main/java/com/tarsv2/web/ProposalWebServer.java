package com.tarsv2.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.tarsv2.approval.ProposalRegistry;
import com.tarsv2.codex.PatchProposal;
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
import java.util.UUID;

public final class ProposalWebServer {

    private static final Logger log = LoggerFactory.getLogger(ProposalWebServer.class);

    private final ProposalRegistry proposalRegistry;
    private final DialogueStyle dialogue;
    private final int port;
    private HttpServer server;

    public ProposalWebServer(ProposalRegistry proposalRegistry, DialogueStyle dialogue, int port) {
        this.proposalRegistry = Objects.requireNonNull(proposalRegistry);
        this.dialogue = Objects.requireNonNull(dialogue);
        this.port = port;
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", this::handleRoot);
        server.createContext("/proposals", this::handleProposals);
        server.setExecutor(null);
        server.start();

        dialogue.say("Web UI started on port " + port + ".", DialogueStyle.OutputMode.CHAT);
        log.info("ProposalWebServer started on port {}", port);
    }

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

        if ("POST".equals(method) && path.matches("/proposals/[^/]+/(approve|reject)")) {
            handleProposalAction(exchange, path);
            return;
        }

        if ("GET".equals(method) && path.matches("/proposals/[^/]+") && !path.equals("/proposals")) {
            String id = path.substring("/proposals/".length());
            handleSingleProposal(exchange, id);
            return;
        }

        if ("GET".equals(method)) {
            handleProposalList(exchange);
            return;
        }

        sendResponse(exchange, 405, "text/plain", "Method not allowed");
    }

    private void handleProposalList(HttpExchange exchange) throws IOException {
        List<PatchProposal> pending = proposalRegistry.getPending();

        StringBuilder html = new StringBuilder();
        html.append(htmlHead("TARS Proposals"));
        html.append("<h1>Pending Proposals</h1>");

        if (pending.isEmpty()) {
            html.append("<p>No pending proposals.</p>");
        } else {
            html.append("<table><tr><th>ID</th><th>Description</th><th>Status</th><th>Actions</th></tr>");
            for (PatchProposal p : pending) {
                html.append(String.format(
                        "<tr><td><a href='/proposals/%s'>%s</a></td><td>%s</td><td>%s</td>"
                                + "<td><form method='post' action='/proposals/%s/approve' style='display:inline'>"
                                + "<button type='submit'>Approve</button></form> "
                                + "<form method='post' action='/proposals/%s/reject' style='display:inline'>"
                                + "<button type='submit'>Reject</button></form></td></tr>",
                        p.getId(), p.getId(), escapeHtml(p.getDescription()), p.getStatus(), p.getId(), p.getId()
                ));
            }
            html.append("</table>");
        }

        html.append("<p><a href='/'>Back to Dashboard</a></p></body></html>");
        sendResponse(exchange, 200, "text/html", html.toString());
    }

    private void handleSingleProposal(HttpExchange exchange, String id) throws IOException {
        UUID uuid = UUID.fromString(id);
        Optional<PatchProposal> opt = proposalRegistry.get(uuid);
        if (opt.isEmpty()) {
            sendResponse(exchange, 404, "text/plain", "Proposal not found: " + id);
            return;
        }

        PatchProposal p = opt.get();
        StringBuilder html = new StringBuilder();
        html.append(htmlHead("Proposal " + p.getId()));
        html.append(String.format("<h1>Proposal: %s</h1>", escapeHtml(p.getDescription())));
        html.append(String.format("<p><strong>Status:</strong> %s</p>", p.getStatus()));
        html.append(String.format("<p><strong>Target Path:</strong> %s</p>", escapeHtml(p.getTargetPath())));
        html.append(String.format("<p><strong>Created:</strong> %s</p>", p.getCreatedAt()));

        html.append("<h2>Diff Preview</h2>");
        html.append(String.format("<pre>%s</pre>", escapeHtml(p.getDiff())));

        html.append("<h2>Referenced Files</h2>");
        html.append("<ul>");
        for (String file : proposalRegistry.getReferencedFiles(uuid)) {
            html.append("<li>").append(escapeHtml(file)).append("</li>");
        }
        html.append("</ul>");

        html.append("<h2>Validation Result</h2>");
        html.append("<pre>").append(escapeHtml(proposalRegistry.getValidationResult(uuid))).append("</pre>");

        html.append("<h2>Apply Result Log</h2>");
        html.append("<pre>");
        for (String entry : proposalRegistry.getExecutionLogs(uuid)) {
            html.append(escapeHtml(entry)).append("\n");
        }
        html.append("</pre>");

        if (p.getStatus() == PatchProposal.Status.PENDING) {
            html.append("<form method='post' action='/proposals/" + id + "/approve' style='display:inline'>"
                    + "<button type='submit'>Approve</button></form> ");
            html.append("<form method='post' action='/proposals/" + id + "/reject' style='display:inline'>"
                    + "<button type='submit'>Reject</button></form>");
        }

        html.append("<p><a href='/proposals'>Back to Proposals</a></p></body></html>");
        sendResponse(exchange, 200, "text/html", html.toString());
    }

    private void handleProposalAction(HttpExchange exchange, String path) throws IOException {
        String[] parts = path.split("/");
        if (parts.length < 4) {
            sendResponse(exchange, 400, "text/plain", "Invalid path");
            return;
        }

        UUID id = UUID.fromString(parts[2]);
        String action = parts[3];

        boolean result;
        if ("approve".equals(action)) {
            result = proposalRegistry.approve(id);
            log.info("Web UI: Proposal {} approved by authenticated user", id);
        } else if ("reject".equals(action)) {
            result = proposalRegistry.reject(id);
            log.info("Web UI: Proposal {} rejected by authenticated user", id);
        } else {
            sendResponse(exchange, 400, "text/plain", "Unknown action: " + action);
            return;
        }

        if (result) {
            exchange.getResponseHeaders().add("Location", "/proposals");
            sendResponse(exchange, 303, "text/plain", "Redirecting...");
        } else {
            sendResponse(exchange, 404, "text/plain", "Proposal not found or already decided: " + id);
        }
    }

    private boolean authenticate(HttpExchange exchange) throws IOException {
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader != null && authHeader.startsWith("Basic ")) {
            try {
                String decoded = new String(Base64.getDecoder().decode(authHeader.substring(6)), StandardCharsets.UTF_8);
                String[] parts = decoded.split(":", 2);
                if (parts.length == 2 && AuthenticationConfig.authenticate(parts[0], parts[1])) {
                    return true;
                }
            } catch (IllegalArgumentException ignored) {
            }
        }

        exchange.getResponseHeaders().add("WWW-Authenticate", "Basic realm=\"TARS Proposal Review\"");
        sendResponse(exchange, 401, "text/plain", "Authentication required");
        return false;
    }

    private String buildDashboardHtml() {
        int pending = proposalRegistry.getPending().size();
        return htmlHead("TARS Dashboard")
                + "<h1>TARS v2 — Proposal Review Dashboard</h1>"
                + "<p>Pending proposals: <strong>" + pending + "</strong></p>"
                + "<p><a href='/proposals'>View Proposals</a></p>"
                + "</body></html>";
    }

    private void sendResponse(HttpExchange exchange, int code, String contentType, String body) throws IOException {
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
                + "body { font-family: monospace; max-width: 980px; margin: 40px auto; padding: 0 20px; }"
                + "table { border-collapse: collapse; width: 100%; }"
                + "th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }"
                + "th { background-color: #333; color: white; }"
                + "pre { background: #f4f4f4; padding: 16px; overflow-x: auto; border: 1px solid #ddd; white-space: pre-wrap; }"
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
