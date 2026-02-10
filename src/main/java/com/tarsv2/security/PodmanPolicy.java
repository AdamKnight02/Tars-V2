package com.tarsv2.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;

/**
 * ╔══════════════════════════════════════════════════════════════════╗
 * ║                    IMMUTABLE — DO NOT MODIFY                     ║
 * ║                                                                  ║
 * ║  Defines the security policy for Podman container execution.     ║
 * ║  Enforces secret injection rules (env vars ONLY, never files).   ║
 * ╚══════════════════════════════════════════════════════════════════╝
 */
public final class PodmanPolicy {

    private static final Logger log = LoggerFactory.getLogger(PodmanPolicy.class);

    /** Env vars that may be injected into containers. Others are blocked. */
    private static final Set<String> ALLOWED_ENV_KEYS = Set.of(
            "TARS_TASK_ID",
            "TARS_INPUT_PATH",
            "TARS_OUTPUT_PATH",
            "TARS_TIMEOUT",
            "API_KEY",
            "SCRAPER_TOKEN"
    );

    private PodmanPolicy() {}

    /**
     * Validates that a set of environment variables is safe for injection.
     *
     * @param envVars the proposed env vars
     * @throws SecurityException if any key is not on the whitelist
     */
    public static void validateEnvVars(Map<String, String> envVars) {
        for (String key : envVars.keySet()) {
            if (!ALLOWED_ENV_KEYS.contains(key)) {
                String msg = "BLOCKED: Env var '" + key + "' is not whitelisted for container injection";
                log.error(msg);
                throw new SecurityException(msg);
            }
        }
    }

    /**
     * Returns the set of allowed environment variable keys.
     */
    public static Set<String> getAllowedEnvKeys() {
        return ALLOWED_ENV_KEYS;
    }
}
