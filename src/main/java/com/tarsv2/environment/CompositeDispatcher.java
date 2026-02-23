package com.tarsv2.environment;

import com.tarsv2.openclaw.Intent;
import com.tarsv2.openclaw.IntentResult;
import com.tarsv2.openclaw.IntentType;

import java.util.Objects;
import java.util.Set;

/**
 * Composite dispatcher that routes LLM intents to LLMDispatcher and
 * all remaining intents to SandboxDispatcher.
 */
public final class CompositeDispatcher implements EnvironmentDispatcher {

    private static final Set<IntentType> LLM_INTENTS = Set.of(
            IntentType.REASON,
            IntentType.EVALUATE,
            IntentType.ANALYZE_CODE
    );

    private final EnvironmentDispatcher llmDispatcher;
    private final EnvironmentDispatcher sandboxDispatcher;

    public CompositeDispatcher(EnvironmentDispatcher llmDispatcher, EnvironmentDispatcher sandboxDispatcher) {
        this.llmDispatcher = Objects.requireNonNull(llmDispatcher);
        this.sandboxDispatcher = Objects.requireNonNull(sandboxDispatcher);
    }

    @Override
    public IntentResult dispatch(ExecutionEnvironment env, Intent intent) {
        if (LLM_INTENTS.contains(intent.getType())) {
            return llmDispatcher.dispatch(env, intent);
        }
        return sandboxDispatcher.dispatch(env, intent);
    }
}
