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
            + "Return ONLY a valid unified git diff.\n"
            + "Must begin with:\n"
            + "--- a/<path>\n"
            + "+++ b/<path>\n"
            + "@@\n"
            + "No commentary.\n"
            + "No JSON.\n"
            + "No explanation.\n"
            + "Only diff.";

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
     * @return envelope-wrapped diff or empty envelope on failure
     */
    public String generateDiffOnly(String instruction, String targetFilePath) {
        String fileContent = readFileFromSandbox(targetFilePath);
        String prompt = buildPrompt(instruction, targetFilePath, fileContent);

        for (int i = 0; i <= MAX_REVISIONS; i++) {
            String actor = llmService.generate(
                    LlmRole.ACTOR,
                    "Return unified diff only with this exact envelope: BEGIN_DIFF ... END_DIFF. No commentary."
                            + STRICT_OUTPUT_REQUIREMENT,
                    prompt
            );

            log.info("Raw LLM diff output:\n{}", actor);

            DiffEnvelope envelope = parseEnvelope(actor);
            if (envelope == null) {
                prompt = "Previous output invalid. Return BEGIN_DIFF/END_DIFF with valid unified diff only."
                        + STRICT_OUTPUT_REQUIREMENT;
                continue;
            }

            // Extract and validate diff lines
            String cleaned = DiffParser.extractDiffLines(envelope.diff());
            if (!DiffParser.hasValidDiffHeader(cleaned)) {
                log.warn("Diff has no valid header after extraction, rejecting (attempt {})", i + 1);
                prompt = "Previous diff was invalid — missing --- a/ and +++ b/ headers. "
                        + "Return a valid unified diff only."
                        + STRICT_OUTPUT_REQUIREMENT;
                continue;
            }

            Reflection reflection = reflect(instruction, cleaned);
            if (reflection.score() >= QUALITY_THRESHOLD) {
                return "BEGIN_DIFF\n" + cleaned + "\nEND_DIFF";
            }

            prompt = "Revise diff to address critique:\n" + reflection.critique()
                    + STRICT_OUTPUT_REQUIREMENT;
        }
        return "BEGIN_DIFF\n\nEND_DIFF";
    }

    /**
     * Generates a unified diff for the given instruction without a specific target file.
     */
    public String generateDiffOnly(String instruction) {
        return generateDiffOnly(instruction, null);
    }

    public String extractDiff(String codexOutput) {
        DiffEnvelope parsed = parseEnvelope(codexOutput);
        return parsed == null ? "" : parsed.diff();
    }

    private String buildPrompt(String instruction, String targetFilePath, String fileContent) {
        StringBuilder sb = new StringBuilder();

        if (fileContent != null && !fileContent.isEmpty()) {
            sb.append("-------------------\n");
            sb.append("CURRENT FILE CONTENT:\n");
            sb.append(fileContent).append("\n");
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

    private DiffEnvelope parseEnvelope(String output) {
        if (output == null) {
            return null;
        }
        int start = output.indexOf("BEGIN_DIFF");
        int end = output.indexOf("END_DIFF");
        if (start < 0 || end <= start) {
            return null;
        }
        String diff = output.substring(start + "BEGIN_DIFF".length(), end).trim();
        if (diff.isBlank()) {
            return null;
        }
        return new DiffEnvelope(diff);
    }

    private record Reflection(double score, String critique) {}
    private record DiffEnvelope(String diff) {}
}
