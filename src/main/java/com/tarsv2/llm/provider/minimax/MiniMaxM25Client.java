package com.tarsv2.llm.provider.minimax;

import com.tarsv2.llm.MinimaxClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tarsv2.llm.LlmErrorType;
import com.tarsv2.model.ModelRequest;
import com.tarsv2.model.ModelResponse;
import com.tarsv2.model.TarsModel;

import java.time.Duration;

public final class MiniMaxM25Client implements TarsModel {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final MinimaxClient delegate;

    public MiniMaxM25Client(String endpoint, String model, Duration timeout) {
        this.delegate = new MinimaxClient(endpoint, model, timeout);
    }

    @Override
    public ModelResponse generate(ModelRequest request) {
        String output = request.structuredJson()
                ? delegate.generateDeterministicDiff(request.userPrompt())
                : delegate.chat(request.userPrompt());
        if (output == null) {
            return ModelResponse.unavailable("MiniMax unavailable");
        }
        JsonNode error = parseError(output);
        if (error != null) {
            LlmErrorType errorType = LlmErrorType.valueOf(error.path("error_type").asText("UNKNOWN"));
            if (errorType == LlmErrorType.TIMEOUT) {
                return ModelResponse.timeout(output);
            }
            return ModelResponse.unavailable(output);
        }
        if (output.startsWith("[ERROR]")) {
            return ModelResponse.unavailable(output);
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

