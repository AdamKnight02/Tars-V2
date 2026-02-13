package com.tarsv2.codex;

import com.tarsv2.llm.LlmClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public final class CodexOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(CodexOrchestrator.class);
    private static final String STRICT_OUTPUT_REQUIREMENT =
            "\n\nSTRICT OUTPUT REQUIREMENT:\n"
            + "- Output ONLY the unified diff — no commentary, no JSON, no explanation.\n"
            + "- Emit a unified diff only.\n"
            + "- The very first line must be exactly: --- a/src/...\n";

    private final LlmClient llmClient;
    private final Path sandboxRoot;

    public CodexOrchestrator(LlmClient llmClient) {
        this(llmClient, Path.of("."));
    }

    public CodexOrchestrator(LlmClient llmClient, Path sandboxRoot) {
        this.llmClient = Objects.requireNonNull(llmClient);
        this.sandboxRoot = Objects.requireNonNull(sandboxRoot);
    }

    /**
     * Generates a unified diff for the given instruction.
     * If {@code targetFilePath} is provided, reads the file from the sandbox
     * and injects its contents into the prompt for accurate diff generation.
     *
     * @param instruction  the codex instruction describing the desired change
     * @param targetFilePath relative path to the target file (may be null)
     * @return raw unified diff response from the actor LLM
     */
    public String generateDiffOnly(String instruction, String targetFilePath) {
        String fileContent = readFileFromSandbox(targetFilePath);
        String prompt = buildPrompt(instruction, targetFilePath, fileContent);
        String rawDiff = llmClient.complete(
                "Return a unified diff only." + STRICT_OUTPUT_REQUIREMENT,
                prompt
        );
        log.info("Raw LLM diff output:\n{}", rawDiff);
        return rawDiff;
    }

    /**
     * Generates a unified diff for the given instruction without a specific target file.
     */
    public String generateDiffOnly(String instruction) {
        return generateDiffOnly(instruction, null);
    }

    /**
     * Returns the output exactly as produced by Codex mode.
     */
    public String extractDiff(String codexOutput) {
        return codexOutput == null ? "" : codexOutput;
    }

    private String buildPrompt(String instruction, String targetFilePath, String fileContent) {
        StringBuilder sb = new StringBuilder();

        if (fileContent != null && !fileContent.isEmpty()) {
            sb.append("Below is the CURRENT content of the file to be modified.\n");
            sb.append("Use these exact contents to produce correct line numbers in your diff.\n\n");
            sb.append("-------------------\n");
            sb.append("CURRENT FILE CONTENT (").append(targetFilePath).append("):\n");
            sb.append(numberLines(fileContent)).append("\n");
            sb.append("-------------------\n\n");
        }

        sb.append("-------------------\n");
        sb.append("INSTRUCTION:\n");
        sb.append(instruction).append("\n");
        sb.append("-------------------\n");

        if (targetFilePath != null) {
            sb.append("\nTarget file path: ").append(targetFilePath).append("\n");
        }

        sb.append(STRICT_OUTPUT_REQUIREMENT);

        return sb.toString();
    }

    /**
     * Adds line numbers to file content so the LLM can reference exact lines.
     */
    private String numberLines(String content) {
        String[] lines = content.split("\\R", -1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            sb.append(String.format("%4d | %s\n", i + 1, lines[i]));
        }
        return sb.toString();
    }

    private String readFileFromSandbox(String targetFilePath) {
        if (targetFilePath == null || targetFilePath.isBlank()) {
            return null;
        }
        Path filePath = sandboxRoot.resolve(targetFilePath);
        if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            log.warn("Target file not found in sandbox: {}", filePath);
            return null;
        }
        try {
            String content = Files.readString(filePath);
            log.info("Read {} bytes from sandbox file: {}", content.length(), targetFilePath);
            return content;
        } catch (IOException e) {
            log.error("Failed to read sandbox file: {}", filePath, e);
            return null;
        }
    }

}
