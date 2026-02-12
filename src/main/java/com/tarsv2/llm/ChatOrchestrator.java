package com.tarsv2.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tarsv2.context.ContextBudget;
import com.tarsv2.context.ContextSummarizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Interactive dual-LLM chat orchestration for free-text CLI dialogue.
 */
public final class ChatOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ChatOrchestrator.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final LlmService llmService;
    private final ChatConfig chatConfig;
    private final ContextSummarizer contextSummarizer;
    private final ContextBudget contextBudget;

    public ChatOrchestrator(
            LlmService llmService,
            ChatConfig chatConfig,
            ContextSummarizer contextSummarizer,
            ContextBudget contextBudget
    ) {
        this.llmService = Objects.requireNonNull(llmService);
        this.chatConfig = Objects.requireNonNull(chatConfig);
        this.contextSummarizer = Objects.requireNonNull(contextSummarizer);
        this.contextBudget = Objects.requireNonNull(contextBudget);
    }

    /**
     * Handles one chat turn with Actor->Reflector->optional-Actor-revision loop.
     */
    public String chat(String userInput) {
        String normalizedInput = normalizeInput(userInput);
        if (normalizedInput.isEmpty()) {
            return "Please share a question or instruction, and I'll help.";
        }

        String actorPrompt = fitPromptToBudget(buildActorPrompt(normalizedInput));
        String actorAnswer = llmService.generate(
                LlmRole.ACTOR,
                "You are TARS's Actor. Respond helpfully, accurately, and concisely.",
                actorPrompt
        );

        String reflectorPrompt = fitPromptToBudget(buildReflectorPrompt(normalizedInput, actorAnswer));
        String reflectionRaw = llmService.generate(
                LlmRole.REFLECTOR,
                "You are TARS's Reflector. Return strict JSON only.",
                reflectorPrompt
        );

        ReflectionResult reflection = parseReflection(reflectionRaw);
        if (reflection.qualityScore() >= chatConfig.qualityThreshold()) {
            return actorAnswer;
        }

        log.info("Chat reflection score {} below threshold {}, requesting revision",
                reflection.qualityScore(), chatConfig.qualityThreshold());

        String revisionPrompt = fitPromptToBudget(buildRevisionPrompt(normalizedInput, actorAnswer, reflection));
        return llmService.generate(
                LlmRole.ACTOR,
                "You are TARS's Actor. Revise your prior answer using critique feedback.",
                revisionPrompt
        );
    }

    private String normalizeInput(String userInput) {
        return userInput == null ? "" : userInput.trim();
    }

    private String fitPromptToBudget(String prompt) {
        if (!contextSummarizer.needsSummarization(prompt, contextBudget)) {
            return prompt;
        }
        return contextSummarizer.fitToBudget(prompt, contextBudget);
    }

    private String buildActorPrompt(String userInput) {
        return "User input:\n" + userInput + "\n\nProvide a direct, useful response.";
    }

    private String buildReflectorPrompt(String userInput, String actorAnswer) {
        return "Evaluate the actor answer for the user input and respond ONLY valid JSON with this schema:\n"
                + "{\"qualityScore\": <number 0.0 to 1.0>, \"critique\": \"<short critique>\"}\n\n"
                + "User input:\n" + userInput + "\n\nActor answer:\n" + actorAnswer;
    }

    private String buildRevisionPrompt(String userInput, String actorAnswer, ReflectionResult reflection) {
        return "Revise the answer using the critique.\n\n"
                + "Original user input:\n" + userInput + "\n\n"
                + "Previous actor answer:\n" + actorAnswer + "\n\n"
                + "Reflector critique:\n" + reflection.critique() + "\n\n"
                + "Provide an improved final response.";
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
            String critique = root.path("critique").asText("No critique provided");
            return new ReflectionResult(score, critique);
        } catch (Exception e) {
            log.warn("Failed to parse reflection JSON, forcing revision path");
            return new ReflectionResult(0.0, "Invalid reflection format");
        }
    }

    public record ReflectionResult(double qualityScore, String critique) {}
}
