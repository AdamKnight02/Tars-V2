package com.tarsv2.model.config;

import java.time.Duration;
import java.util.Objects;

public record ProviderConfig(String providerName, String baseUrl, String apiKeyEnv, Duration timeout) {

    public ProviderConfig {
        Objects.requireNonNull(providerName, "providerName must not be null");
        Objects.requireNonNull(baseUrl, "baseUrl must not be null");
        Objects.requireNonNull(apiKeyEnv, "apiKeyEnv must not be null");
        timeout = timeout == null ? Duration.ofSeconds(45) : timeout;
    }
}
