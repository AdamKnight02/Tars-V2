package com.tarsv2.model;

import com.tarsv2.llm.LlmClient;

import java.util.Objects;

public final class LlamaChatModel extends AbstractCircuitBreakingModel {

    private final LlmClient llmClient;

    public LlamaChatModel(LlmClient llmClient, int circuitBreakerFailures) {
        super(circuitBreakerFailures);
        this.llmClient = Objects.requireNonNull(llmClient);
    }

    @Override
    protected ModelResponse doGenerate(ModelRequest request) {
        String content = llmClient.complete(request.systemPrompt(), request.userPrompt());
        if (content.startsWith("[ERROR]")) {
            return ModelResponse.unavailable(content);
        }
        return ModelResponse.ok(content);
    }
}
