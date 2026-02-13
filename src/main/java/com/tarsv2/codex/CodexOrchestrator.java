package com.tarsv2.codex;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tarsv2.llm.LlmRole;
import com.tarsv2.llm.LlmService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public final class CodexOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(CodexOrchestrator.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final double QUALITY_THRESHOLD = 0.80;
    private static final int MAX_REVISIONS = 2;

    private static final String STRICT_OUTPUT_REQUIREMENT =
            "\n\nSTRICT OUTPUT REQUIREMENT:\n"
            + "- Produce a valid unified diff.\n"
            + "- Use correct line numbers based on the provided file.\n"
            + "- Include at least 3 lines of context around each change.\n"
            + "- Do NOT fabricate @@ -0,0 blocks.\n"
            + "- Output ONLY the unified diff — no commentary, no JSON, no explanation.\n"
            + "- Do NOT wrap output in BEGIN_DIFF/END_DIFF.\n"
            + "- The diff MUST begin with:\n"
            + "  --- a/<path>\n"
            + "  +++ b/<path>\n"
            + "  @@ -<line>,<count> +<line>,<count> @@\n";

    private final LlmService llmService;
    private final Path sandboxRoot;

    public CodexOrchestrator(LlmService llmService) {
        this(llmService, Path.of("."));
    }

    public CodexOrchestrator(LlmService llmService, Path sandboxRoot) {
        this.llmService = Objects.requireNonNull(llmService);
        this.sandboxRoot = Objects.requireNonNull(sandboxRoot);
    }

    /**
     * Generates a unified diff for the given instruction.
     * If {@code targetFilePath} is provided, reads the file from the sandbox
     * and injects its contents into the prompt for accurate diff generation.
     *
     * @param instruction  the codex instruction describing the desired change
     * @param targetFilePath relative path to the target file (may be null)
     * @return validated unified diff string, or empty string on failure
     */
    public String generateDiffOnly(String instruction, String targetFilePath) {
        String fileContent = readFileFromSandbox(targetFilePath);
        String prompt = buildPrompt(instruction, targetFilePath, fileContent);

        for (int i = 0; i <= MAX_REVISIONS; i++) {
            String actor = llmService.generate(
                    LlmRole.ACTOR,
                    "Return a valid unified diff only. No wrappers, no commentary."
                            + STRICT_OUTPUT_REQUIREMENT,
                    prompt
            );

            log.info("Raw LLM diff output:\n{}", actor);

            // Strip any BEGIN_DIFF/END_DIFF wrappers the LLM might still produce
            String unwrapped = stripEnvelopeWrappers(actor);

            // Extract and validate diff lines
            String cleaned = DiffParser.extractDiffLines(unwrapped);
            if (!DiffParser.hasValidDiffHeader(cleaned)) {
                log.warn("Diff has no valid header after extraction, rejecting (attempt {})", i + 1);
                prompt = "Previous diff was invalid — missing --- a/ and +++ b/ headers. "
                        + "Return a valid unified diff only."
                        + STRICT_OUTPUT_REQUIREMENT;
                if (fileContent != null && !fileContent.isEmpty()) {
                    prompt += "\n\nCURRENT FILE CONTENT:\n" + fileContent;
                }
                continue;
            }

            if (!DiffParser.hasHunkHeaders(cleaned)) {
                log.warn("Diff has no @@ hunk headers, rejecting (attempt {})", i + 1);
                prompt = "Previous diff was invalid — missing @@ hunk headers with line numbers. "
                        + "Return a valid unified diff only."
                        + STRICT_OUTPUT_REQUIREMENT;
                if (fileContent != null && !fileContent.isEmpty()) {
                    prompt += "\n\nCURRENT FILE CONTENT:\n" + fileContent;
                }
                continue;
            }

            if (!DiffParser.hasContextLines(cleaned)) {
                log.warn("Diff has no context lines, rejecting (attempt {})", i + 1);
                prompt = "Previous diff was invalid — must include at least 3 lines of context. "
                        + "Return a valid unified diff only."
                        + STRICT_OUTPUT_REQUIREMENT;
                if (fileContent != null && !fileContent.isEmpty()) {
                    prompt += "\n\nCURRENT FILE CONTENT:\n" + fileContent;
                }
                continue;
            }

            Reflection reflection = reflect(instruction, cleaned);
            if (reflection.score() >= QUALITY_THRESHOLD) {
                return cleaned;
            }

            prompt = "Revise diff to address critique:\n" + reflection.critique()
                    + STRICT_OUTPUT_REQUIREMENT;
            if (fileContent != null && !fileContent.isEmpty()) {
                prompt += "\n\nCURRENT FILE CONTENT:\n" + fileContent;
            }
        }
        return "";
    }

    /**
     * Generates a unified diff for the given instruction without a specific target file.
     */
    public String generateDiffOnly(String instruction) {
        return generateDiffOnly(instruction, null);
    }

    /**
     * Extracts the diff content from raw output.
     * No longer expects BEGIN_DIFF/END_DIFF envelope — returns cleaned diff lines directly.
     */
    public String extractDiff(String codexOutput) {
        if (codexOutput == null || codexOutput.isBlank()) {
            return "";
        }
        String unwrapped = stripEnvelopeWrappers(codexOutput);
        return DiffParser.extractDiffLines(unwrapped);
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

    /**
     * Strips BEGIN_DIFF/END_DIFF wrappers if the LLM still produces them,
     * so downstream parsing works regardless.
     */
    private String stripEnvelopeWrappers(String output) {
        if (output == null) {
            return "";
        }
        String result = output;
        int start = result.indexOf("BEGIN_DIFF");
        int end = result.indexOf("END_DIFF");
        if (start >= 0 && end > start) {
            result = result.substring(start + "BEGIN_DIFF".length(), end).trim();
        }
        return result;
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

    private Reflection reflect(String instruction, String diff) {
        String raw = llmService.generate(
                LlmRole.REFLECTOR,
                "Score codex diff quality and safety. Return JSON only.",
                "Return JSON schema {\"qualityScore\": number, \"critique\": string}.\nInstruction:\n"
                        + instruction + "\nDiff:\n" + diff
        );

        try {
            JsonNode node = MAPPER.readTree(raw);
            double score = node.path("qualityScore").asDouble(0.0);
            String critique = node.path("critique").asText("No critique");
            return new Reflection(score, critique);
        } catch (Exception ignored) {
            return new Reflection(0.0, "Invalid reflector output");
        }
    }

    private record Reflection(double score, String critique) {}
}
