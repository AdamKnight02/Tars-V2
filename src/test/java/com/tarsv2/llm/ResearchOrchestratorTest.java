package com.tarsv2.llm;

import com.tarsv2.model.*;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResearchOrchestratorTest {

    @Test
    void returnsNotImplementedWhenResearchModelDisabled() {
        ModeRouter router = new ModeRouter(
                new ModelRegistry("llama", "minimax", "disabled", Duration.ofSeconds(5), 3),
                Map.of("disabled", new GlmResearchModel())
        );
        ResearchOrchestrator orchestrator = new ResearchOrchestrator(router);

        String result = orchestrator.research("Improve CLI help formatting");

        assertEquals("{\"error\":\"NOT_IMPLEMENTED\"}", result);
    }

    @Test
    void returnsInsufficientContextWhenInputIsVague() {
        ModeRouter router = new ModeRouter(
                new ModelRegistry("llama", "minimax", "disabled", Duration.ofSeconds(5), 3),
                Map.of("disabled", new GlmResearchModel())
        );
        ResearchOrchestrator orchestrator = new ResearchOrchestrator(router);

        String result = orchestrator.research("help");

        assertEquals("{\"error\":\"INSUFFICIENT_CONTEXT\"}", result);
    }

    @Test
    void parsesValidResearchJsonWhenImplementedModelReturnsSchema() {
        ModeRouter router = new ModeRouter(
                new ModelRegistry("llama", "minimax", "glm", Duration.ofSeconds(5), 3),
                Map.of("glm", request -> ModelResponse.ok("{\"summary\":\"Improve CLI help output formatting\",\"affected_files\":[\"src/main/java/com/tarsv2/TarsCli.java\"],\"diff\":\"+ aligned help rows\",\"risk_level\":\"LOW\",\"rollback_instructions\":\"Revert help format changes\"}"))
        );
        ResearchOrchestrator orchestrator = new ResearchOrchestrator(router);

        String result = orchestrator.research("Improve CLI help formatting");

        assertTrue(result.contains("\"summary\""));
        assertTrue(result.contains("\"risk_level\":\"LOW\""));
    }
}
