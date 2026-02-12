package com.tarsv2.openclaw;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A structured request from TARS to OpenClaw.
 *
 * <p>TARS produces Intents; it never executes tools directly.
 * OpenClaw validates, routes, and executes each Intent, then
 * returns a structured {@link IntentResult}.</p>
 */
public final class Intent {

    private final String id;
    private final IntentType type;
    private final IntentPayload payload;
    private final IntentContext context;
    private final Instant createdAt;

    public Intent(IntentType type, IntentPayload payload, IntentContext context) {
        this.id = UUID.randomUUID().toString().substring(0, 12);
        this.type = Objects.requireNonNull(type);
        this.payload = Objects.requireNonNull(payload);
        this.context = Objects.requireNonNull(context);
        this.createdAt = Instant.now();
    }

    public String getId() { return id; }
    public IntentType getType() { return type; }
    public IntentPayload getPayload() { return payload; }
    public IntentContext getContext() { return context; }
    public Instant getCreatedAt() { return createdAt; }

    @Override
    public String toString() {
        return String.format("Intent[%s | %s | %s]", id, type, context.getEnvironmentId());
    }
}
