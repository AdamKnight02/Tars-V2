package com.tarsv2.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

public final class GlmClient implements ReasoningModelClient {

    private static final Logger log = LoggerFactory.getLogger(GlmClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final MediaType JSON_MEDIA = MediaType.get("application/json; charset=utf-8");

    private final String endpoint;
    private final String model;
    private final OkHttpClient httpClient;

    public GlmClient() {
        String envUrl = System.getenv("TARS_GLM_API_URL");
        this.endpoint = (envUrl != null && !envUrl.isBlank())
                ? envUrl : "https://api.z.ai/api/paas/v4/chat/completions";
        String envModel = System.getenv("TARS_GLM_RESEARCH_MODEL");
        this.model = (envModel != null && !envModel.isBlank()) ? envModel : "glm-5";
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .callTimeout(90, TimeUnit.SECONDS)
                .build();
    }

    @Override
    public String chat(String prompt) {
        return execute(prompt, 0.7);
    }

    @Override
    public String generateDeterministicDiff(String prompt) {
        return execute(prompt, 0.0);
    }

    private String execute(String prompt, double temperature) {
        try {
            String apiKey = System.getenv("TARS_GLM_API_KEY");
            if (apiKey == null || apiKey.isBlank()) {
                throw new IllegalStateException("Missing GLM API key (TARS_GLM_API_KEY)");
            }

            ObjectNode root = MAPPER.createObjectNode();
            root.put("model", model);
            root.put("temperature", temperature);
            root.put("stream", false);

            ArrayNode messages = root.putArray("messages");
            ObjectNode system = messages.addObject();
            system.put("role", "system");
            system.put("content", "You are a helpful AI assistant.");
            ObjectNode user = messages.addObject();
            user.put("role", "user");
            user.put("content", prompt);

            String requestBody = MAPPER.writeValueAsString(root);

            Request request = new Request.Builder()
                    .url(endpoint)
                    .post(RequestBody.create(requestBody, JSON_MEDIA))
                    .addHeader("Authorization", "Bearer " + apiKey.trim())
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Accept-Language", "en-US,en")
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                ResponseBody body = response.body();
                String raw = body != null ? body.string() : "";
                if (!response.isSuccessful()) {
                    log.warn("GLM API returned HTTP {}: {}", response.code(), raw);
                    if (isModelUnavailable(response.code(), raw)) {
                        return "[NOT_IMPLEMENTED] Research model unavailable";
                    }
                    throw new IllegalStateException("GLM API returned HTTP " + response.code() + ": " + raw);
                }
                return parseContent(raw);
            }
        } catch (Exception e) {
            if (isModelUnavailable(e.getMessage())) {
                log.warn("GLM call unavailable: {}", e.getMessage());
                return "[NOT_IMPLEMENTED] Research model unavailable";
            }
            log.warn("GLM call failed: {}", e.getMessage());
            return "[ERROR] " + e.getMessage();
        }
    }

    private boolean isModelUnavailable(int statusCode, String rawBody) {
        return statusCode == 429 && rawBody != null && rawBody.contains("\"code\":\"1113\"");
    }

    private boolean isModelUnavailable(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        return message.contains("Missing GLM API key") || message.contains("HTTP 429") && message.contains("\"code\":\"1113\"");
    }

    private String parseContent(String rawJson) {
        try {
            JsonNode root = MAPPER.readTree(rawJson);
            JsonNode choices = root.path("choices");
            if (choices.isArray() && !choices.isEmpty()) {
                JsonNode content = choices.get(0).path("message").path("content");
                if (content.isTextual()) {
                    return content.asText();
                }
            }
            return rawJson;
        } catch (Exception e) {
            return rawJson;
        }
    }
}
