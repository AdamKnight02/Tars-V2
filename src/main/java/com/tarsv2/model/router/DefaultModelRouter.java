package com.tarsv2.model.router;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tarsv2.model.ModelRequest;
import com.tarsv2.model.ModelResponse;
import com.tarsv2.model.TarsModel;
import com.tarsv2.model.config.ModelConfig;

import java.util.Map;
import java.util.Objects;

public final class DefaultModelRouter implements ModelRouter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ModelConfig config;
    private final Map<String, TarsModel> models;

    public DefaultModelRouter(ModelConfig config, Map<String, TarsModel> models) {
        this.config = Objects.requireNonNull(config);
        this.models = Map.copyOf(models);
    }

    @Override
    public ModelResponse route(RoutingMode mode, ModelRequest request) {
        String primaryModelId = switch (mode) {
            case CHAT -> config.bindings().chatModel();
            case CODEX -> config.bindings().codexModel();
            case RESEARCH -> config.bindings().researchModel();
        };

        TarsModel primaryModel = models.get(primaryModelId);
        if (primaryModel == null) {
            return ModelResponse.unavailable("No model configured for mode " + mode + ": " + primaryModelId);
        }

        ModelResponse firstAttempt = primaryModel.generate(request);
        if (isMiniMaxTimeout(primaryModelId, firstAttempt)) {
            ModelResponse secondAttempt = primaryModel.generate(request);
            if (isMiniMaxTimeout(primaryModelId, secondAttempt)) {
                ModelResponse glmResponse = callModel(config.bindings().researchModel(), request);
                if (isGlmBilling(glmResponse)) {
                    ModelResponse minimaxRecovery = primaryModel.generate(request);
                    if (minimaxRecovery.status() == ModelResponse.Status.OK) {
                        return minimaxRecovery;
                    }
                    return noAvailableModels();
                }
                if (glmResponse.status() == ModelResponse.Status.OK) {
                    return glmResponse;
                }
                return noAvailableModels();
            }
            return secondAttempt;
        }

        return firstAttempt;
    }

    private ModelResponse callModel(String modelId, ModelRequest request) {
        TarsModel model = models.get(modelId);
        if (model == null) {
            return ModelResponse.unavailable("No model configured: " + modelId);
        }
        return model.generate(request);
    }

    private boolean isMiniMaxTimeout(String modelId, ModelResponse response) {
        return looksLikeMiniMax(modelId)
                && response.status() == ModelResponse.Status.TIMEOUT
                && hasErrorType(response.message(), "TIMEOUT");
    }

    private boolean isGlmBilling(ModelResponse response) {
        return response.status() == ModelResponse.Status.NOT_IMPLEMENTED
                && hasErrorType(response.message(), "BILLING");
    }

    private boolean looksLikeMiniMax(String modelId) {
        if (modelId == null) {
            return false;
        }
        String lower = modelId.toLowerCase();
        return lower.contains("minimax") || lower.contains("m2.5") || lower.equals("m25");
    }

    private boolean hasErrorType(String payload, String errorType) {
        if (payload == null || payload.isBlank()) {
            return false;
        }
        try {
            JsonNode root = MAPPER.readTree(payload);
            return errorType.equals(root.path("error_type").asText(""));
        } catch (Exception e) {
            return false;
        }
    }

    private ModelResponse noAvailableModels() {
        return ModelResponse.unavailable("{\"error\":\"NO_AVAILABLE_MODELS\"}");
    }
}
