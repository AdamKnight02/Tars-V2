package com.tarsv2.llm;

import com.tarsv2.context.ContextBudget;
import com.tarsv2.context.ContextSummarizer;
import com.tarsv2.model.*;

import java.util.Objects;

public final class ChatOrchestrator {

    private final ModeRouter modeRouter;
    private final ContextSummarizer contextSummarizer;
    private final ContextBudget contextBudget;

    public ChatOrchestrator(
            ModeRouter modeRouter,
            ContextSummarizer contextSummarizer,
            ContextBudget contextBudget
    ) {
        this.modeRouter = Objects.requireNonNull(modeRouter);
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

        ModelResponse response = modeRouter.route(
                Mode.CHAT,
                new ModelRequest("You are TARS chat mode.", prompt, false)
        );
        return response.status() == ModelResponse.Status.OK
                ? response.content()
                : "Chat model unavailable: " + response.message();
    }
}
