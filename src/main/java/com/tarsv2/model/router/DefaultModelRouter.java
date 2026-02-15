package com.tarsv2.model.router;

import com.tarsv2.model.ModelRequest;
import com.tarsv2.model.ModelResponse;
import com.tarsv2.model.TarsModel;
import com.tarsv2.model.config.ModelConfig;

import java.util.Map;
import java.util.Objects;

public final class DefaultModelRouter implements ModelRouter {

    private final ModelConfig config;
    private final Map<String, TarsModel> models;

    public DefaultModelRouter(ModelConfig config, Map<String, TarsModel> models) {
        this.config = Objects.requireNonNull(config);
        this.models = Map.copyOf(models);
    }

    @Override
    public ModelResponse route(RoutingMode mode, ModelRequest request) {
        String modelId = switch (mode) {
            case CHAT -> config.bindings().chatModel();
            case CODEX -> config.bindings().codexModel();
            case RESEARCH -> config.bindings().researchModel();
        };

        TarsModel model = models.get(modelId);
        if (model == null) {
            return ModelResponse.unavailable("No model configured for mode " + mode + ": " + modelId);
        }
        return model.generate(request);
    }
}
