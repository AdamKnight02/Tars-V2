package com.tarsv2.llm;

import com.tarsv2.model.*;
import com.tarsv2.personality.DialogueStyle;

import java.util.Objects;

/**
 * Compatibility orchestrator that now performs a single deterministic model call.
 */
public final class DualLlmOrchestrator {

    private final ModeRouter modeRouter;
    private final DialogueStyle dialogue;

    public DualLlmOrchestrator(ModeRouter modeRouter, DialogueStyle dialogue) {
        this.modeRouter = Objects.requireNonNull(modeRouter);
        this.dialogue = Objects.requireNonNull(dialogue);
    }

    public OrchestratorResult process(String taskDescription) {
        return process(taskDescription, DialogueStyle.OutputMode.SYSTEM);
    }

    public OrchestratorResult process(String taskDescription, DialogueStyle.OutputMode mode) {
        Mode routeMode = mode == DialogueStyle.OutputMode.CHAT ? Mode.CHAT : Mode.CODEX;
        dialogue.say("Routing request through deterministic mode router", DialogueStyle.OutputMode.SYSTEM);

        ModelResponse response = modeRouter.route(
                routeMode,
                new ModelRequest("Generate deterministic output.", taskDescription, routeMode == Mode.CODEX)
        );

        boolean ok = response.status() == ModelResponse.Status.OK;
        return new OrchestratorResult(ok ? response.content() : response.message(), ok ? 1.0 : 0.0, 1, ok);
    }

    public record OrchestratorResult(String output, double qualityScore, int iterations, boolean passed) {}
}
