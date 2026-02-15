package com.tarsv2.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tarsv2.model.ModelRequest;
import com.tarsv2.model.ModelResponse;
import com.tarsv2.model.router.ModelRouter;
import com.tarsv2.model.router.RoutingMode;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public final class ResearchOrchestrator {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String INVALID_JSON_ERROR = "{\"error\":\"INVALID_JSON\"}";
    private static final String INSUFFICIENT_CONTEXT_ERROR = "{\"error\":\"INSUFFICIENT_CONTEXT\"}";
    private static final Set<String> ALLOWED_RISK_LEVELS = Set.of("LOW", "MODERATE", "HIGH");

    private final ReasoningModelClient glmClient;
    private final ModelRouter modelRouter;
    private final RepositoryContextInjector repositoryContextInjector;

    public ResearchOrchestrator(ReasoningModelClient glmClient) {
        this.glmClient = Objects.requireNonNull(glmClient);
        this.modelRouter = null;
        this.repositoryContextInjector = new RepositoryContextInjector();
    }

    public ResearchOrchestrator(ModelRouter modelRouter) {
        this.glmClient = null;
        this.modelRouter = Objects.requireNonNull(modelRouter);
        this.repositoryContextInjector = new RepositoryContextInjector();
    }

    public String research(String userInput) {
        String topic = normalize(userInput);
        if (isInsufficientContext(topic)) {
            return INSUFFICIENT_CONTEXT_ERROR;
        }

        String prompt = repositoryContextInjector.inject(buildResearchPrompt(topic)).augmentedPrompt();
        String raw;
        if (modelRouter != null) {
            ModelResponse routed = modelRouter.route(RoutingMode.RESEARCH,
                    new ModelRequest("Research mode", prompt, true));
            if (routed.status() == ModelResponse.Status.NOT_IMPLEMENTED) {
                return "{\"error\":\"NOT_IMPLEMENTED\"}";
            }
            raw = routed.status() == ModelResponse.Status.OK ? routed.content() : "";
        } else {
            raw = glmClient.chat(prompt);
            if (raw.startsWith("[NOT_IMPLEMENTED]")) {
                return "{\"error\":\"NOT_IMPLEMENTED\"}";
            }
        }

        JsonNode parsed = parseAndValidateResearchJson(raw);
        return parsed == null ? INVALID_JSON_ERROR : toCanonicalJson(parsed);
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
            return object.path("rollback_instructions").isTextual() ? object : null;
        } catch (Exception e) {
            return null;
        }
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

    private String normalize(String input) {
        return input == null ? "" : input.trim();
    }

    private boolean isInsufficientContext(String input) {
        return input.isBlank() || input.split("\\s+").length < 2;
    }

    private String toCanonicalJson(JsonNode node) {
        try {
            return MAPPER.writeValueAsString(node);
        } catch (Exception e) {
            return INVALID_JSON_ERROR;
        }
    }
}
