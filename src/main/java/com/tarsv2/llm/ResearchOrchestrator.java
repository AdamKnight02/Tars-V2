package com.tarsv2.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Deterministic research orchestration that enforces strict JSON-only output.
 */
public final class ResearchOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ResearchOrchestrator.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final int MAX_JSON_RETRIES = 3;
    private static final int MAX_QUALITY_REVISIONS = 2;
    private static final double QUALITY_THRESHOLD = 0.75;

    private static final String INVALID_JSON_ERROR = "{\"error\":\"INVALID_JSON\"}";
    private static final String INSUFFICIENT_CONTEXT_ERROR = "{\"error\":\"INSUFFICIENT_CONTEXT\"}";

    private static final Set<String> ALLOWED_RISK_LEVELS = Set.of("LOW", "MODERATE", "HIGH");

    private final LlmService llmService;

    public ResearchOrchestrator(LlmService llmService) {
        this.llmService = Objects.requireNonNull(llmService);
    }

    public String research(String userInput) {
        String topic = normalize(userInput);
        if (isInsufficientContext(topic)) {
            return INSUFFICIENT_CONTEXT_ERROR;
        }

        String actorPrompt = buildResearchPrompt(topic);
        JsonNode candidate = generateValidResearchJson(actorPrompt, topic);
        if (candidate == null) {
            return INVALID_JSON_ERROR;
        }

        for (int revision = 0; revision < MAX_QUALITY_REVISIONS; revision++) {
            ReflectionResult review = scoreResearch(candidate, topic);
            if (review.qualityScore() >= QUALITY_THRESHOLD) {
                return toCanonicalJson(candidate);
            }

            String revisionPrompt = buildRevisionPrompt(topic, toCanonicalJson(candidate), review.critique());
            candidate = generateValidResearchJson(revisionPrompt, topic);
            if (candidate == null) {
                return INVALID_JSON_ERROR;
            }
        }

        return toCanonicalJson(candidate);
    }

    private JsonNode generateValidResearchJson(String initialPrompt, String topic) {
        String prompt = initialPrompt;
        for (int attempt = 0; attempt <= MAX_JSON_RETRIES; attempt++) {
            String raw = llmService.generate(
                    LlmRole.ACTOR,
                    "You generate deterministic technical research proposals. Return JSON only.",
                    prompt
            );

            JsonNode parsed = parseAndValidateResearchJson(raw);
            if (parsed != null) {
                return parsed;
            }

            prompt = "Your previous response was invalid. Return ONLY valid JSON following the required schema with no extra text.\n"
                    + "Topic: " + topic + "\n"
                    + "Required schema:\n"
                    + "{\"summary\":string,\"affected_files\":[string],\"diff\":string,\"risk_level\":\"LOW|MODERATE|HIGH\",\"rollback_instructions\":string}";
        }
        return null;
    }

    private ReflectionResult scoreResearch(JsonNode candidate, String topic) {
        String reflectionRaw = llmService.generate(
                LlmRole.REFLECTOR,
                "You are a strict quality validator. Score JSON output quality only. Return JSON only.",
                "Evaluate this research JSON for schema compliance, technical specificity, and rollback clarity.\n"
                        + "Return ONLY JSON with schema {\"qualityScore\": <number 0.0 to 1.0>, \"critique\": \"<brief technical critique>\"}.\n"
                        + "Topic: " + topic + "\n"
                        + "Candidate JSON:\n" + toCanonicalJson(candidate)
        );

        try {
            JsonNode root = MAPPER.readTree(reflectionRaw);
            double score = root.path("qualityScore").asDouble(0.0);
            if (score < 0.0 || score > 1.0) {
                score = 0.0;
            }
            String critique = root.path("critique").asText("No critique provided");
            return new ReflectionResult(score, critique);
        } catch (Exception e) {
            log.warn("Invalid reflector output during research scoring");
            return new ReflectionResult(0.0, "Invalid reflection format");
        }
    }

    private JsonNode parseAndValidateResearchJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        try {
            JsonNode root = MAPPER.readTree(raw);
            if (!root.isObject()) {
                return null;
            }

            ObjectNode object = (ObjectNode) root;
            if (!hasExactFields(object, Set.of("summary", "affected_files", "diff", "risk_level", "rollback_instructions"))) {
                return null;
            }

            if (!object.path("summary").isTextual()) {
                return null;
            }

            JsonNode affectedFiles = object.path("affected_files");
            if (!affectedFiles.isArray()) {
                return null;
            }
            ArrayNode files = (ArrayNode) affectedFiles;
            for (JsonNode file : files) {
                if (!file.isTextual()) {
                    return null;
                }
            }

            if (!object.path("diff").isTextual()) {
                return null;
            }

            String riskLevel = object.path("risk_level").asText("");
            if (!ALLOWED_RISK_LEVELS.contains(riskLevel)) {
                return null;
            }

            if (!object.path("rollback_instructions").isTextual()) {
                return null;
            }

            return object;
        } catch (Exception e) {
            return null;
        }
    }

    private boolean hasExactFields(ObjectNode object, Set<String> required) {
        Set<String> actual = new LinkedHashSet<>();
        Iterator<String> fields = object.fieldNames();
        while (fields.hasNext()) {
            actual.add(fields.next());
        }
        return actual.equals(required);
    }

    private String buildResearchPrompt(String topic) {
        return "Generate a technical research proposal for the following topic and return JSON only.\n"
                + "Topic: " + topic + "\n"
                + "Schema:\n"
                + "{\"summary\":string,\"affected_files\":[string],\"diff\":string,\"risk_level\":\"LOW|MODERATE|HIGH\",\"rollback_instructions\":string}";
    }

    private String buildRevisionPrompt(String topic, String priorJson, String critique) {
        return "Revise the prior proposal using critique and return ONLY JSON with the required schema.\n"
                + "Topic: " + topic + "\n"
                + "Prior JSON:\n" + priorJson + "\n"
                + "Critique:\n" + critique + "\n"
                + "Schema:\n"
                + "{\"summary\":string,\"affected_files\":[string],\"diff\":string,\"risk_level\":\"LOW|MODERATE|HIGH\",\"rollback_instructions\":string}";
    }

    private String normalize(String input) {
        return input == null ? "" : input.trim();
    }

    private boolean isInsufficientContext(String input) {
        if (input.isBlank()) {
            return true;
        }

        String lowered = input.toLowerCase();
        if (Set.of("help", "improve", "research", "something better", "fix it").contains(lowered)) {
            return true;
        }

        return input.split("\\s+").length < 3;
    }

    private String toCanonicalJson(JsonNode node) {
        try {
            return MAPPER.writeValueAsString(node);
        } catch (Exception e) {
            return INVALID_JSON_ERROR;
        }
    }

    private record ReflectionResult(double qualityScore, String critique) {}
}
