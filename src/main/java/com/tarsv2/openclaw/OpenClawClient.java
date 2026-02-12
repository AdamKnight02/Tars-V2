package com.tarsv2.openclaw;

import com.tarsv2.connector.DevCapability;
import com.tarsv2.connector.DevPermissionModel;
import com.tarsv2.environment.ExecutionEnvironment;
import com.tarsv2.environment.EnvironmentRegistry;
import com.tarsv2.personality.DialogueStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The sole execution layer for TARS Intents.
 *
 * <p>OpenClaw validates each Intent against the target environment's
 * tool set, permission model, and resource limits before dispatching.
 * TARS never calls tools directly — all execution flows through here.</p>
 *
 * <p>Flow: TARS → Intent → OpenClawClient → Environment → Tool → IntentResult</p>
 */
public final class OpenClawClient {

    private static final Logger log = LoggerFactory.getLogger(OpenClawClient.class);

    private final EnvironmentRegistry environments;
    private final DevPermissionModel permissions;
    private final DialogueStyle dialogue;

    public OpenClawClient(EnvironmentRegistry environments,
                          DevPermissionModel permissions,
                          DialogueStyle dialogue) {
        this.environments = Objects.requireNonNull(environments);
        this.permissions = Objects.requireNonNull(permissions);
        this.dialogue = Objects.requireNonNull(dialogue);
    }

    /**
     * Executes an Intent. Validates environment, permissions, and tool
     * availability before dispatching.
     *
     * @param intent the intent to execute
     * @return structured result
     */
    public IntentResult execute(Intent intent) {
        long start = System.currentTimeMillis();
        String intentId = intent.getId();

        log.info("OpenClaw executing: {}", intent);

        // 1. Resolve environment
        ExecutionEnvironment env = environments.get(intent.getContext().getEnvironmentId());
        if (env == null) {
            String msg = "Unknown environment: " + intent.getContext().getEnvironmentId();
            log.warn(msg);
            return IntentResult.failure(intentId, msg, elapsed(start));
        }

        // 2. Check tool availability in environment
        if (!env.isToolAllowed(intent.getType().name())) {
            String msg = "Tool " + intent.getType() + " not allowed in environment " + env.getId();
            log.warn(msg);
            return IntentResult.failure(intentId, msg, elapsed(start));
        }

        // 3. Check permission model
        DevCapability required = mapIntentToCapability(intent.getType());
        if (required != null && !permissions.hasCapability(required)) {
            String msg = "Missing capability " + required + " for intent " + intent.getType();
            log.warn(msg);
            dialogue.say("Permission denied: " + required + " not granted.");
            return IntentResult.failure(intentId, msg, elapsed(start));
        }

        // 4. Dispatch to environment
        try {
            IntentResult result = env.dispatch(intent);
            log.info("OpenClaw completed {}: success={} latency={}ms",
                    intentId, result.success(), result.latencyMs());
            return result;
        } catch (Exception e) {
            log.error("OpenClaw execution failed for {}: {}", intentId, e.getMessage());
            return IntentResult.failure(intentId, e.getMessage(), elapsed(start));
        }
    }

    /**
     * Maps an IntentType to the required DevCapability. Returns null
     * if the intent needs no special capability.
     */
    private DevCapability mapIntentToCapability(IntentType type) {
        return switch (type) {
            case READ_FILE, MEMORY_READ, QUERY_CI -> DevCapability.READ_CODE;
            case WRITE_FILE, APPLY_PATCH, GENERATE_DIFF, RUN_COMMAND,
                 RUN_TESTS, COMMIT, MEMORY_WRITE -> DevCapability.PROPOSE_CHANGES;
            case CREATE_BRANCH, OPEN_PR, COMMENT_PR -> DevCapability.OPEN_PR;
        };
    }

    private long elapsed(long start) {
        return System.currentTimeMillis() - start;
    }
}
