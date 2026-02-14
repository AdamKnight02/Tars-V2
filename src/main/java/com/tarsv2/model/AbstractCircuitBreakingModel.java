package com.tarsv2.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

abstract class AbstractCircuitBreakingModel implements TarsModel {

    private static final Logger log = LoggerFactory.getLogger(AbstractCircuitBreakingModel.class);

    private final int failureThreshold;
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private volatile boolean open = false;
    private volatile Instant openedAt = Instant.EPOCH;

    protected AbstractCircuitBreakingModel(int failureThreshold) {
        this.failureThreshold = Math.max(1, failureThreshold);
    }

    @Override
    public final ModelResponse generate(ModelRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        if (open) {
            return ModelResponse.unavailable("Circuit breaker is open since " + openedAt);
        }
        ModelResponse response = doGenerate(request);
        if (response.status() == ModelResponse.Status.OK) {
            consecutiveFailures.set(0);
            open = false;
            return response;
        }

        int failures = consecutiveFailures.incrementAndGet();
        if (failures >= failureThreshold) {
            open = true;
            openedAt = Instant.now();
            log.warn("Opening circuit breaker after {} consecutive failures", failures);
        }
        return response;
    }

    protected abstract ModelResponse doGenerate(ModelRequest request);
}
