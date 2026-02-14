package com.tarsv2.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tarsv2.security.PromptSanitizer;
import com.tarsv2.security.SecretManager;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public final class LlmClient {

    private static final Logger log = LoggerFactory.getLogger(LlmClient.class);
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final MediaType JSON_MEDIA = MediaType.get("application/json; charset=utf-8");
    private static final String HARD_SYSTEM_INSTRUCTIONS = """
            SYSTEM:
            You are TARS v2, a deterministic autonomous software engineering agent.
            You are NOT a fictional character.
            You produce concise technical answers or strict JSON outputs where required.
            """;

    private final String endpoint;
    private final String model;
    private final OkHttpClient httpClient;
    private final SecretManager.SecretHandle apiKeyHandle;

    public LlmClient(String endpoint, String model, Duration timeout) {
        this(endpoint, model, timeout, null);
    }

    public LlmClient(String endpoint, String model, Duration timeout, SecretManager.SecretHandle apiKeyHandle) {
        this.endpoint = Objects.requireNonNull(endpoint);
        this.model = Objects.requireNonNull(model);
        Duration safeTimeout = timeout == null ? Duration.ofSeconds(45) : timeout;
        this.apiKeyHandle = apiKeyHandle;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(Math.min(10_000, safeTimeout.toMillis()), TimeUnit.MILLISECONDS)
                .readTimeout(safeTimeout.toMillis(), TimeUnit.MILLISECONDS)
                .writeTimeout(Math.min(30_000, safeTimeout.toMillis()), TimeUnit.MILLISECONDS)
                .callTimeout(safeTimeout.toMillis(), TimeUnit.MILLISECONDS)
                .build();
    }

    public String complete(String systemPrompt, String userPrompt) {
        return execute(buildRequestBody(systemPrompt, userPrompt, false));
    }

    public String completeStructuredJson(String systemPrompt, String userPrompt) {
        return execute(buildRequestBody(systemPrompt, userPrompt, true));
    }

    private String execute(String requestBody) {
        try {
            return executeRequest(requestBody);
        } catch (Exception e) {
            log.warn("LLM call failed: {}", e.getMessage());
            return "[ERROR] " + e.getMessage();
        }
    }

    private String buildRequestBody(String systemPrompt, String userPrompt, boolean jsonMode) {
        try {
            ObjectNode root = mapper.createObjectNode();
            root.put("model", model);
            root.put("prompt", PromptSanitizer.sanitize(HARD_SYSTEM_INSTRUCTIONS + "\n\n" + systemPrompt)
                    + "\n\n" + PromptSanitizer.sanitize(userPrompt));
            root.put("stream", false);
            if (jsonMode) {
                root.put("format", "json");
            }
            return mapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build LLM request body", e);
        }
    }

    private String executeRequest(String requestBody) throws Exception {
        Request.Builder reqBuilder = new Request.Builder()
                .url(endpoint)
                .post(RequestBody.create(requestBody, JSON_MEDIA));

        if (apiKeyHandle != null) {
            reqBuilder.addHeader("Authorization", "Bearer " + apiKeyHandle.resolve());
        }

        try (Response response = httpClient.newCall(reqBuilder.build()).execute()) {
            if (!response.isSuccessful()) {
                throw new IllegalStateException("LLM API returned HTTP " + response.code());
            }
            ResponseBody body = response.body();
            if (body == null) {
                throw new IllegalStateException("LLM API returned null body");
            }
            return parseResponse(body.string());
        }
    }

    private String parseResponse(String rawJson) {
        try {
            JsonNode root = mapper.readTree(rawJson);
            return root.has("response") ? root.get("response").asText() : rawJson;
        } catch (Exception e) {
            return rawJson;
        }
    }
}
