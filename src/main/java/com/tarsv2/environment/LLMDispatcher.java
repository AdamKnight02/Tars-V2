package com.tarsv2.environment;

import com.tarsv2.model.ModelRequest;
import com.tarsv2.model.ModelResponse;
import com.tarsv2.model.router.ModelRouter;
import com.tarsv2.model.router.RoutingMode;
import com.tarsv2.openclaw.Intent;
import com.tarsv2.openclaw.IntentResult;

import java.util.Map;
import java.util.Objects;

/**
 * Dispatcher for LLM-backed intents that are executed through OpenClaw.
 */
public final class LLMDispatcher implements EnvironmentDispatcher {

    private final ModelRouter modelRouter;

    public LLMDispatcher(ModelRouter modelRouter) {
        this.modelRouter = Objects.requireNonNull(modelRouter);
    }

    @Override
    public IntentResult dispatch(ExecutionEnvironment env, Intent intent) {
        long start = System.currentTimeMillis();
        String prompt = intent.getPayload().get("prompt");
        RoutingMode mode = switch (intent.getType()) {
            case REASON -> RoutingMode.RESEARCH;
            case EVALUATE -> RoutingMode.CHAT;
            case ANALYZE_CODE -> RoutingMode.CODEX;
            default -> throw new IllegalArgumentException("Unsupported LLM intent: " + intent.getType());
        };
        ModelResponse response = modelRouter.route(mode, new ModelRequest("", prompt == null ? "" : prompt, false));
        long latency = System.currentTimeMillis() - start;

        return IntentResult.success(
                intent.getId(),
                Map.of("content", response.content()),
                "LLM execution complete",
                latency
        );
    }
}
