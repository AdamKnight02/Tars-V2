package com.tarsv2.llm;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Production LLM service backed by concrete {@link LlmClient} instances.
 */
public final class DefaultLlmService implements LlmService {

    private final Map<LlmRole, LlmClient> clients;

    public DefaultLlmService(LlmClient actor, LlmClient reflector) {
        Objects.requireNonNull(actor);
        Objects.requireNonNull(reflector);
        if (actor.getRole() != LlmRole.ACTOR) {
            throw new IllegalArgumentException("Actor client must have ACTOR role");
        }
        if (reflector.getRole() != LlmRole.REFLECTOR) {
            throw new IllegalArgumentException("Reflector client must have REFLECTOR role");
        }
        this.clients = new EnumMap<>(LlmRole.class);
        this.clients.put(LlmRole.ACTOR, actor);
        this.clients.put(LlmRole.REFLECTOR, reflector);
    }

    @Override
    public String generate(LlmRole role, String systemPrompt, String userPrompt) {
        LlmClient client = clients.get(role);
        if (client == null) {
            throw new IllegalArgumentException("No client configured for role: " + role);
        }
        return client.complete(systemPrompt, userPrompt);
    }
}
