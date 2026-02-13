package com.tarsv2.codex;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tarsv2.llm.LlmClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public final class CodexOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(CodexOrchestrator.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final LlmClient llmClient;
    private final Path sandboxRoot;
    private final DeterministicPatchBuilder patchBuilder;

    public CodexOrchestrator(LlmClient llmClient) {
        this(llmClient, Path.of("."), new DeterministicPatchBuilder());
    }

    public CodexOrchestrator(LlmClient llmClient, Path sandboxRoot) {
        this(llmClient, sandboxRoot, new DeterministicPatchBuilder());
    }

    CodexOrchestrator(LlmClient llmClient, Path sandboxRoot, DeterministicPatchBuilder patchBuilder) {
        this.llmClient = Objects.requireNonNull(llmClient);
        this.sandboxRoot = Objects.requireNonNull(sandboxRoot);
        this.patchBuilder = Objects.requireNonNull(patchBuilder);
    }

    public String generateDiffOnly(String targetFilePath, String task) {
        String safeTargetFilePath = requireTargetFilePath(targetFilePath);
        String safeTask = requireTask(task);
        String originalContent = readFileFromSandbox(safeTargetFilePath);
        String prompt = buildPrompt(safeTask, safeTargetFilePath, originalContent);
        String structuredJson = llmClient.completeStructuredJson(
                "You produce ONLY JSON objects describing file edits.",
                prompt
        );

        ChangeRequest changeRequest = parseChangeRequest(structuredJson);
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
        String exactTargetPath = requireTargetFilePath(targetFilePath);
        String contents = Objects.requireNonNull(fileContent, "fileContent must not be null");

        return "You are a deterministic code change planner.\n"
                + "Target file path:\n"
                + exactTargetPath
                + "\n\n"
                + "Current file contents:\n"
                + contents
                + "\n\n"
                + "Task:\n"
                + instruction
                + "\n\n"
                + "Return ONLY valid JSON with this shape:\n"
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
            String content = Files.readString(filePath);
            log.info("Read {} bytes from sandbox file: {}", content.length(), targetFilePath);
            return content;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read sandbox file: " + targetFilePath, e);
        }
    }

}
