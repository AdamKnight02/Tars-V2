package com.tarsv2.environment;

import com.tarsv2.approval.ApprovalGate;
import com.tarsv2.approval.ChangeProposal;
import com.tarsv2.openclaw.Intent;
import com.tarsv2.openclaw.IntentResult;
import com.tarsv2.sandbox.SandboxEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Default dispatcher that executes intents inside a SandboxEnvironment.
 *
 * <p>Write operations are routed through the ApprovalGate — no direct
 * disk writes without human approval.</p>
 */
public final class SandboxDispatcher implements EnvironmentDispatcher {

    private static final Logger log = LoggerFactory.getLogger(SandboxDispatcher.class);

    private final SandboxEnvironment sandbox;
    private final ApprovalGate approvalGate;

    public SandboxDispatcher(SandboxEnvironment sandbox, ApprovalGate approvalGate) {
        this.sandbox = sandbox;
        this.approvalGate = approvalGate;
    }

    @Override
    public IntentResult dispatch(ExecutionEnvironment env, Intent intent) {
        long start = System.currentTimeMillis();
        String intentId = intent.getId();

        return switch (intent.getType()) {
            case READ_FILE -> handleReadFile(intentId, intent, start);
            case WRITE_FILE -> handleWriteFile(intentId, intent, start);
            case GENERATE_DIFF -> handleGenerateDiff(intentId, intent, start);
            case APPLY_PATCH -> handleApplyPatch(intentId, intent, start);
            case RUN_COMMAND -> handleRunCommand(intentId, intent, start);
            case RUN_TESTS -> handleRunTests(intentId, intent, start);
            default -> IntentResult.failure(intentId,
                    "Intent type " + intent.getType() + " not handled by SandboxDispatcher",
                    elapsed(start));
        };
    }

    private IntentResult handleReadFile(String id, Intent intent, long start) {
        String filePath = intent.getPayload().require("path");
        Path resolved = sandbox.getRoot().resolve(filePath);
        try {
            if (!Files.exists(resolved)) {
                return IntentResult.failure(id, "File not found: " + filePath, elapsed(start));
            }
            String content = Files.readString(resolved);
            int lineCount = content.split("\n", -1).length;
            return IntentResult.success(id,
                    Map.of("content", content, "lineCount", String.valueOf(lineCount)),
                    "Read " + lineCount + " lines from " + filePath,
                    elapsed(start));
        } catch (IOException e) {
            return IntentResult.failure(id, "Read failed: " + e.getMessage(), elapsed(start));
        }
    }

    private IntentResult handleWriteFile(String id, Intent intent, long start) {
        String filePath = intent.getPayload().require("path");
        String content = intent.getPayload().require("content");
        String rationale = intent.getPayload().has("rationale")
                ? intent.getPayload().get("rationale") : "File write via OpenClaw";

        // Route through approval gate — no direct writes
        ChangeProposal proposal = new ChangeProposal(
                "Write file: " + filePath,
                content,
                rationale
        );
        String proposalId = approvalGate.submit(proposal);

        return IntentResult.success(id,
                Map.of("proposalId", proposalId, "path", filePath),
                "Proposal " + proposalId + " submitted for file write to " + filePath,
                elapsed(start));
    }

    private IntentResult handleGenerateDiff(String id, Intent intent, long start) {
        String filePath = intent.getPayload().require("path");
        String newContent = intent.getPayload().require("newContent");
        Path resolved = sandbox.getRoot().resolve(filePath);

        try {
            String oldContent = Files.exists(resolved) ? Files.readString(resolved) : "";
            String diff = generateUnifiedDiff(filePath, oldContent, newContent);
            return IntentResult.success(id,
                    Map.of("diff", diff, "path", filePath),
                    "Generated diff for " + filePath,
                    elapsed(start));
        } catch (IOException e) {
            return IntentResult.failure(id, "Diff generation failed: " + e.getMessage(), elapsed(start));
        }
    }

    private IntentResult handleApplyPatch(String id, Intent intent, long start) {
        String filePath = intent.getPayload().require("path");
        String diff = intent.getPayload().require("diff");
        String rationale = intent.getPayload().has("rationale")
                ? intent.getPayload().get("rationale") : "Patch via OpenClaw";

        // Route through approval gate
        ChangeProposal proposal = new ChangeProposal(
                "Apply patch: " + filePath,
                diff,
                rationale
        );
        String proposalId = approvalGate.submit(proposal);

        return IntentResult.success(id,
                Map.of("proposalId", proposalId, "path", filePath),
                "Patch proposal " + proposalId + " submitted for " + filePath,
                elapsed(start));
    }

    private IntentResult handleRunCommand(String id, Intent intent, long start) {
        String command = intent.getPayload().require("command");
        log.info("Sandbox command execution: {}", command);

        // Command execution in sandbox is simulated — actual execution
        // would go through Podman containers
        return IntentResult.success(id,
                Map.of("command", command, "status", "queued"),
                "Command queued for sandbox execution: " + truncate(command, 60),
                elapsed(start));
    }

    private IntentResult handleRunTests(String id, Intent intent, long start) {
        String testTarget = intent.getPayload().has("target")
                ? intent.getPayload().get("target") : "all";
        log.info("Sandbox test execution: {}", testTarget);

        return IntentResult.success(id,
                Map.of("target", testTarget, "status", "queued"),
                "Tests queued for sandbox execution: " + testTarget,
                elapsed(start));
    }

    private String generateUnifiedDiff(String path, String oldContent, String newContent) {
        String[] oldLines = oldContent.split("\n", -1);
        String[] newLines = newContent.split("\n", -1);
        StringBuilder diff = new StringBuilder();
        diff.append("--- a/").append(path).append("\n");
        diff.append("+++ b/").append(path).append("\n");
        diff.append(String.format("@@ -1,%d +1,%d @@\n", oldLines.length, newLines.length));
        for (String line : oldLines) {
            diff.append("-").append(line).append("\n");
        }
        for (String line : newLines) {
            diff.append("+").append(line).append("\n");
        }
        return diff.toString();
    }

    private String truncate(String s, int max) {
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }

    private long elapsed(long start) {
        return System.currentTimeMillis() - start;
    }
}
