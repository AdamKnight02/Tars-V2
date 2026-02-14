package com.tarsv2.llm;

import java.util.Objects;

@Deprecated
public final class DefaultLlmService implements LlmService {

    private final LlmClient client;

    public DefaultLlmService(LlmClient client) {
        this.client = Objects.requireNonNull(client);
    }

    @Override
    public String generate(LlmRole role, String systemPrompt, String userPrompt) {
        return client.complete(systemPrompt, userPrompt);
    }
}
