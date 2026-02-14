package com.tarsv2.llm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResearchOrchestratorTest {

    @Test
    void returnsNotImplementedWhenResearchModelDisabled() {
        ResearchOrchestrator orchestrator = new ResearchOrchestrator(new GlmClient());

        String result = orchestrator.research("Improve CLI help formatting");

        assertEquals("{\"error\":\"NOT_IMPLEMENTED\"}", result);
    }

    @Test
    void returnsInsufficientContextWhenInputIsVague() {
        ResearchOrchestrator orchestrator = new ResearchOrchestrator(new GlmClient());

        String result = orchestrator.research("help");

        assertEquals("{\"error\":\"INSUFFICIENT_CONTEXT\"}", result);
    }

    @Test
    void parsesValidResearchJsonWhenImplementedModelReturnsSchema() {
        ResearchOrchestrator orchestrator = new ResearchOrchestrator(new ReasoningModelClient() {
            @Override
            public String chat(String prompt) {
                return "{\"summary\":\"Improve CLI help output formatting\",\"affected_files\":[\"src/main/java/com/tarsv2/TarsCli.java\"],\"diff\":\"+ aligned help rows\",\"risk_level\":\"LOW\",\"rollback_instructions\":\"Revert help format changes\"}";
            }

            @Override
            public String generateDeterministicDiff(String prompt) {
                return "";
            }
        });

        String result = orchestrator.research("Improve CLI help formatting");

        assertTrue(result.contains("\"summary\""));
        assertTrue(result.contains("\"risk_level\":\"LOW\""));
    }
}
