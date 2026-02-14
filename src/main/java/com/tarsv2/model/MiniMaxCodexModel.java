package com.tarsv2.model;

import com.tarsv2.llm.LlmClient;

import java.util.Objects;

public final class MiniMaxCodexModel extends AbstractCircuitBreakingModel {

    private final LlmClient llmClient;

    public MiniMaxCodexModel(LlmClient llmClient, int circuitBreakerFailures) {
        super(circuitBreakerFailures);
        this.llmClient = Objects.requireNonNull(llmClient);
    }

    @Override
    protected ModelResponse doGenerate(ModelRequest request) {
        String content = request.structuredJson()
                ? llmClient.completeStructuredJson(request.systemPrompt(), request.userPrompt())
                : llmClient.complete(request.systemPrompt(), request.userPrompt());

        if (content.startsWith("[ERROR]")) {
            return ModelResponse.unavailable(content);
        }
        return ModelResponse.ok(content);
    }
}
