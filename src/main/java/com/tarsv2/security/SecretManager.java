package com.tarsv2.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ╔══════════════════════════════════════════════════════════════════╗
 * ║                    IMMUTABLE — DO NOT MODIFY                     ║
 * ║                                                                  ║
 * ║  Manages secrets through opaque handles. Secrets are NEVER       ║
 * ║  exposed as raw strings in logs or serialized output.            ║
 * ║  Resolution happens only at the point of use.                    ║
 * ╚══════════════════════════════════════════════════════════════════╝
 */
public final class SecretManager {

    private static final Logger log = LoggerFactory.getLogger(SecretManager.class);

    private final Map<String, String> store = new ConcurrentHashMap<>();

    /**
     * Registers a secret under the given key.
     *
     * @param key   the secret identifier
     * @param value the secret value (stored in memory only)
     * @return an opaque handle for later resolution
     */
    public SecretHandle register(String key, String value) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        store.put(key, value);
        log.info("Secret registered: {} (value hidden)", key);
        return new SecretHandle(key, this);
    }

    /**
     * Registers a secret from an environment variable.
     *
     * @param key     the secret identifier
     * @param envVar  the environment variable name
     * @return an opaque handle, or null if the env var is not set
     */
    public SecretHandle registerFromEnv(String key, String envVar) {
        String value = System.getenv(envVar);
        if (value == null || value.isBlank()) {
            log.warn("Environment variable {} not set — secret '{}' unavailable", envVar, key);
            return null;
        }
        return register(key, value);
    }

    String resolve(String key) {
        String value = store.get(key);
        if (value == null) {
            throw new SecurityException("Secret not found: " + key);
        }
        return value;
    }

    /**
     * Opaque handle to a secret. The actual value is only resolved
     * at the point of use and never appears in toString/logs.
     */
    public static final class SecretHandle {
        private final String key;
        private final SecretManager manager;

        SecretHandle(String key, SecretManager manager) {
            this.key = key;
            this.manager = manager;
        }

        /** Resolves the secret value. Use sparingly and never log the result. */
        public String resolve() {
            return manager.resolve(key);
        }

        @Override
        public String toString() {
            return "SecretHandle[" + key + "=***]";
        }
    }
}
