package com.tarsv2.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
        Duration safeTimeout = timeout == null ? Duration.ofSeconds(45) : timeout;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(Math.min(10_000, safeTimeout.toMillis()), TimeUnit.MILLISECONDS)
                .readTimeout(safeTimeout.toMillis(), TimeUnit.MILLISECONDS)
                .writeTimeout(Math.min(30_000, safeTimeout.toMillis()), TimeUnit.MILLISECONDS)
                .callTimeout(safeTimeout.toMillis(), TimeUnit.MILLISECONDS)
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
        } catch (Exception e) {
            log.warn("MiniMax call failed: {}", e.getMessage());
            return "[ERROR] " + e.getMessage();
        }
    }

    private String buildRequestBody(String prompt, double temperature) throws Exception {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("model", model);
        root.put("temperature", temperature);
        root.put("stream", false);

        ArrayNode messages = root.putArray("messages");
        ObjectNode user = messages.addObject();
        user.put("role", "user");
        user.put("content", prompt);

        return MAPPER.writeValueAsString(root);
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
