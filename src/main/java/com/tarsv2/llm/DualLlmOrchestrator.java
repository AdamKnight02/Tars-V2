package com.tarsv2.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tarsv2.personality.DialogueStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Orchestrates Actor/Reflector quality control for engineering and chat flows.
 */
public final class DualLlmOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(DualLlmOrchestrator.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final double QUALITY_THRESHOLD = 0.7;
    private static final int MAX_ITERATIONS = 3;
    private static final int MAX_CHAT_REVISIONS = 2;

    private final LlmClient actor;
    private final LlmClient reflector;
    private final DialogueStyle dialogue;

    public DualLlmOrchestrator(LlmClient actor, LlmClient reflector, DialogueStyle dialogue) {
        this.actor = Objects.requireNonNull(actor);
        this.reflector = Objects.requireNonNull(reflector);
        this.dialogue = Objects.requireNonNull(dialogue);

        if (actor.getRole() != LlmRole.ACTOR) {
            throw new IllegalArgumentException("First client must have ACTOR role");
        }
        if (reflector.getRole() != LlmRole.REFLECTOR) {
            throw new IllegalArgumentException("Second client must have REFLECTOR role");
        }
    }

    public OrchestratorResult process(String taskDescription) {
        return process(taskDescription, DialogueStyle.OutputMode.SYSTEM);
    }

    /**
     * Runs Actor/Reflector processing with behavior scoped to an output mode.
     */
    public OrchestratorResult process(String taskDescription, DialogueStyle.OutputMode mode) {
        return mode == DialogueStyle.OutputMode.CHAT
                ? processChat(taskDescription)
                : processStructured(taskDescription);
    }

    private OrchestratorResult processStructured(String taskDescription) {
        dialogue.say("Starting dual-LLM processing for: " + taskDescription, DialogueStyle.OutputMode.SYSTEM);

        String actorOutput = null;
        String reflectionFeedback = null;
        double qualityScore = 0.0;

        for (int iteration = 1; iteration <= MAX_ITERATIONS; iteration++) {
            String actorPrompt = buildActorPrompt(taskDescription, reflectionFeedback);
            actorOutput = actor.complete(
                    "You are TARS's Actor model. Generate high-quality output for the given task.",
                    actorPrompt
            );

            String reflectionRaw = reflector.complete(
                    "You are TARS's Reflector model. Critique the Actor's output rigorously.",
                    buildReflectorPrompt(taskDescription, actorOutput)
            );

            ReflectionResult reflection = parseReflection(reflectionRaw);
            qualityScore = reflection.qualityScore();
            reflectionFeedback = reflection.critique();

            if (qualityScore >= QUALITY_THRESHOLD) {
                return new OrchestratorResult(actorOutput, qualityScore, iteration, true);
            }
        }

        return new OrchestratorResult(actorOutput, qualityScore, MAX_ITERATIONS, false);
    }

    private OrchestratorResult processChat(String userInput) {
        String answer = actor.complete(
                "You are TARS's Actor. Respond with coherent, technically correct, clear chat output.",
                userInput
        );

        double bestScore = 0.0;
        String best = answer;
        for (int i = 1; i <= MAX_CHAT_REVISIONS; i++) {
            ReflectionResult review = parseReflection(reflector.complete(
                    "You are TARS's Reflector. Score coherence, technical correctness, clarity, hallucination risk.",
                    buildChatReflectorPrompt(userInput, answer)
            ));

            if (review.qualityScore() > bestScore) {
                bestScore = review.qualityScore();
                best = answer;
            }

            if (review.qualityScore() >= QUALITY_THRESHOLD) {
                return new OrchestratorResult(answer, review.qualityScore(), i, true);
            }

            answer = actor.complete(
                    "Revise your previous chat answer to address reflector critique.",
                    "User input:\n" + userInput + "\n\nPrior answer:\n" + answer + "\n\nCritique:\n" + review.critique()
            );
        }

        return new OrchestratorResult(best, bestScore, MAX_CHAT_REVISIONS, false);
    }

    private String buildActorPrompt(String task, String feedback) {
        if (feedback == null) {
            return "Task: " + task;
        }
        return String.format("Task: %s\n\nPrevious attempt received this feedback:\n%s\n\nPlease improve.", task, feedback);
    }

    private String buildReflectorPrompt(String task, String actorOutput) {
        return "Evaluate the actor output and return ONLY valid JSON using this exact schema:\n"
                + "{\"qualityScore\": <number 0.0 to 1.0>, \"critique\": \"<specific feedback>\"}\n\n"
                + "Task:\n" + task + "\n\n"
                + "Actor output:\n" + actorOutput;
    }

    private String buildChatReflectorPrompt(String userInput, String actorOutput) {
        return "Evaluate chat response quality and return ONLY JSON:\n"
                + "{\"qualityScore\": <number 0.0 to 1.0>, \"critique\": \"<feedback mentioning coherence, technical correctness, clarity, hallucination risk>\"}\n\n"
                + "User input:\n" + userInput + "\n\n"
                + "Actor response:\n" + actorOutput;
    }

    private ReflectionResult parseReflection(String reflectionRaw) {
        if (reflectionRaw == null || reflectionRaw.isBlank()) {
            return new ReflectionResult(0.0, "Missing reflection output");
        }

        try {
            JsonNode root = MAPPER.readTree(reflectionRaw);
            double score = root.path("qualityScore").asDouble(0.0);
            if (score < 0.0 || score > 1.0) {
                score = 0.0;
            }
            String critique = root.path("critique").asText(reflectionRaw);
            return new ReflectionResult(score, critique);
        } catch (Exception e) {
            log.warn("Failed to parse reflector JSON score; using fallback revision score");
            return new ReflectionResult(0.0, reflectionRaw);
        }
    }

    private record ReflectionResult(double qualityScore, String critique) {}

    public record OrchestratorResult(
            String output,
            double qualityScore,
            int iterations,
            boolean passed
    ) {}
}
