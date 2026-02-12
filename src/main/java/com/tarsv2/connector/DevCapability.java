package com.tarsv2.connector;

/**
 * Capabilities that may be granted to TARS for developer operations.
 *
 * <p>Capabilities are granted by configuration, not by code changes.
 * Every connector action checks the permission model before executing.</p>
 */
public enum DevCapability {

    /** Read source code, browse files, view diffs. */
    READ_CODE("Read source code and browse repositories"),

    /** Propose changes: generate diffs, stage files, suggest patches. */
    PROPOSE_CHANGES("Propose code changes via diffs and patches"),

    /** Open pull requests and comment on them. Never merge or approve. */
    OPEN_PR("Open pull requests and post comments");

    private final String description;

    DevCapability(String description) {
        this.description = description;
    }

    public String getDescription() { return description; }
}
