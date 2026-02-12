package com.tarsv2.llm;

/**
 * Role-aware LLM service abstraction for deterministic orchestration and testing.
 */
public interface LlmService {

    /**
     * Generate a response for a role with system and user prompts.
     */
    String generate(LlmRole role, String systemPrompt, String userPrompt);
}
