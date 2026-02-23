package com.tarsv2.openclaw;

import com.tarsv2.connector.DevCapability;
import com.tarsv2.connector.DevPermissionModel;
import com.tarsv2.environment.ExecutionEnvironment;
import com.tarsv2.environment.EnvironmentRegistry;
import com.tarsv2.personality.DialogueStyle;
import com.tarsv2.workforce.economics.EconomicEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

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
    private static final Logger executionLog = LoggerFactory.getLogger("execution_log");

    private final EnvironmentRegistry environments;
    private final DevPermissionModel permissions;
    private final DialogueStyle dialogue;
    private final EconomicEngine economicEngine;

    public OpenClawClient(EnvironmentRegistry environments,
                          DevPermissionModel permissions,
                          DialogueStyle dialogue,
                          EconomicEngine economicEngine) {
        this.environments = Objects.requireNonNull(environments);
        this.permissions = Objects.requireNonNull(permissions);
        this.dialogue = Objects.requireNonNull(dialogue);
        this.economicEngine = Objects.requireNonNull(economicEngine);
    }

    public IntentResult execute(Intent intent) {
        long start = System.currentTimeMillis();
        String intentId = intent.getId();

        log.info("OpenClaw executing: {}", intent);

        if (!economicEngine.canAfford(intent.getContext().getEstimatedCost())) {
            String msg = "Economic budget exceeded for intent " + intentId;
            log.warn(msg);
            IntentResult failed = IntentResult.failure(intentId, msg, elapsed(start));
            executionLog.info("intentId={} type={} env={} success={} latencyMs={} message=\"{}\"",
                    intentId, intent.getType(), intent.getContext().getEnvironmentId(), false, failed.latencyMs(), msg);
            return failed;
        }

        ExecutionEnvironment env = environments.get(intent.getContext().getEnvironmentId());
        if (env == null) {
            String msg = "Unknown environment: " + intent.getContext().getEnvironmentId();
            log.warn(msg);
            IntentResult failed = IntentResult.failure(intentId, msg, elapsed(start));
            executionLog.info("intentId={} type={} env={} success={} latencyMs={} message=\"{}\"",
                    intentId, intent.getType(), intent.getContext().getEnvironmentId(), false, failed.latencyMs(), msg);
            return failed;
        }

        if (!env.isToolAllowed(intent.getType().name())) {
            String msg = "Tool " + intent.getType() + " not allowed in environment " + env.getId();
            log.warn(msg);
            IntentResult failed = IntentResult.failure(intentId, msg, elapsed(start));
            executionLog.info("intentId={} type={} env={} success={} latencyMs={} message=\"{}\"",
                    intentId, intent.getType(), env.getId(), false, failed.latencyMs(), msg);
            return failed;
        }

        DevCapability required = mapIntentToCapability(intent.getType());
        if (required != null && !permissions.hasCapability(required)) {
            String msg = "Missing capability " + required + " for intent " + intent.getType();
            log.warn(msg);
            dialogue.say("Permission denied: " + required + " not granted.", DialogueStyle.OutputMode.CHAT);
            IntentResult failed = IntentResult.failure(intentId, msg, elapsed(start));
            executionLog.info("intentId={} type={} env={} success={} latencyMs={} message=\"{}\"",
                    intentId, intent.getType(), env.getId(), false, failed.latencyMs(), msg);
            return failed;
        }

        try {
            IntentResult result = env.dispatch(intent);
            log.info("OpenClaw completed {}: success={} latency={}ms",
                    intentId, result.success(), result.latencyMs());
            executionLog.info("intentId={} type={} env={} success={} latencyMs={} message=\"{}\"",
                    intentId, intent.getType(), env.getId(), result.success(), result.latencyMs(), result.message());
            return result;
        } catch (Exception e) {
            log.error("OpenClaw execution failed for {}: {}", intentId, e.getMessage());
            IntentResult failed = IntentResult.failure(intentId, e.getMessage(), elapsed(start));
            executionLog.info("intentId={} type={} env={} success={} latencyMs={} message=\"{}\"",
                    intentId, intent.getType(), env.getId(), false, failed.latencyMs(), failed.message());
            return failed;
        }
    }

    private DevCapability mapIntentToCapability(IntentType type) {
        return switch (type) {
            case REASON, EVALUATE, ANALYZE_CODE, READ_FILE, MEMORY_READ, QUERY_CI -> DevCapability.READ_CODE;
            case WRITE_FILE, APPLY_PATCH, GENERATE_DIFF, RUN_COMMAND,
                    RUN_TESTS, COMMIT, MEMORY_WRITE -> DevCapability.PROPOSE_CHANGES;
            case CREATE_BRANCH, OPEN_PR, COMMENT_PR -> DevCapability.OPEN_PR;
        };
    }

    private long elapsed(long start) {
        return System.currentTimeMillis() - start;
    }
}
