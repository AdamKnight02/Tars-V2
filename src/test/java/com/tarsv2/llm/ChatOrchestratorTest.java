package com.tarsv2.llm;

import com.tarsv2.context.ContextBudget;
import com.tarsv2.context.ContextSummarizer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChatOrchestratorTest {

    @Test
    void chatReturnsNonEmptyResponse() {
        ChatOrchestrator orchestrator = new ChatOrchestrator(
                new ReasoningModelClient() {
                    @Override
                    public String chat(String prompt) {
                        return "MiniMax reply";
                    }

                    @Override
                    public String generateDeterministicDiff(String prompt) {
                        return "";
                    }
                },
                new ContextSummarizer(),
                new ContextBudget(8000)
        );

        String result = orchestrator.chat("Hello TARS");

        assertNotNull(result);
        assertFalse(result.isBlank());
        assertEquals("MiniMax reply", result);
    }
}
