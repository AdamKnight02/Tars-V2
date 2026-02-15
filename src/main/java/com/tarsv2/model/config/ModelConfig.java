package com.tarsv2.model.config;

import java.time.Duration;
import java.util.Map;

public record ModelConfig(ModeBindings bindings,
                          Map<String, ModelProfile> profiles,
                          Map<String, ProviderConfig> providers) {

    public static ModelConfig fromEnvironment() {
        String minimaxModel = envOrDefaultRaw("TARS_MINIMAX_MODEL", "MiniMax-M2.5");
        String glmResearchModel = envOrDefaultRaw("TARS_GLM_RESEARCH_MODEL", "GLM-5");
        String glmFutureModel = envOrDefaultRaw("TARS_GLM_FUTURE_MODEL", "GLM-future");

        Map<String, ModelProfile> profiles = Map.of(
                "m2.5", new ModelProfile("m2.5", "minimax", minimaxModel, true),
                "glm-5", new ModelProfile("glm-5", "glm", glmResearchModel, false),
                "glm-future", new ModelProfile("glm-future", "glm", glmFutureModel, false)
        );

        Map<String, ProviderConfig> providers = Map.of(
                "minimax", new ProviderConfig(
                        "minimax",
                        envOrDefault("TARS_MINIMAX_API_URL", "https://api.minimax.chat/v1/text/chatcompletion_v2"),
                        "TARS_MINIMAX_API_KEY",
                        Duration.ofSeconds(parseIntEnv("TARS_MODEL_TIMEOUT_SECONDS", 45))
                ),
                "glm", new ProviderConfig(
                        "glm",
                        envOrDefault("TARS_GLM_API_URL", "https://open.bigmodel.cn/api/paas/v4/chat/completions"),
                        "TARS_GLM_API_KEY",
                        Duration.ofSeconds(parseIntEnv("TARS_MODEL_TIMEOUT_SECONDS", 45))
                )
        );

        return new ModelConfig(
                new ModeBindings(
                        envOrDefault("TARS_MODEL_CHAT", "m2.5"),
                        envOrDefault("TARS_MODEL_CODEX", "m2.5"),
                        envOrDefault("TARS_MODEL_RESEARCH", "glm-5")
                ),
                profiles,
                providers
        );
    }

    private static String envOrDefault(String key, String fallback) {
        String value = System.getenv(key);
        return (value == null || value.isBlank()) ? fallback : value.trim().toLowerCase();
    }

    private static String envOrDefaultRaw(String key, String fallback) {
        String value = System.getenv(key);
        return (value == null || value.isBlank()) ? fallback : value.trim();
    }

    private static int parseIntEnv(String key, int fallback) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
