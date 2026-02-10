package com.tarsv2.llm;

/**
 * Defines the two distinct LLM roles in TARS's dual-model architecture.
 *
 * <p>The Actor plans and executes; the Reflector critiques and evaluates.
 * Separating these roles prevents self-reinforcing loops and enables
 * genuine adversarial evaluation of proposals.</p>
 */
public enum LlmRole {

    /**
     * The Actor model (LLaMA).
     * <p>Responsible for: planning, code generation, task execution,
     * Podman orchestration, and producing change proposals.</p>
     */
    ACTOR("LLaMA", "Planning and execution"),

    /**
     * The Reflection model (Qwen).
     * <p>Responsible for: critique, evaluation, quality scoring,
     * learning signal generation, and self-improvement proposals.</p>
     */
    REFLECTOR("Qwen", "Critique and evaluation");

    private final String modelFamily;
    private final String responsibility;

    LlmRole(String modelFamily, String responsibility) {
        this.modelFamily = modelFamily;
        this.responsibility = responsibility;
    }

    public String getModelFamily() { return modelFamily; }
    public String getResponsibility() { return responsibility; }
}
