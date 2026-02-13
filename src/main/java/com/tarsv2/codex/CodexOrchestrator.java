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
     * Generates a unified diff for the given file path and task instruction.
     * Reads the target file from the sandbox and injects its full contents into
     * the prompt for deterministic diff generation against that exact file path.
     *
     * @param targetFilePath relative path to the target file
     * @param taskInstruction the codex instruction describing the desired change
     * @return raw unified diff response from the actor LLM
     */
    public String generateDiffOnly(String targetFilePath, String taskInstruction) {
        String exactTargetFilePath = requireTargetFilePath(targetFilePath);
        if (taskInstruction == null || taskInstruction.isBlank()) {
            throw new IllegalArgumentException("Task description is required for diff generation.");
        }
        String fileContent = readFileFromSandbox(exactTargetFilePath);
        String prompt = buildPrompt(taskInstruction, exactTargetFilePath, fileContent);
        String rawDiff = llmClient.complete(
                "You are a deterministic patch generator.",
                prompt
        );
        log.info("Raw LLM diff output:\n{}", rawDiff);
        return rawDiff;
    }

    /**
     * Returns the output exactly as produced by Codex mode.
     */
    public String extractDiff(String codexOutput) {
        return codexOutput == null ? "" : codexOutput;
    }

    private String buildPrompt(String instruction, String targetFilePath, String fileContent) {
        String exactTargetPath = requireTargetFilePath(targetFilePath);
        String contents = Objects.requireNonNull(fileContent, "fileContent must not be null");

        return "You are a deterministic patch generator.\n"
                + "Target file path:\n"
                + exactTargetPath
                + "\n\n"
                + "Current file contents:\n"
                + contents
                + "\n\n"
                + "Task:\n"
                + instruction
                + "\n\n"
                + "Generate a valid unified diff referencing ONLY the target file path.\n"
                + "- The diff header must be exactly:\n"
                + "  --- a/" + exactTargetPath + "\n"
                + "  +++ b/" + exactTargetPath + "\n"
                + "- Do not use placeholder filenames.\n"
                + "- Do not use names like original.txt.\n"
                + "- Include context lines.\n"
                + "- Do not include markdown.\n"
                + "- Output diff only.";
    }

    private String requireTargetFilePath(String targetFilePath) {
        if (targetFilePath == null || targetFilePath.isBlank()) {
            throw new IllegalArgumentException("Target file path is required for deterministic diff generation.");
        }
        return targetFilePath;
    }

    private String readFileFromSandbox(String targetFilePath) {
        if (targetFilePath == null || targetFilePath.isBlank()) {
            throw new IllegalArgumentException("Target file path is required for deterministic diff generation.");
        }
        Path filePath = sandboxRoot.resolve(targetFilePath);
        if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            throw new IllegalArgumentException("Target file not found in sandbox: " + filePath);
        }
        try {
            String content = Files.readString(filePath);
            log.info("Read {} bytes from sandbox file: {}", content.length(), targetFilePath);
            return content;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read sandbox file: " + filePath, e);
        }
    }

}
