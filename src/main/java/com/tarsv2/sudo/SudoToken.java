package com.tarsv2.sudo;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Temporary elevated-privilege token for sudo operations.
 *
 * <p>A SudoToken grants time-limited, scoped elevation. It cannot:</p>
 * <ul>
 *   <li>Modify immutable files</li>
 *   <li>Approve changes (only humans approve)</li>
 *   <li>Alter safety logic</li>
 *   <li>Persist authority beyond expiry</li>
 * </ul>
 */
public final class SudoToken {

    private final String id;
    private final Set<String> grantedScopes;
    private final String issuer;
    private final Instant issuedAt;
    private final Instant expiresAt;
    private volatile boolean revoked;

    public SudoToken(Set<String> grantedScopes, String issuer, Duration ttl) {
        this.id = UUID.randomUUID().toString().substring(0, 8);
        this.grantedScopes = Set.copyOf(Objects.requireNonNull(grantedScopes));
        this.issuer = Objects.requireNonNull(issuer);
        this.issuedAt = Instant.now();
        this.expiresAt = issuedAt.plus(ttl);
        this.revoked = false;
    }

    public String getId() { return id; }
    public Set<String> getGrantedScopes() { return grantedScopes; }
    public String getIssuer() { return issuer; }
    public Instant getIssuedAt() { return issuedAt; }
    public Instant getExpiresAt() { return expiresAt; }

    /**
     * Checks if this token is currently valid (not expired, not revoked).
     */
    public boolean isValid() {
        return !revoked && Instant.now().isBefore(expiresAt);
    }

    /**
     * Checks if this token grants a specific scope.
     */
    public boolean hasScope(String scope) {
        return isValid() && grantedScopes.contains(scope);
    }

    /**
     * Revokes this token immediately. Cannot be undone.
     */
    void revoke() {
        this.revoked = true;
    }

    boolean isRevoked() {
        return revoked;
    }

    @Override
    public String toString() {
        return String.format("SudoToken[%s | scopes=%s | issuer=%s | valid=%s | expires=%s]",
                id, grantedScopes, issuer, isValid(), expiresAt);
    }
}
