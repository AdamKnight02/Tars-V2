package com.tarsv2.environment;

import com.tarsv2.openclaw.Intent;
import com.tarsv2.openclaw.IntentResult;

import java.util.*;

/**
 * A pinned execution environment for TARS actions.
 *
 * <p>Each environment defines a restricted tool set, resource limits,
 * and configuration. All execution occurs inside an Environment via
 * OpenClaw — TARS never accesses the host directly.</p>
 *
 * <p>Modeled after ALE "ROCK"-like environment pinning:
 * deterministic, bounded, observable.</p>
 */
public final class ExecutionEnvironment {

    private final String id;
    private final String description;
    private final Set<String> allowedTools;
    private final Map<String, String> config;
    private final ResourceLimits limits;
    private final EnvironmentDispatcher dispatcher;

    public ExecutionEnvironment(String id, String description,
                                Set<String> allowedTools,
                                Map<String, String> config,
                                ResourceLimits limits,
                                EnvironmentDispatcher dispatcher) {
        this.id = Objects.requireNonNull(id);
        this.description = Objects.requireNonNull(description);
        this.allowedTools = Set.copyOf(allowedTools);
        this.config = Map.copyOf(config);
        this.limits = Objects.requireNonNull(limits);
        this.dispatcher = Objects.requireNonNull(dispatcher);
    }

    public String getId() { return id; }
    public String getDescription() { return description; }
    public Set<String> getAllowedTools() { return allowedTools; }
    public Map<String, String> getConfig() { return config; }
    public ResourceLimits getLimits() { return limits; }

    /**
     * Checks whether a tool (by IntentType name) is permitted here.
     */
    public boolean isToolAllowed(String toolName) {
        return allowedTools.contains(toolName);
    }

    /**
     * Dispatches an intent for execution within this environment.
     */
    public IntentResult dispatch(Intent intent) {
        return dispatcher.dispatch(this, intent);
    }

    @Override
    public String toString() {
        return String.format("Environment[%s | tools=%d | %s]", id, allowedTools.size(), description);
    }
}
