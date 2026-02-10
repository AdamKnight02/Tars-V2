package com.tarsv2.agent;

/**
 * Thrown when an agent encounters an unrecoverable error during execution.
 */
public class AgentExecutionException extends Exception {

    /**
     * @param message description of the failure
     */
    public AgentExecutionException(String message) {
        super(message);
    }

    /**
     * @param message description of the failure
     * @param cause   the underlying cause
     */
    public AgentExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
