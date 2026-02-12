package com.tarsv2.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tarsv2.security.PromptSanitizer;
import com.tarsv2.security.SecretManager;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Client for communicating with a local or remote LLM endpoint.
 *
 * <p>Each instance is bound to a specific {@link LlmRole} (Actor or Reflector)
 * and talks to the corresponding model endpoint.</p>
 *
 * <p>All outbound prompts are sanitized via {@link PromptSanitizer}.
 * API keys are accessed only through opaque {@link SecretManager} handles.</p>
 */
public final class LlmClient {

    private static final Logger log = LoggerFactory.getLogger(LlmClient.class);
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final MediaType JSON_MEDIA = MediaType.get("application/json; charset=utf-8");
    private static final String HARD_SYSTEM_INSTRUCTIONS = """
            SYSTEM:
            You are TARS v2, a deterministic autonomous software engineering agent.
            You are NOT a fictional character.
            You do NOT roleplay or reference movies, stories, movies, or actors.
            You produce ONLY concise technical answers or structured JSON outputs where required.
            If you produce anything other than valid JSON when required, that is a failure.
            """;

    private static final int MAX_RETRIES = 3;
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(120);

    private final LlmRole role;
    private final String endpoint;
    private final String model;
    private final OkHttpClient httpClient;
    private final SecretManager.SecretHandle apiKeyHandle;

    /**
     * @param role     the LLM role this client serves
     * @param endpoint the model API endpoint (e.g., "http://localhost:11434/api/generate")
     * @param model    the Ollama model name (e.g., "llama3:8b")
     */
    public LlmClient(LlmRole role, String endpoint, String model) {
        this(role, endpoint, model, null);
    }

    /**
     * @param role          the LLM role this client serves
     * @param endpoint      the model API endpoint
     * @param model         the Ollama model name
     * @param apiKeyHandle  opaque handle to the API key (nullable for local models)
     */
    public LlmClient(LlmRole role, String endpoint, String model, SecretManager.SecretHandle apiKeyHandle) {
        this.role = Objects.requireNonNull(role);
        this.endpoint = Objects.requireNonNull(endpoint);
        this.model = Objects.requireNonNull(model);
        this.apiKeyHandle = apiKeyHandle;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(CONNECT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)
                .readTimeout(READ_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    /**
     * Sends a prompt to the LLM and returns the response.
     * All prompts are sanitized before transmission.
     * Retries up to 3 times on transient failures with exponential backoff.
     *
     * @param systemPrompt the system-level instruction
     * @param userPrompt   the user/task-level prompt
     * @return the model's response text
     */
    public String complete(String systemPrompt, String userPrompt) {
        log.info("[{}] Sending prompt to {} at {}", role, role.getModelFamily(), endpoint);

        String fullSystemPrompt = HARD_SYSTEM_INSTRUCTIONS + "\n\n" + systemPrompt;
        String sanitizedSystem = PromptSanitizer.sanitize(fullSystemPrompt);
        String sanitizedUser = PromptSanitizer.sanitize(userPrompt);

        log.debug("[{}] System: {}", role, sanitizedSystem);
        log.debug("[{}] User: {}", role, sanitizedUser);

        String requestBody = buildRequestBody(sanitizedSystem, sanitizedUser);

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                String response = executeRequest(requestBody);
                log.info("[{}] Received response ({} chars)", role, response.length());
                return response;
            } catch (IOException e) {
                log.warn("[{}] Attempt {}/{} failed: {}", role, attempt, MAX_RETRIES, e.getMessage());
                if (attempt == MAX_RETRIES) {
                    log.error("[{}] All {} attempts exhausted.", role, MAX_RETRIES);
                    return String.format("[%s] ERROR: LLM endpoint unreachable after %d attempts — %s",
                            role, MAX_RETRIES, e.getMessage());
                }
                try {
                    long backoffMs = (long) Math.pow(2, attempt) * 1000;
                    log.info("[{}] Backing off for {}ms before retry", role, backoffMs);
                    Thread.sleep(backoffMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return String.format("[%s] ERROR: Interrupted during retry backoff", role);
                }
            }
        }

        return String.format("[%s] ERROR: Unexpected retry loop exit", role);
    }

    private String buildRequestBody(String systemPrompt, String userPrompt) {
        try {
            ObjectNode root = mapper.createObjectNode();

            root.put("model", model);
            root.put("prompt", systemPrompt + "\n\n" + userPrompt);
            root.put("stream", false);

            return mapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new RuntimeException("Failed to build LLM request body", e);
        }
    }

    private String executeRequest(String requestBody) throws IOException {
        Request.Builder reqBuilder = new Request.Builder()
                .url(endpoint)
                .post(RequestBody.create(requestBody, JSON_MEDIA));

        if (apiKeyHandle != null) {
            reqBuilder.addHeader("Authorization", "Bearer " + apiKeyHandle.resolve());
        }

        try (Response response = httpClient.newCall(reqBuilder.build()).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("LLM API returned HTTP " + response.code()
                        + ": " + (response.body() != null ? response.body().string() : "no body"));
            }

            ResponseBody body = response.body();
            if (body == null) {
                throw new IOException("LLM API returned null body");
            }

            return parseResponse(body.string());
        }
    }

    private String parseResponse(String rawJson) {
        try {
            JsonNode root = mapper.readTree(rawJson);

            // Ollama format: { "response": "..." }
            if (root.has("response")) {
                return root.get("response").asText();
            }

            log.warn("[{}] Could not parse standard response format, returning raw", role);
            return rawJson;
        } catch (Exception e) {
            log.warn("[{}] JSON parsing failed, returning raw response", role);
            return rawJson;
        }
    }

    public LlmRole getRole() { return role; }
    public String getEndpoint() { return endpoint; }
    public String getModel() { return model; }
}
