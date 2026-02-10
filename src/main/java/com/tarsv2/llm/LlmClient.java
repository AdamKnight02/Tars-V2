package com.tarsv2.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Client for communicating with a local or remote LLM endpoint.
 *
 * <p>Each instance is bound to a specific {@link LlmRole} (Actor or Reflector)
 * and talks to the corresponding model endpoint. The dual-client setup
 * ensures TARS never uses the same model to both generate and evaluate
 * its own output.</p>
 *
 * <p>TODO: Implement actual HTTP calls to Ollama / vLLM / other backends.
 * Current implementation returns placeholder responses for scaffold testing.</p>
 */
public final class LlmClient {

    private static final Logger log = LoggerFactory.getLogger(LlmClient.class);

    private final LlmRole role;
    private final String endpoint;

    /**
     * @param role     the LLM role this client serves
     * @param endpoint the model API endpoint (e.g., "http://localhost:11434/api/generate")
     */
    public LlmClient(LlmRole role, String endpoint) {
        this.role = Objects.requireNonNull(role);
        this.endpoint = Objects.requireNonNull(endpoint);
    }

    /**
     * Sends a prompt to the LLM and returns the response.
     *
     * @param systemPrompt the system-level instruction
     * @param userPrompt   the user/task-level prompt
     * @return the model's response text
     */
    public String complete(String systemPrompt, String userPrompt) {
        log.info("[{}] Sending prompt to {} at {}", role, role.getModelFamily(), endpoint);
        log.debug("[{}] System: {}", role, systemPrompt);
        log.debug("[{}] User: {}", role, userPrompt);

        // TODO: Replace with actual OkHttp call to Ollama/vLLM endpoint
        // Example Ollama payload:
        // {
        //   "model": "llama3",
        //   "system": systemPrompt,
        //   "prompt": userPrompt,
        //   "stream": false
        // }

        String placeholder = String.format(
                "[%s:%s] Placeholder response for: %.80s...",
                role, role.getModelFamily(), userPrompt
        );
        log.info("[{}] Received response ({} chars)", role, placeholder.length());
        return placeholder;
    }

    /**
     * Returns the role this client serves.
     *
     * @return the LLM role
     */
    public LlmRole getRole() {
        return role;
    }

    /**
     * Returns the endpoint URL.
     *
     * @return the API endpoint
     */
    public String getEndpoint() {
        return endpoint;
    }
}
