package com.tarsv2.integration;

import com.tarsv2.model.ModelRequest;
import com.tarsv2.model.ModelResponse;
import com.tarsv2.model.TarsModel;
import com.tarsv2.model.config.ModelConfig;
import com.tarsv2.model.config.ModeBindings;
import com.tarsv2.model.router.DefaultModelRouter;
import com.tarsv2.model.router.RoutingMode;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModelRouterModeSelectionIT {

    @Test
    void routesByModeBinding() {
        TarsModel codex = request -> ModelResponse.ok("CODEX");
        TarsModel chat = request -> ModelResponse.ok("CHAT");
        TarsModel research = request -> ModelResponse.ok("RESEARCH");

        ModelConfig base = ModelConfig.fromEnvironment();
        ModelConfig config = new ModelConfig(new ModeBindings("chat-model", "codex-model", "research-model"),
                base.profiles(),
                base.providers());

        DefaultModelRouter router = new DefaultModelRouter(config, Map.of(
                "chat-model", chat,
                "codex-model", codex,
                "research-model", research
        ));

        assertEquals("CHAT", router.route(RoutingMode.CHAT, new ModelRequest("", "", false)).content());
        assertEquals("CODEX", router.route(RoutingMode.CODEX, new ModelRequest("", "", true)).content());
        assertEquals("RESEARCH", router.route(RoutingMode.RESEARCH, new ModelRequest("", "", true)).content());
    }
}
