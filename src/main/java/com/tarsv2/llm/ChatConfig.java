package com.tarsv2.llm;

/**
 * Configuration for interactive dual-LLM chat behavior.
 */
public record ChatConfig(double qualityThreshold) {

    public static final double DEFAULT_QUALITY_THRESHOLD = 0.75;

    public static ChatConfig fromEnvironment() {
        String fromProperty = System.getProperty("tars.chat.qualityThreshold");
        if (fromProperty != null && !fromProperty.isBlank()) {
            return new ChatConfig(parseThreshold(fromProperty, DEFAULT_QUALITY_THRESHOLD));
        }

        String fromEnv = System.getenv("TARS_CHAT_QUALITY_THRESHOLD");
        return new ChatConfig(parseThreshold(fromEnv, DEFAULT_QUALITY_THRESHOLD));
    }

    private static double parseThreshold(String raw, double fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            double value = Double.parseDouble(raw.trim());
            if (value >= 0.0 && value <= 1.0) {
                return value;
            }
        } catch (NumberFormatException ignored) {
            // use fallback
        }
        return fallback;
    }
}
