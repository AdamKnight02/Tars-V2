package com.tarsv2.llm;

import com.tarsv2.context.ContextBudget;
import com.tarsv2.context.ContextSummarizer;

import java.util.Objects;

public final class ChatOrchestrator {

    private final ReasoningModelClient minimaxClient;
    private final ContextSummarizer contextSummarizer;
    private final ContextBudget contextBudget;

    public ChatOrchestrator(
            ReasoningModelClient minimaxClient,
            ContextSummarizer contextSummarizer,
            ContextBudget contextBudget
    ) {
        this.minimaxClient = Objects.requireNonNull(minimaxClient);
        this.contextSummarizer = Objects.requireNonNull(contextSummarizer);
        this.contextBudget = Objects.requireNonNull(contextBudget);
    }

    public String chat(String userInput) {
        String normalizedInput = userInput == null ? "" : userInput.trim();
        if (normalizedInput.isEmpty()) {
            return "Please share a question or instruction, and I'll help.";
        }

        String prompt = "User input:\n" + normalizedInput + "\n\nProvide a direct, useful response.";
        if (contextSummarizer.needsSummarization(prompt, contextBudget)) {
            prompt = contextSummarizer.fitToBudget(prompt, contextBudget);
        }

        String response = minimaxClient.chat(prompt);
        return response.startsWith("[ERROR]") ? "Chat model unavailable: " + response : response;
    }
}
