package com.tarsv2.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public final class ResearchOrchestrator {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_JSON_RETRIES = 3;
    private static final int MAX_QUALITY_REVISIONS = 2;
    private static final double QUALITY_THRESHOLD = 0.75;

    private static final String INVALID_JSON_ERROR = "{\"error\":\"INVALID_JSON\"}";
    private static final String INSUFFICIENT_CONTEXT_ERROR = "{\"error\":\"INSUFFICIENT_CONTEXT\"}";

    private static final Set<String> ALLOWED_RISK_LEVELS = Set.of("LOW", "MODERATE", "HIGH");

    private final LlmService llmService;
    private final RepositoryContextInjector repositoryContextInjector;

    public ResearchOrchestrator(LlmService llmService) {
        this.llmService = Objects.requireNonNull(llmService);
        this.repositoryContextInjector = new RepositoryContextInjector();
    }

    public String research(String userInput) {
        String topic = normalize(userInput);
        if (isInsufficientContext(topic)) {
            return INSUFFICIENT_CONTEXT_ERROR;
        }

        RepositoryContextInjector.InjectionResult injection = repositoryContextInjector.inject(buildResearchPrompt(topic));
        JsonNode candidate = generateValidResearchJson(injection.augmentedPrompt(), topic);
        if (candidate == null) {
            return INVALID_JSON_ERROR;
        }

        for (int revision = 0; revision < MAX_QUALITY_REVISIONS; revision++) {
            ReflectionResult review = scoreResearch(candidate, topic);
            if (review.qualityScore() >= QUALITY_THRESHOLD) {
                return toCanonicalJson(candidate);
            }

            RepositoryContextInjector.InjectionResult revisionInjection = repositoryContextInjector.inject(
                    buildRevisionPrompt(topic, toCanonicalJson(candidate), review.critique()));
            candidate = generateValidResearchJson(revisionInjection.augmentedPrompt(), topic);
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
                    "Return JSON only.",
                    prompt
            );

            JsonNode parsed = parseAndValidateResearchJson(raw);
            if (parsed != null) {
                return parsed;
            }

            prompt = repositoryContextInjector.inject(
                    "Your previous response was invalid. Return ONLY valid JSON. Topic: " + topic).augmentedPrompt();
        }
        return null;
    }

    private ReflectionResult scoreResearch(JsonNode candidate, String topic) {
        String reflectionRaw = llmService.generate(
                LlmRole.REFLECTOR,
                "Score JSON output quality only. Return JSON only.",
                buildScoringPrompt(candidate, topic)
        );

        if (containsHallucinationTrigger(reflectionRaw)) {
            return new ReflectionResult(0.0, "Disallowed content detected by reflector rule");
        }

        try {
            JsonNode root = MAPPER.readTree(reflectionRaw);
            double score = root.path("qualityScore").asDouble(0.0);
            if (score < 0.0 || score > 1.0) {
                score = 0.0;
            }
            String critique = root.path("critique").asText("No critique provided");
            return new ReflectionResult(score, critique);
        } catch (Exception e) {
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
            if (!object.path("affected_files").isArray()) {
                return null;
            }
            if (!object.path("diff").isTextual()) {
                return null;
            }
            if (!ALLOWED_RISK_LEVELS.contains(object.path("risk_level").asText(""))) {
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

    private String buildScoringPrompt(JsonNode candidate, String topic) {
        return repositoryContextInjector.inject(
                "Evaluate research JSON quality. Return JSON with {\"qualityScore\": <number>, \"critique\": \"text\"}.\nTopic: "
                        + topic + "\nCandidate:\n" + toCanonicalJson(candidate)).augmentedPrompt();
    }

    private boolean hasExactFields(ObjectNode object, Set<String> required) {
        Set<String> actual = new LinkedHashSet<>();
        object.fieldNames().forEachRemaining(actual::add);
        return actual.equals(required);
    }

    private String buildResearchPrompt(String topic) {
        return "Generate a technical research proposal for topic: " + topic + " and return JSON only with schema:"
                + "{\"summary\":string,\"affected_files\":[string],\"diff\":string,\"risk_level\":\"LOW|MODERATE|HIGH\",\"rollback_instructions\":string}";
    }

    private String buildRevisionPrompt(String topic, String priorJson, String critique) {
        return "Revise prior proposal and return ONLY JSON. Topic: " + topic + "\nPrior JSON:\n" + priorJson
                + "\nCritique:\n" + critique;
    }

    private String normalize(String input) {
        return input == null ? "" : input.trim();
    }

    private boolean isInsufficientContext(String input) {
        if (input.isBlank()) return true;
        return input.split("\\s+").length < 2;
    }

    private String toCanonicalJson(JsonNode node) {
        try {
            return MAPPER.writeValueAsString(node);
        } catch (Exception e) {
            return INVALID_JSON_ERROR;
        }
    }

    private boolean containsHallucinationTrigger(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String lowered = value.toLowerCase();
        return lowered.contains("android") || lowered.contains("interstellar");
    }

    private record ReflectionResult(double qualityScore, String critique) {}
}
