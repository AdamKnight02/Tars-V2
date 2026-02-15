package com.tarsv2.model;

import com.tarsv2.model.config.ModelConfig;
import com.tarsv2.model.config.ModeBindings;
import com.tarsv2.model.router.DefaultModelRouter;
import com.tarsv2.model.router.RoutingMode;

import java.util.Map;
import java.util.Objects;

/**
 * Backward-compatible adapter around the new router abstraction.
 */
public final class ModeRouter {

    private final com.tarsv2.model.router.ModelRouter delegate;

    public ModeRouter(ModelRegistry config, Map<String, TarsModel> models) {
        this(toModelConfig(config), models);
    }

    public ModeRouter(ModelConfig config, Map<String, TarsModel> models) {
        Objects.requireNonNull(config);
        this.delegate = new DefaultModelRouter(config, models);
    }

    public ModelResponse route(Mode mode, ModelRequest request) {
        return delegate.route(switch (mode) {
            case CHAT -> RoutingMode.CHAT;
            case CODEX -> RoutingMode.CODEX;
            case RESEARCH -> RoutingMode.RESEARCH;
        }, request);
    }

    private static ModelConfig toModelConfig(ModelRegistry registry) {
        ModelConfig base = ModelConfig.fromEnvironment();
        return new ModelConfig(
                new ModeBindings(registry.chat(), registry.codex(), registry.research()),
                base.profiles(),
                base.providers()
        );
    }
}
