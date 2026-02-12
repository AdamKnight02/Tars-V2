package com.tarsv2.llm;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicReference;

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

    @Test
    void revisionOccursWhenReflectorContainsDisallowedTerms() {
        Queue<String> responses = new ArrayDeque<>();
        responses.add("{\"summary\":\"Improve sandbox dispatcher behavior\",\"affected_files\":[\"src/main/java/com/tarsv2/environment/SandboxDispatcher.java\"],\"diff\":\"+ tighten dispatch checks\",\"risk_level\":\"LOW\",\"rollback_instructions\":\"Revert sandbox dispatcher updates\"}");
        responses.add("{\"qualityScore\":0.99,\"critique\":\"Interstellar reference appeared\"}");
        responses.add("{\"summary\":\"Improve sandbox dispatcher behavior\",\"affected_files\":[\"src/main/java/com/tarsv2/environment/SandboxDispatcher.java\"],\"diff\":\"+ tighten dispatch checks with repository classes\",\"risk_level\":\"LOW\",\"rollback_instructions\":\"Revert sandbox dispatcher updates\"}");
        responses.add("{\"qualityScore\":0.91,\"critique\":\"Grounded and specific\"}");

        LlmService service = (role, systemPrompt, userPrompt) -> responses.remove();
        ResearchOrchestrator orchestrator = new ResearchOrchestrator(service);

        String result = orchestrator.research("Improve sandbox dispatcher behavior");

        assertTrue(result.contains("repository classes"));
        assertTrue(responses.isEmpty());
    }

    @Test
    void researchInjectsRepositoryStructureForKeywordTopic() {
        Queue<String> responses = new ArrayDeque<>();
        responses.add("{\"summary\":\"Improve sandbox dispatcher behavior\",\"affected_files\":[\"src/main/java/com/tarsv2/environment/SandboxDispatcher.java\"],\"diff\":\"+ tighten dispatch checks\",\"risk_level\":\"LOW\",\"rollback_instructions\":\"Revert sandbox dispatcher updates\"}");
        responses.add("{\"qualityScore\":0.95,\"critique\":\"Grounded and specific\"}");

        AtomicReference<String> actorPromptCapture = new AtomicReference<>("");
        LlmService service = (role, systemPrompt, userPrompt) -> {
            if (role == LlmRole.ACTOR && actorPromptCapture.get().isEmpty()) {
                actorPromptCapture.set(userPrompt);
            }
            return responses.remove();
        };

        ResearchOrchestrator orchestrator = new ResearchOrchestrator(service);
        String result = orchestrator.research("Improve Sandbox Environment dispatch behavior");

        assertTrue(result.contains("sandbox dispatcher behavior"));
        assertTrue(actorPromptCapture.get().contains("REPOSITORY CONTEXT:"));
    }
}
