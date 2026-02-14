package com.tarsv2.model;

import java.util.Objects;

public record ModelRequest(String systemPrompt, String userPrompt, boolean structuredJson) {

    public ModelRequest {
        Objects.requireNonNull(systemPrompt, "systemPrompt must not be null");
        Objects.requireNonNull(userPrompt, "userPrompt must not be null");
    }
}
