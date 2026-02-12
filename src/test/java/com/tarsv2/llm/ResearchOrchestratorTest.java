package com.tarsv2.llm;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResearchOrchestratorTest {

    @Test
    void returnsValidResearchJsonWhenActorOutputIsValid() {
        Queue<String> responses = new ArrayDeque<>();
        responses.add("{\"summary\":\"Improve CLI help output formatting\",\"affected_files\":[\"src/main/java/com/tarsv2/TarsCli.java\"],\"diff\":\"+ aligned help rows\",\"risk_level\":\"LOW\",\"rollback_instructions\":\"Revert help format changes\"}");
        responses.add("{\"qualityScore\":0.95,\"critique\":\"Schema compliant and specific\"}");

        LlmService service = (role, systemPrompt, userPrompt) -> responses.remove();
        ResearchOrchestrator orchestrator = new ResearchOrchestrator(service);

        String result = orchestrator.research("Improve CLI help formatting");

        assertEquals("{\"summary\":\"Improve CLI help output formatting\",\"affected_files\":[\"src/main/java/com/tarsv2/TarsCli.java\"],\"diff\":\"+ aligned help rows\",\"risk_level\":\"LOW\",\"rollback_instructions\":\"Revert help format changes\"}", result);
        assertTrue(responses.isEmpty());
    }

    @Test
    void retriesWhenActorReturnsInvalidJsonBeforeSucceeding() {
        Queue<String> responses = new ArrayDeque<>();
        responses.add("not-json");
        responses.add("{\"summary\":\"Improve CLI help output formatting\",\"affected_files\":[\"src/main/java/com/tarsv2/TarsCli.java\"],\"diff\":\"+ aligned help rows\",\"risk_level\":\"LOW\",\"rollback_instructions\":\"Revert help format changes\"}");
        responses.add("{\"qualityScore\":0.9,\"critique\":\"Valid output\"}");

        LlmService service = (role, systemPrompt, userPrompt) -> responses.remove();
        ResearchOrchestrator orchestrator = new ResearchOrchestrator(service);

        String result = orchestrator.research("Improve CLI help formatting");

        assertTrue(result.contains("\"summary\""));
        assertTrue(responses.isEmpty());
    }

    @Test
    void returnsInvalidJsonErrorAfterRetryExhaustion() {
        Queue<String> responses = new ArrayDeque<>();
        responses.add("invalid");
        responses.add("still invalid");
        responses.add("not json");
        responses.add("wrong again");

        LlmService service = (role, systemPrompt, userPrompt) -> responses.remove();
        ResearchOrchestrator orchestrator = new ResearchOrchestrator(service);

        String result = orchestrator.research("Improve CLI help formatting");

        assertEquals("{\"error\":\"INVALID_JSON\"}", result);
    }

    @Test
    void returnsInsufficientContextWhenInputIsVague() {
        LlmService service = (role, systemPrompt, userPrompt) -> "{}";
        ResearchOrchestrator orchestrator = new ResearchOrchestrator(service);

        String result = orchestrator.research("help");

        assertEquals("{\"error\":\"INSUFFICIENT_CONTEXT\"}", result);
    }
}
