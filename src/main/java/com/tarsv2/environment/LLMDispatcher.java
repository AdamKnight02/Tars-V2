package com.tarsv2.environment;

import com.tarsv2.openclaw.Intent;
import com.tarsv2.openclaw.IntentResult;

import java.util.Map;

/**
 * Dispatcher for LLM-backed intents that are executed through OpenClaw.
 */
public final class LLMDispatcher implements EnvironmentDispatcher {

    @Override
    public IntentResult dispatch(ExecutionEnvironment env, Intent intent) {
        long start = System.currentTimeMillis();
        String mode = switch (intent.getType()) {
            case REASON -> "reason";
            case EVALUATE -> "evaluate";
            case ANALYZE_CODE -> "analyze_code";
            default -> throw new IllegalArgumentException("Unsupported LLM intent: " + intent.getType());
        };

        return IntentResult.success(
                intent.getId(),
                Map.of(
                        "mode", mode,
                        "prompt", intent.getPayload().has("prompt") ? intent.getPayload().get("prompt") : "",
                        "status", "queued",
                        "content", "[]"
                ),
                "LLM intent queued: " + mode,
                System.currentTimeMillis() - start
        );
    }
}
