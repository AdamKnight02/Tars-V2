package com.tarsv2.codex;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tarsv2.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public final class CodexOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(CodexOrchestrator.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ModeRouter modeRouter;
    private final Path sandboxRoot;
    private final DeterministicPatchBuilder patchBuilder;

    public CodexOrchestrator(ModeRouter modeRouter) {
        this(modeRouter, Path.of("."), new DeterministicPatchBuilder());
    }

    public CodexOrchestrator(ModeRouter modeRouter, Path sandboxRoot) {
        this(modeRouter, sandboxRoot, new DeterministicPatchBuilder());
    }

    CodexOrchestrator(ModeRouter modeRouter, Path sandboxRoot, DeterministicPatchBuilder patchBuilder) {
        this.modeRouter = Objects.requireNonNull(modeRouter);
        this.sandboxRoot = Objects.requireNonNull(sandboxRoot);
        this.patchBuilder = Objects.requireNonNull(patchBuilder);
    }

    public String generateDiffOnly(String targetFilePath, String task) throws IOException {
        String safeTargetFilePath = requireTargetFilePath(targetFilePath);
        String safeTask = requireTask(task);
        String originalContent = readFileFromSandbox(safeTargetFilePath);

        ModelResponse response = modeRouter.route(
                Mode.CODEX,
                new ModelRequest(
                        "You produce ONLY JSON objects describing file edits.",
                        buildPrompt(safeTask, safeTargetFilePath, originalContent),
                        true
                )
        );

        if (response.status() != ModelResponse.Status.OK) {
            throw new IllegalStateException("CODEX model failed: " + response.message());
        }

        ChangeRequest changeRequest = parseChangeRequest(response.content());
        String diff = patchBuilder.buildUnifiedDiff(safeTargetFilePath, originalContent, changeRequest);
        log.info("Deterministic diff generated for {} ({} chars)", safeTargetFilePath, diff.length());
        return diff;
    }

    private ChangeRequest parseChangeRequest(String structuredJson) {
        try {
            return MAPPER.readValue(structuredJson, ChangeRequest.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("LLM returned invalid change JSON.", e);
        }
    }

    private String buildPrompt(String instruction, String targetFilePath, String fileContent) {
        return "You are a deterministic code change planner.\n"
                + "Target file path:\n" + requireTargetFilePath(targetFilePath)
                + "\n\nCurrent file contents:\n" + Objects.requireNonNull(fileContent)
                + "\n\nTask:\n" + instruction
                + "\n\nReturn ONLY valid JSON with this shape:\n"
                + "{\n"
                + "  \"action\": \"<action identifier>\",\n"
                + "  \"locationHint\": \"<context hint>\",\n"
                + "  \"content\": \"<inserted or replacement text>\"\n"
                + "}\n"
                + "Do not return markdown. Do not return a unified diff.";
    }

    private String requireTask(String task) {
        if (task == null || task.isBlank()) {
            throw new IllegalArgumentException("Task description is required for diff generation.");
        }
        return task;
    }

    private String requireTargetFilePath(String targetFilePath) {
        if (targetFilePath == null || targetFilePath.isBlank()) {
            throw new IllegalArgumentException("Target file path is required for deterministic diff generation.");
        }
        return targetFilePath;
    }

    private String readFileFromSandbox(String targetFilePath) {
        Path filePath = sandboxRoot.resolve(targetFilePath).normalize();
        Path normalizedRoot = sandboxRoot.toAbsolutePath().normalize();
        Path absoluteFile = filePath.toAbsolutePath().normalize();

        if (!absoluteFile.startsWith(normalizedRoot)) {
            throw new IllegalArgumentException("Target file path escapes sandbox root.");
        }

        if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            throw new IllegalArgumentException("Target file not found in sandbox: " + targetFilePath);
        }
        try {
            return Files.readString(filePath);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read sandbox file: " + targetFilePath, e);
        }
    }
}
