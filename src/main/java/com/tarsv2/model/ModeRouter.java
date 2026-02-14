package com.tarsv2.model;

import java.util.Map;
import java.util.Objects;

public final class ModeRouter {

    private final ModelRegistry config;
    private final Map<String, TarsModel> models;

    public ModeRouter(ModelRegistry config, Map<String, TarsModel> models) {
        this.config = Objects.requireNonNull(config);
        this.models = Map.copyOf(models);
    }

    public ModelResponse route(Mode mode, ModelRequest request) {
        String configuredName = configuredModel(mode);
        TarsModel model = models.get(configuredName);
        if (model == null) {
            return ModelResponse.unavailable("No model configured for mode " + mode + ": " + configuredName);
        }
        return model.generate(request);
    }

    private String configuredModel(Mode mode) {
        return switch (mode) {
            case CHAT -> config.chat();
            case CODEX -> config.codex();
            case RESEARCH -> config.research();
        };
    }
}
