package com.tarsv2.llm;

@Deprecated
public record ChatConfig(double qualityThreshold) {
    public static ChatConfig fromEnvironment() {
        return new ChatConfig(0.0);
    }
}
