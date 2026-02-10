package com.tarsv2.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Objects;

/**
 * ╔══════════════════════════════════════════════════════════════════╗
 * ║                    IMMUTABLE — DO NOT MODIFY                     ║
 * ║                                                                  ║
 * ║  Manages authentication for the TARS Web UI.                     ║
 * ║  Only authenticated humans may approve/reject proposals.         ║
 * ║  TARS agents must NEVER call approval endpoints.                 ║
 * ╚══════════════════════════════════════════════════════════════════╝
 */
public final class AuthenticationConfig {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationConfig.class);

    private final String username;
    private final String passwordHash;

    /**
     * @param username the admin username
     * @param password the admin password (stored as SHA-256 hash)
     */
    public AuthenticationConfig(String username, String password) {
        this.username = Objects.requireNonNull(username);
        this.passwordHash = hashPassword(password);
    }

    /**
     * Creates a default config from environment variables.
     * Falls back to "admin"/"tars-admin" if env vars are not set.
     */
    public static AuthenticationConfig fromEnvironment() {
        String user = System.getenv("TARS_ADMIN_USER");
        String pass = System.getenv("TARS_ADMIN_PASS");
        if (user == null || user.isBlank()) user = "admin";
        if (pass == null || pass.isBlank()) pass = "tars-admin";
        log.info("Authentication configured for user: {}", user);
        return new AuthenticationConfig(user, pass);
    }

    /**
     * Validates credentials.
     *
     * @param user the username to check
     * @param pass the password to check
     * @return true if credentials match
     */
    public boolean authenticate(String user, String pass) {
        if (user == null || pass == null) return false;
        return username.equals(user) && passwordHash.equals(hashPassword(pass));
    }

    /**
     * Validates a Basic auth header value.
     *
     * @param authHeader the Authorization header value (e.g., "Basic dXNlcjpwYXNz")
     * @return true if valid
     */
    public boolean authenticateBasic(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Basic ")) {
            return false;
        }
        try {
            String decoded = new String(Base64.getDecoder().decode(authHeader.substring(6)),
                    StandardCharsets.UTF_8);
            String[] parts = decoded.split(":", 2);
            if (parts.length != 2) return false;
            return authenticate(parts[0], parts[1]);
        } catch (Exception e) {
            log.warn("Failed to decode Basic auth header");
            return false;
        }
    }

    public String getUsername() {
        return username;
    }

    private static String hashPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(password.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
