package com.tarsv2.codex;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tarsv2.llm.LlmRole;
import com.tarsv2.llm.LlmService;

import java.util.Objects;

public final class CodexOrchestrator {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final double QUALITY_THRESHOLD = 0.80;
    private static final int MAX_REVISIONS = 2;

    private final LlmService llmService;

    public CodexOrchestrator(LlmService llmService) {
        this.llmService = Objects.requireNonNull(llmService);
    }

    public String generateDiffOnly(String instruction) {
        String prompt = instruction;
        for (int i = 0; i <= MAX_REVISIONS; i++) {
            String actor = llmService.generate(
                    LlmRole.ACTOR,
                    "Return unified diff only with this exact envelope: BEGIN_DIFF ... END_DIFF. No commentary.",
                    prompt
            );

            DiffEnvelope envelope = parseEnvelope(actor);
            if (envelope == null) {
                prompt = "Previous output invalid. Return BEGIN_DIFF/END_DIFF with valid unified diff only.";
                continue;
            }

            Reflection reflection = reflect(instruction, envelope.diff());
            if (reflection.score() >= QUALITY_THRESHOLD) {
                return "BEGIN_DIFF\n" + envelope.diff() + "\nEND_DIFF";
            }

            prompt = "Revise diff to address critique:\n" + reflection.critique();
        }
        return "BEGIN_DIFF\n\nEND_DIFF";
    }

    public String extractDiff(String codexOutput) {
        DiffEnvelope parsed = parseEnvelope(codexOutput);
        return parsed == null ? "" : parsed.diff();
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
        if (diff.isBlank() || !diff.contains("diff --git")) {
            return null;
        }
        return new DiffEnvelope(diff);
    }

    private record Reflection(double score, String critique) {}
    private record DiffEnvelope(String diff) {}
}
