package com.tarsv2.llm.provider.glm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tarsv2.llm.GlmClient;
import com.tarsv2.llm.LlmErrorType;
import com.tarsv2.model.ModelRequest;
import com.tarsv2.model.ModelResponse;
import com.tarsv2.model.TarsModel;

public final class GlmResearchClient implements TarsModel {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final GlmClient delegate;

    public GlmResearchClient() {
        this.delegate = new GlmClient();
    }

    @Override
    public ModelResponse generate(ModelRequest request) {
        String output = delegate.chat(request.userPrompt());
        if (output == null) {
            return ModelResponse.notImplemented("GLM unavailable");
        }
        JsonNode error = parseError(output);
        if (error != null) {
            LlmErrorType errorType = LlmErrorType.valueOf(error.path("error_type").asText("UNKNOWN"));
            if (errorType == LlmErrorType.BILLING) {
                return ModelResponse.notImplemented(output);
            }
            if (errorType == LlmErrorType.TIMEOUT) {
                return ModelResponse.timeout(output);
            }
            return ModelResponse.unavailable(output);
        }
        if (output.startsWith("[NOT_IMPLEMENTED]")) {
            return ModelResponse.notImplemented(output);
        }
        return ModelResponse.ok(output);
    }

    private JsonNode parseError(String output) {
        try {
            JsonNode root = MAPPER.readTree(output);
            return root.has("error_type") ? root : null;
        } catch (Exception e) {
            return null;
        }
    }
}

