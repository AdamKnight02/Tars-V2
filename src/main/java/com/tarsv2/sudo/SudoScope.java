package com.tarsv2.sudo;

/**
 * Predefined sudo scopes that define what elevation permits.
 *
 * <p>Sudo MAY allow:</p>
 * <ul>
 *   <li>Larger refactors (multi-file changes)</li>
 *   <li>Multi-repo scope</li>
 *   <li>Increased retry limits</li>
 * </ul>
 *
 * <p>Sudo MAY NOT:</p>
 * <ul>
 *   <li>Modify immutable files</li>
 *   <li>Approve changes</li>
 *   <li>Alter safety logic</li>
 *   <li>Persist authority</li>
 * </ul>
 */
public final class SudoScope {

    /** Allows multi-file refactoring in a single proposal. */
    public static final String LARGE_REFACTOR = "large-refactor";

    /** Allows operations across multiple repositories. */
    public static final String MULTI_REPO = "multi-repo";

    /** Increases retry limits for flaky operations. */
    public static final String INCREASED_RETRIES = "increased-retries";

    /** Allows elevated resource limits in sandboxes. */
    public static final String ELEVATED_RESOURCES = "elevated-resources";

    private SudoScope() {}
}
