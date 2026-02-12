package com.tarsv2.environment;

import com.tarsv2.openclaw.Intent;
import com.tarsv2.openclaw.IntentResult;

/**
 * Functional interface for dispatching intents within an environment.
 */
@FunctionalInterface
public interface EnvironmentDispatcher {

    /**
     * Dispatches an intent for execution within the given environment.
     *
     * @param env    the execution environment
     * @param intent the intent to execute
     * @return structured result
     */
    IntentResult dispatch(ExecutionEnvironment env, Intent intent);
}
