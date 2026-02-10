package com.tarsv2.agent;

import com.tarsv2.podman.PodmanResult;

/**
 * Base interface for all TARS agents.
 *
 * <p>Agents are modular task executors that encapsulate domain-specific
 * logic (resume parsing, Depop analysis, web scraping, etc.). Each agent:</p>
 * <ul>
 *   <li>Defines its own Podman tasks</li>
 *   <li>Processes results through the dual-LLM loop</li>
 *   <li>Proposes changes through the approval gate</li>
 * </ul>
 *
 * <p>New agents can be added without modifying core control logic —
 * they simply implement this interface and register with the agent registry.</p>
 */
public interface TarsAgent {

    /**
     * Returns the unique name of this agent.
     *
     * @return agent name (e.g., "ResumeAgent", "DepopAgent")
     */
    String getName();

    /**
     * Returns a short description of what this agent does.
     *
     * @return human-readable description
     */
    String getDescription();

    /**
     * Executes the agent's primary task.
     *
     * @param input task-specific input data
     * @return the result of the agent's work
     * @throws AgentExecutionException if the agent encounters an unrecoverable error
     */
    AgentResult execute(String input) throws AgentExecutionException;
}
