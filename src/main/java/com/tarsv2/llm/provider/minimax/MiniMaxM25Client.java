package com.tarsv2.llm.provider.minimax;

import com.tarsv2.llm.MinimaxClient;
import com.tarsv2.model.ModelRequest;
import com.tarsv2.model.ModelResponse;
import com.tarsv2.model.TarsModel;

import java.time.Duration;

public final class MiniMaxM25Client implements TarsModel {

    private final MinimaxClient delegate;

    public MiniMaxM25Client(String endpoint, String model, Duration timeout) {
        this.delegate = new MinimaxClient(endpoint, model, timeout);
    }

    @Override
    public ModelResponse generate(ModelRequest request) {
        String output = request.structuredJson()
                ? delegate.generateDeterministicDiff(request.userPrompt())
                : delegate.chat(request.userPrompt());
        if (output == null || output.startsWith("[ERROR]")) {
            return ModelResponse.unavailable(output == null ? "MiniMax unavailable" : output);
        }
        return ModelResponse.ok(output);
    }
}
