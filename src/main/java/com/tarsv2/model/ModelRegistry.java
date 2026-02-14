package com.tarsv2.model;

import java.time.Duration;

public record ModelRegistry(
        String chat,
        String codex,
        String research,
        Duration modelTimeout,
        int circuitBreakerFailureThreshold
) {

    public static ModelRegistry fromEnvironment() {
        return new ModelRegistry(
                envOrDefault("TARS_MODEL_CHAT", "minimax"),
                envOrDefault("TARS_MODEL_CODEX", "minimax"),
                envOrDefault("TARS_MODEL_RESEARCH", "glm"),
                Duration.ofSeconds(parseIntEnv("TARS_MODEL_TIMEOUT_SECONDS", 45)),
                parseIntEnv("TARS_MODEL_CIRCUIT_BREAKER_FAILURES", 3)
        );
    }

    private static String envOrDefault(String key, String fallback) {
        String value = System.getenv(key);
        return (value == null || value.isBlank()) ? fallback : value.trim().toLowerCase();
    }

    private static int parseIntEnv(String key, int fallback) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }
}
