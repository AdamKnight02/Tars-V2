package com.tarsv2.connector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Permission model for TARS developer capabilities.
 *
 * <p>Capabilities are granted by external configuration — TARS
 * cannot grant capabilities to itself. Every connector action
 * checks this model before proceeding.</p>
 */
public final class DevPermissionModel {

    private static final Logger log = LoggerFactory.getLogger(DevPermissionModel.class);

    private final Set<DevCapability> granted;

    private DevPermissionModel(Set<DevCapability> granted) {
        this.granted = Set.copyOf(granted);
    }

    /**
     * Checks whether a capability is granted.
     */
    public boolean hasCapability(DevCapability cap) {
        boolean has = granted.contains(cap);
        if (!has) {
            log.debug("Capability check failed: {} not granted", cap);
        }
        return has;
    }

    /**
     * Requires a capability, throwing if not granted.
     */
    public void requireCapability(DevCapability cap) {
        if (!hasCapability(cap)) {
            throw new SecurityException("DevCapability " + cap + " not granted");
        }
    }

    /**
     * Returns all granted capabilities.
     */
    public Set<DevCapability> getGranted() {
        return granted;
    }

    @Override
    public String toString() {
        return "DevPermissionModel" + granted;
    }

    /**
     * Creates a permission model from environment configuration.
     * Reads TARS_DEV_CAPABILITIES env var (comma-separated).
     * Defaults to READ_CODE only if not set.
     */
    public static DevPermissionModel fromEnvironment() {
        String raw = System.getenv("TARS_DEV_CAPABILITIES");
        if (raw == null || raw.isBlank()) {
            log.info("TARS_DEV_CAPABILITIES not set — defaulting to READ_CODE only");
            return new DevPermissionModel(Set.of(DevCapability.READ_CODE));
        }

        Set<DevCapability> caps = new LinkedHashSet<>();
        for (String token : raw.split(",")) {
            try {
                caps.add(DevCapability.valueOf(token.trim().toUpperCase()));
            } catch (IllegalArgumentException e) {
                log.warn("Unknown capability in TARS_DEV_CAPABILITIES: {}", token.trim());
            }
        }

        log.info("Loaded dev capabilities: {}", caps);
        return new DevPermissionModel(caps);
    }

    /**
     * Creates a permission model with explicit capabilities.
     */
    public static DevPermissionModel withCapabilities(DevCapability... caps) {
        return new DevPermissionModel(Set.of(caps));
    }
}
