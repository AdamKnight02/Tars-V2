package com.tarsv2.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.tarsv2.personality.DialogueStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Orchestrates the dual-LLM Actor/Reflector loop.
 *
 * <p>The orchestrator implements the core cognitive cycle:</p>
 * <ol>
 *   <li><strong>Actor</strong> (LLaMA) generates a plan or code</li>
 *   <li><strong>Reflector</strong> (Qwen) critiques and scores the output</li>
 *   <li>If quality is sufficient, the result proceeds to approval</li>
 *   <li>If not, the Actor receives feedback and iterates</li>
 * </ol>
 *
 * <p>This separation prevents echo-chamber reasoning — the model that
 * writes code is never the same one that evaluates it.</p>
 */
public final class DualLlmOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(DualLlmOrchestrator.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Minimum quality score (0.0–1.0) required to proceed. */
    private static final double QUALITY_THRESHOLD = 0.7;

    /** Maximum Actor–Reflector iterations before giving up. */
    private static final int MAX_ITERATIONS = 3;

    private final LlmClient actor;
    private final LlmClient reflector;
    private final DialogueStyle dialogue;

    /**
     * @param actor     the Actor LLM client (LLaMA)
     * @param reflector the Reflector LLM client (Qwen)
     * @param dialogue  personality formatter
     */
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

    /**
     * Runs the Actor–Reflector loop for a given task.
     *
     * @param taskDescription what the Actor should produce
     * @return the final Actor output after reflection approval
     */
    public OrchestratorResult process(String taskDescription) {
        dialogue.say("Starting dual-LLM processing for: " + taskDescription);

        String actorOutput = null;
        String reflectionFeedback = null;
        double qualityScore = 0.0;

        for (int iteration = 1; iteration <= MAX_ITERATIONS; iteration++) {
            log.info(dialogue.narrate("Iteration " + iteration + "/" + MAX_ITERATIONS));

            // Step 1: Actor generates
            String actorPrompt = buildActorPrompt(taskDescription, reflectionFeedback);
            actorOutput = actor.complete(
                    "You are TARS's Actor model. Generate high-quality output for the given task.",
                    actorPrompt
            );
            log.info(dialogue.narrate("Actor produced " + actorOutput.length() + " chars"));

            // Step 2: Reflector evaluates
            String reflectorPrompt = buildReflectorPrompt(taskDescription, actorOutput);
            String reflectionRaw = reflector.complete(
                    "You are TARS's Reflector model. Critique the Actor's output rigorously.",
                    reflectorPrompt
            );

            ReflectionResult reflection = parseReflection(reflectionRaw);
            qualityScore = reflection.qualityScore();
            reflectionFeedback = reflection.critique();

            log.info(dialogue.narrate(String.format(
                    "Reflector score: %.2f (threshold: %.2f)", qualityScore, QUALITY_THRESHOLD)));

            if (qualityScore >= QUALITY_THRESHOLD) {
                dialogue.say(String.format(
                        "Quality check passed (%.2f) on iteration %d.", qualityScore, iteration));
                return new OrchestratorResult(actorOutput, qualityScore, iteration, true);
            }

            dialogue.say(String.format(
                    "Quality check failed (%.2f). Sending feedback to Actor for iteration %d.",
                    qualityScore, iteration + 1));
        }

        dialogue.say("Max iterations reached. Returning best effort output.");
        return new OrchestratorResult(actorOutput, qualityScore, MAX_ITERATIONS, false);
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

    /**
     * Result of the dual-LLM orchestration process.
     *
     * @param output       the final Actor output
     * @param qualityScore the Reflector's quality score
     * @param iterations   number of iterations performed
     * @param passed       whether the quality threshold was met
     */
    public record OrchestratorResult(
            String output,
            double qualityScore,
            int iterations,
            boolean passed
    ) {}
}
