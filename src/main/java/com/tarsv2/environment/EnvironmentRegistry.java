package com.tarsv2.environment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Registry of all available execution environments.
 *
 * <p>Environments are registered at startup and looked up by ID
 * when OpenClaw resolves an Intent's target environment.</p>
 */
public final class EnvironmentRegistry {

    private static final Logger log = LoggerFactory.getLogger(EnvironmentRegistry.class);

    private final Map<String, ExecutionEnvironment> environments = new LinkedHashMap<>();

    public void register(ExecutionEnvironment env) {
        environments.put(env.getId(), env);
        log.info("Registered environment: {}", env);
    }

    public ExecutionEnvironment get(String id) {
        return environments.get(id);
    }

    public Collection<ExecutionEnvironment> getAll() {
        return Collections.unmodifiableCollection(environments.values());
    }

    public int size() {
        return environments.size();
    }
}
