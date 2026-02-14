package com.tarsv2.llm;

import com.tarsv2.context.ContextBudget;
import com.tarsv2.context.ContextSummarizer;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ChatOrchestratorTest {

    @Test
    void chatReturnsNonEmptyResponse() {
        Queue<String> responses = new ArrayDeque<>();
        responses.add("Actor reply");
        responses.add("{\"qualityScore\":0.92,\"critique\":\"Clear and accurate\"}");

        LlmService service = (role, systemPrompt, userPrompt) -> responses.remove();
        ChatOrchestrator orchestrator = new ChatOrchestrator(
                service,
                new ChatConfig(0.75),
                new ContextSummarizer(),
                new ContextBudget(8000)
        );

        String result = orchestrator.chat("Hello TARS");

        assertNotNull(result);
        assertFalse(result.isBlank());
        assertEquals("Actor reply", result);
    }

    @Test
    void chatTriggersRevisionWhenScoreIsBelowThreshold() {
        Queue<String> responses = new ArrayDeque<>();
        responses.add("Initial answer");
        responses.add("{\"qualityScore\":0.42,\"critique\":\"Needs concrete steps\"}");
        responses.add("Revised answer with concrete steps");

        LlmService service = (role, systemPrompt, userPrompt) -> responses.remove();
        ChatOrchestrator orchestrator = new ChatOrchestrator(
                service,
                new ChatConfig(0.75),
                new ContextSummarizer(),
                new ContextBudget(8000)
        );

        String result = orchestrator.chat("How should I debug this issue?");

        assertEquals("Revised answer with concrete steps", result);
        assertTrue(responses.isEmpty(), "Expected revision path to consume all mocked responses");
    }

    @Test
    void chatForcesRevisionWhenReflectorContainsDisallowedTerms() {
        Queue<String> responses = new ArrayDeque<>();
        responses.add("Initial answer");
        responses.add("{\"qualityScore\":0.99,\"critique\":\"Mentions Android framework details\"}");
        responses.add("Revised answer grounded in repository");

        LlmService service = (role, systemPrompt, userPrompt) -> responses.remove();
        ChatOrchestrator orchestrator = new ChatOrchestrator(
                service,
                new ChatConfig(0.75),
                new ContextSummarizer(),
                new ContextBudget(8000)
        );

        String result = orchestrator.chat("Explain sandbox behavior");

        assertEquals("Revised answer grounded in repository", result);
        assertTrue(responses.isEmpty());
    }
}
