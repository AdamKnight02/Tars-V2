package com.tarsv2.sudo;

import com.tarsv2.personality.DialogueStyle;
import com.tarsv2.personality.HumorLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages sudo tokens for temporary elevated privileges.
 *
 * <p>Sudo flow:</p>
 * <ol>
 *   <li>Detect "sudo:" prefix in command</li>
 *   <li>Request password (masked)</li>
 *   <li>Issue temporary SudoToken with expiry and scopes</li>
 *   <li>Validate all elevated operations against the token</li>
 * </ol>
 *
 * <p><strong>KILL SWITCH:</strong> Immediate sudo revocation.
 * Cancels all elevated intents. Cannot be modified by TARS.</p>
 *
 * <p>Sudo MAY NOT:</p>
 * <ul>
 *   <li>Modify immutable files</li>
 *   <li>Approve changes (only humans approve)</li>
 *   <li>Alter safety logic</li>
 *   <li>Persist authority beyond expiry</li>
 * </ul>
 */
public final class SudoManager {

    private static final Logger log = LoggerFactory.getLogger(SudoManager.class);

    /** Default sudo session duration. */
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(15);

    /** Maximum sudo session duration. */
    private static final Duration MAX_TTL = Duration.ofHours(1);

    /**
     * Files that sudo CANNOT touch, regardless of scope.
     */
    private static final Set<String> IMMUTABLE_FILES = Set.of(
            "ApprovalGate.java",
            "ChangeControl.java",
            "PodmanPolicy.java",
            "SecretManager.java",
            "PromptSanitizer.java",
            "AuthenticationConfig.java"
    );

    private final Map<String, SudoToken> activeTokens = new ConcurrentHashMap<>();
    private final DialogueStyle dialogue;
    private final String sudoPasswordHash;

    /**
     * @param dialogue      personality formatter (minimal humor for sudo events)
     * @param sudoPassword  the sudo password (hashed internally)
     */
    public SudoManager(DialogueStyle dialogue, String sudoPassword) {
        this.dialogue = dialogue;
        this.sudoPasswordHash = hashPassword(sudoPassword);
    }

    /**
     * Detects if a command is a sudo request.
     */
    public boolean isSudoRequest(String input) {
        return input != null && input.trim().startsWith("sudo:");
    }

    /**
     * Strips the sudo: prefix from a command.
     */
    public String extractCommand(String sudoInput) {
        return sudoInput.trim().substring(5).trim();
    }

    /**
     * Authenticates and issues a sudo token.
     *
     * @param password       the sudo password
     * @param requestedScopes scopes requested
     * @return the issued token, or empty if auth fails
     */
    public Optional<SudoToken> authenticate(String password, Set<String> requestedScopes) {
        if (!hashPassword(password).equals(sudoPasswordHash)) {
            log.warn("Sudo authentication failed");
            // Minimal humor for security events
            dialogue.say("[SECURITY] Sudo authentication failed. Access denied.");
            return Optional.empty();
        }

        SudoToken token = new SudoToken(requestedScopes, "human-operator", DEFAULT_TTL);
        activeTokens.put(token.getId(), token);

        log.info("Sudo token issued: {}", token);
        dialogue.say("[SUDO] Elevated session " + token.getId()
                + " granted. Scopes: " + requestedScopes
                + ". Expires in " + DEFAULT_TTL.toMinutes() + " minutes.");

        return Optional.of(token);
    }

    /**
     * Validates that a sudo token permits a given operation.
     *
     * @param tokenId    the token ID
     * @param scope      the required scope
     * @param targetFile optional target file (checked against immutable list)
     * @return true if the operation is permitted
     */
    public boolean validate(String tokenId, String scope, String targetFile) {
        SudoToken token = activeTokens.get(tokenId);
        if (token == null || !token.isValid()) {
            log.warn("Sudo validation failed: token {} invalid or expired", tokenId);
            return false;
        }

        if (!token.hasScope(scope)) {
            log.warn("Sudo validation failed: token {} lacks scope {}", tokenId, scope);
            return false;
        }

        // HARD SAFETY: immutable files are NEVER modifiable, even with sudo
        if (targetFile != null && isImmutableFile(targetFile)) {
            log.warn("BLOCKED: Sudo attempt to modify immutable file: {}", targetFile);
            dialogue.say("[SECURITY] Blocked: " + targetFile
                    + " is immutable. Sudo does not override safety boundaries.");
            return false;
        }

        return true;
    }

    /**
     * KILL SWITCH — immediately revokes ALL active sudo tokens.
     *
     * <p>This action:</p>
     * <ul>
     *   <li>Revokes every active token</li>
     *   <li>Cannot be prevented by TARS</li>
     *   <li>Is logged as a security event</li>
     * </ul>
     */
    public void killSwitch() {
        int revoked = 0;
        for (SudoToken token : activeTokens.values()) {
            if (token.isValid()) {
                token.revoke();
                revoked++;
            }
        }
        activeTokens.clear();

        log.warn("KILL SWITCH activated: {} sudo tokens revoked", revoked);
        dialogue.say("[SECURITY] Kill switch activated. "
                + revoked + " sudo sessions terminated immediately.");
    }

    /**
     * Revokes a specific sudo token.
     */
    public boolean revoke(String tokenId) {
        SudoToken token = activeTokens.remove(tokenId);
        if (token != null) {
            token.revoke();
            log.info("Sudo token {} revoked", tokenId);
            dialogue.say("[SUDO] Session " + tokenId + " revoked.");
            return true;
        }
        return false;
    }

    /**
     * Returns currently active (non-expired) token count.
     */
    public int getActiveCount() {
        return (int) activeTokens.values().stream()
                .filter(SudoToken::isValid)
                .count();
    }

    /**
     * Checks if a filename is in the immutable list.
     */
    public static boolean isImmutableFile(String filename) {
        if (filename == null) return false;
        String basename = filename.contains("/")
                ? filename.substring(filename.lastIndexOf('/') + 1)
                : filename;
        return IMMUTABLE_FILES.contains(basename);
    }

    private static String hashPassword(String password) {
        if (password == null) return "";
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(password.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.Base64.getEncoder().encodeToString(hash);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
