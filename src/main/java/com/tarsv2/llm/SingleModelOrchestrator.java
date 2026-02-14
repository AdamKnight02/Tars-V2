package com.tarsv2.llm;

import com.tarsv2.personality.DialogueStyle;

import java.util.Objects;

public final class SingleModelOrchestrator {

    private final ReasoningModelClient minimaxClient;
    private final DialogueStyle dialogue;

    public SingleModelOrchestrator(ReasoningModelClient minimaxClient, DialogueStyle dialogue) {
        this.minimaxClient = Objects.requireNonNull(minimaxClient);
        this.dialogue = Objects.requireNonNull(dialogue);
    }

    public OrchestratorResult process(String taskDescription, DialogueStyle.OutputMode mode) {
        return process(taskDescription);
    }

    public OrchestratorResult process(String taskDescription) {
        dialogue.say("Routing request through MiniMax single-pass generation", DialogueStyle.OutputMode.SYSTEM);
        String output = minimaxClient.chat(taskDescription);
        boolean ok = !output.startsWith("[ERROR]");
        return new OrchestratorResult(ok ? output : "", ok, ok ? output : "MiniMax request failed: " + output);
    }

    public record OrchestratorResult(String output, boolean passed, String message) {}
}
