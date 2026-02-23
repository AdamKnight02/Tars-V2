package com.tarsv2.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public final class MinimaxClient implements ReasoningModelClient {

    private static final Logger log = LoggerFactory.getLogger(MinimaxClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final MediaType JSON_MEDIA = MediaType.get("application/json; charset=utf-8");

    private final String endpoint;
    private final String model;
    private final OkHttpClient httpClient;

    public MinimaxClient(String endpoint, String model, Duration timeout) {
        this.endpoint = Objects.requireNonNull(endpoint);
        this.model = Objects.requireNonNull(model);
        Duration safeTimeout = timeout == null ? Duration.ofSeconds(60) : timeout;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .callTimeout(Math.max(60, safeTimeout.toSeconds()), TimeUnit.SECONDS)
                .build();
    }

    @Override
    public String chat(String prompt) {
        return execute(prompt, 0.2);
    }

    @Override
    public String generateDeterministicDiff(String prompt) {
        return execute(prompt, 0.0);
    }

    private String execute(String prompt, double temperature) {
        try {
            String requestBody = buildRequestBody(prompt, temperature);
            log.info("MiniMax endpoint URL: {}", endpoint);
            log.info("MiniMax request body: {}", requestBody);
            Request.Builder reqBuilder = new Request.Builder()
                    .url(endpoint)
                    .post(RequestBody.create(requestBody, JSON_MEDIA));

            String apiKey = System.getenv("TARS_MINIMAX_API_KEY");
            if (apiKey == null || apiKey.isBlank()) {
                throw new IllegalStateException("Missing MiniMax API key (TARS_MINIMAX_API_KEY)");
            }
            reqBuilder.addHeader("Authorization", "Bearer " + apiKey.trim());

            try (Response response = httpClient.newCall(reqBuilder.build()).execute()) {
                if (!response.isSuccessful()) {
                    throw new IllegalStateException("MiniMax API returned HTTP " + response.code());
                }
                ResponseBody body = response.body();
                if (body == null) {
                    throw new IllegalStateException("MiniMax API returned null body");
                }
                return parseContent(body.string());
            }
        } catch (SocketTimeoutException timeoutException) {
            log.warn("MiniMax timeout: {}", timeoutException.getMessage());
            return structuredError(LlmErrorType.TIMEOUT, "MINIMAX");
        } catch (Exception e) {
            log.warn("MiniMax call failed: {}", e.getMessage());
            return structuredError(LlmErrorType.NETWORK, "MINIMAX");
        }
    }

    private String buildRequestBody(String prompt, double temperature) throws Exception {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("model", model);
        ArrayNode messages = root.putArray("messages");
        ObjectNode user = messages.addObject();
        user.put("role", "user");
        user.put("content", prompt);

        return MAPPER.writeValueAsString(root);
    }

    private String structuredError(LlmErrorType errorType, String provider) {
        ObjectNode error = MAPPER.createObjectNode();
        error.put("error_type", errorType.name());
        error.put("provider", provider);
        try {
            return MAPPER.writeValueAsString(error);
        } catch (Exception ignored) {
            return "{\"error_type\":\"UNKNOWN\",\"provider\":\"" + provider + "\"}";
        }
    }

    private String parseContent(String rawJson) {
        try {
            JsonNode root = MAPPER.readTree(rawJson);
            JsonNode choices = root.path("choices");
            if (choices.isArray() && !choices.isEmpty()) {
                JsonNode message = choices.get(0).path("message");
                JsonNode content = message.path("content");
                if (content.isTextual()) {
                    return content.asText();
                }
            }
            if (root.path("reply").isTextual()) {
                return root.path("reply").asText();
            }
            return rawJson;
        } catch (Exception e) {
            return rawJson;
        }
    }
}
