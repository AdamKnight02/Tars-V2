package com.tarsv2.llm;

import com.tarsv2.context.ContextBudget;
import com.tarsv2.context.ContextSummarizer;
import com.tarsv2.model.*;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ChatOrchestratorTest {

    @Test
    void chatReturnsNonEmptyResponse() {
        ModeRouter router = new ModeRouter(
                new ModelRegistry("llama", "minimax", "disabled", java.time.Duration.ofSeconds(5), 3),
                Map.of("llama", request -> ModelResponse.ok("Actor reply"))
        );

        ChatOrchestrator orchestrator = new ChatOrchestrator(
                router,
                new ContextSummarizer(),
                new ContextBudget(8000)
        );

        String result = orchestrator.chat("Hello TARS");

        assertNotNull(result);
        assertFalse(result.isBlank());
        assertEquals("Actor reply", result);
    }
}
