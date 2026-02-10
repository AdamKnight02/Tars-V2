package com.tarsv2.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central registry for all available TARS agents.
 *
 * <p>Agents register themselves here and can be looked up by name.
 * This allows new agents to be added without modifying core control logic —
 * they simply register at startup.</p>
 */
public final class AgentRegistry {

    private static final Logger log = LoggerFactory.getLogger(AgentRegistry.class);

    private final Map<String, TarsAgent> agents = new ConcurrentHashMap<>();

    /**
     * Registers an agent. Overwrites any existing agent with the same name.
     *
     * @param agent the agent to register
     */
    public void register(TarsAgent agent) {
        agents.put(agent.getName(), agent);
        log.info("Agent registered: {} — {}", agent.getName(), agent.getDescription());
    }

    /**
     * Looks up an agent by name.
     *
     * @param name the agent name
     * @return the agent, or empty if not found
     */
    public Optional<TarsAgent> get(String name) {
        return Optional.ofNullable(agents.get(name));
    }

    /**
     * Returns all registered agents.
     *
     * @return collection of agents
     */
    public Collection<TarsAgent> getAll() {
        return agents.values();
    }
}
