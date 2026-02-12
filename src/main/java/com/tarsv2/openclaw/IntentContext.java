package com.tarsv2.openclaw;

import java.util.Objects;
import java.util.Set;

/**
 * Execution context attached to every Intent.
 *
 * <p>Captures the environment, cost estimate, confidence level,
 * and sudo scope (if any) for the requested action.</p>
 */
public final class IntentContext {

    private final String environmentId;
    private final String repository;
    private final double estimatedCost;
    private final double confidence;
    private final Set<String> sudoScopes;

    public IntentContext(String environmentId, String repository,
                         double estimatedCost, double confidence,
                         Set<String> sudoScopes) {
        this.environmentId = Objects.requireNonNull(environmentId);
        this.repository = repository;
        this.estimatedCost = estimatedCost;
        this.confidence = confidence;
        this.sudoScopes = sudoScopes != null ? Set.copyOf(sudoScopes) : Set.of();
    }

    public String getEnvironmentId() { return environmentId; }
    public String getRepository() { return repository; }
    public double getEstimatedCost() { return estimatedCost; }
    public double getConfidence() { return confidence; }
    public Set<String> getSudoScopes() { return sudoScopes; }
    public boolean hasSudo() { return !sudoScopes.isEmpty(); }

    @Override
    public String toString() {
        return String.format("IntentContext[env=%s, repo=%s, cost=%.4f, confidence=%.2f, sudo=%s]",
                environmentId, repository, estimatedCost, confidence, sudoScopes);
    }
}
