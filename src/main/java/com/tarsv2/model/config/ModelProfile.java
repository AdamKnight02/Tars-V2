package com.tarsv2.model.config;

import java.util.Objects;

public record ModelProfile(String id, String provider, String modelName, boolean deterministic) {

    public ModelProfile {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(provider, "provider must not be null");
        Objects.requireNonNull(modelName, "modelName must not be null");
    }
}
