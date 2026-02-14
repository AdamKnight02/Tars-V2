package com.tarsv2.llm;

public final class GlmClient implements ReasoningModelClient {
    @Override
    public String chat(String prompt) {
        return "[NOT_IMPLEMENTED] GLM reasoning core is not yet configured.";
    }

    @Override
    public String generateDeterministicDiff(String prompt) {
        return "[NOT_IMPLEMENTED] GLM deterministic diff generation is not yet configured.";
    }
}
