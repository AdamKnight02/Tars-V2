package com.tarsv2.llm;

@Deprecated
public interface LlmService {
    String generate(LlmRole role, String systemPrompt, String userPrompt);
}
