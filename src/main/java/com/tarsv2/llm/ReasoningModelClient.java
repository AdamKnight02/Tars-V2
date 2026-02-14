package com.tarsv2.llm;

public interface ReasoningModelClient {
    String chat(String prompt);
    String generateDeterministicDiff(String prompt);
}
