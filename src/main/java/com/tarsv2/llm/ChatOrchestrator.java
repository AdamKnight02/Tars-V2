package com.tarsv2.llm;

import com.tarsv2.context.ContextBudget;
import com.tarsv2.context.ContextSummarizer;
import com.tarsv2.model.ModelRequest;
import com.tarsv2.model.ModelResponse;
import com.tarsv2.model.router.ModelRouter;
import com.tarsv2.model.router.RoutingMode;

import java.util.Objects;

public final class ChatOrchestrator {

    private final ReasoningModelClient minimaxClient;
    private final ModelRouter modelRouter;
    private final ContextSummarizer contextSummarizer;
    private final ContextBudget contextBudget;

    public ChatOrchestrator(
            ReasoningModelClient minimaxClient,
            ContextSummarizer contextSummarizer,
            ContextBudget contextBudget
    ) {
        this.minimaxClient = Objects.requireNonNull(minimaxClient);
        this.modelRouter = null;
        this.contextSummarizer = Objects.requireNonNull(contextSummarizer);
        this.contextBudget = Objects.requireNonNull(contextBudget);
    }

    public ChatOrchestrator(
            ModelRouter modelRouter,
            ContextSummarizer contextSummarizer,
            ContextBudget contextBudget
    ) {
        this.minimaxClient = null;
        this.modelRouter = Objects.requireNonNull(modelRouter);
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

        String response;
        if (modelRouter != null) {
            ModelResponse routed = modelRouter.route(RoutingMode.CHAT,
                    new ModelRequest("Minimal chat mode", prompt, false));
            response = routed.status() == ModelResponse.Status.OK ? routed.content() : "[ERROR] " + routed.message();
        } else {
            response = minimaxClient.chat(prompt);
        }
        return response.startsWith("[ERROR]") ? "Chat model unavailable: " + response : response;
    }
}
