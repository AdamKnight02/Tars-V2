package com.tarsv2.openclaw;

import java.util.*;

/**
 * Structured payload carried by an Intent.
 *
 * <p>All data is key-value; no free-form text allowed.
 * This ensures deterministic execution by OpenClaw.</p>
 */
public final class IntentPayload {

    private final Map<String, String> fields;

    private IntentPayload(Map<String, String> fields) {
        this.fields = Map.copyOf(fields);
    }

    public String get(String key) {
        return fields.get(key);
    }

    public String require(String key) {
        String value = fields.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Missing required payload field: " + key);
        }
        return value;
    }

    public boolean has(String key) {
        return fields.containsKey(key);
    }

    public Map<String, String> toMap() {
        return fields;
    }

    @Override
    public String toString() {
        return "IntentPayload" + fields;
    }

    /**
     * Builder for constructing payloads field-by-field.
     */
    public static final class Builder {
        private final Map<String, String> fields = new LinkedHashMap<>();

        public Builder put(String key, String value) {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(value, "value");
            fields.put(key, value);
            return this;
        }

        public IntentPayload build() {
            return new IntentPayload(fields);
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
