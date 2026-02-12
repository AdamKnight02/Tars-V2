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
    private static final String REPOSITORY_GROUNDING_INSTRUCTION = """
            You are TARS v2 running inside your own Java codebase.
            You are NOT a fictional character.
            You do NOT reference movies.
            You do NOT explain Android or generic frameworks unless they exist in this repository.

            If asked about systems (Intent, Sandbox, Agent, etc.),
            you MUST reference actual classes and packages inside:
            com.tarsv2.*

            If the answer cannot be derived from repository structure,
            respond:
            "Insufficient repository context."
            """.strip();

    private final ContextBudget contextBudget;
    private final RepositoryContextInjector repositoryContextInjector;

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
        this.repositoryContextInjector = new RepositoryContextInjector();
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
        RepositoryContextInjector.InjectionResult actorInjection = repositoryContextInjector.injectIfRelevant(actorPrompt, normalizedInput);
        logInjection("actor", actorInjection);
        actorPrompt = actorInjection.prompt();
        String actorAnswer = llmService.generate(
                LlmRole.ACTOR,
                REPOSITORY_GROUNDING_INSTRUCTION + "\n\nYou are TARS's Actor. Respond helpfully, accurately, and concisely.",
                actorPrompt
        );

        String reflectorPrompt = fitPromptToBudget(buildReflectorPrompt(normalizedInput, actorAnswer));
        RepositoryContextInjector.InjectionResult reflectorInjection = repositoryContextInjector.injectIfRelevant(reflectorPrompt, normalizedInput);
        logInjection("reflector", reflectorInjection);
        reflectorPrompt = reflectorInjection.prompt();
        String reflectionRaw = llmService.generate(
                LlmRole.REFLECTOR,
                REPOSITORY_GROUNDING_INSTRUCTION + "\n\nYou are TARS's Reflector. Return strict JSON only.",
                reflectorPrompt
        );

        ReflectionResult reflection = parseReflection(reflectionRaw);
        if (reflection.qualityScore() >= chatConfig.qualityThreshold()) {
            return actorAnswer;
        }

        log.info("Chat reflection score {} below threshold {}, requesting revision",
                reflection.qualityScore(), chatConfig.qualityThreshold());

        String revisionPrompt = fitPromptToBudget(buildRevisionPrompt(normalizedInput, actorAnswer, reflection));
        RepositoryContextInjector.InjectionResult revisionInjection = repositoryContextInjector.injectIfRelevant(revisionPrompt, normalizedInput);
        logInjection("revision", revisionInjection);
        revisionPrompt = revisionInjection.prompt();
        return llmService.generate(
                LlmRole.ACTOR,
                REPOSITORY_GROUNDING_INSTRUCTION + "\n\nYou are TARS's Actor. Revise your prior answer using critique feedback.",
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

    private void logInjection(String stage, RepositoryContextInjector.InjectionResult injection) {
        if (!injection.keywordTriggered()) {
            return;
        }

        log.debug("Repository context keyword detection triggered for stage {} with keyword '{}'", stage, injection.triggeredKeyword());
        log.debug("Repository context selected files for stage {}: {}", stage, injection.selectedFiles());
        log.debug("Repository context injected {} characters for stage {}", injection.injectedCharacters(), stage);
    }

    private ReflectionResult parseReflection(String reflectionRaw) {
        if (containsHallucinationTrigger(reflectionRaw)) {
            return new ReflectionResult(0.0, "Disallowed content detected by reflector rule");
        }
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

    private boolean containsHallucinationTrigger(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }

        String lowered = value.toLowerCase();
        return lowered.contains("android")
                || lowered.contains("interstellar")
                || lowered.contains("actor who played");
    }

    public record ReflectionResult(double qualityScore, String critique) {}
}
