package com.tarsv2.personality;

/**
 * Controls how much personality flair TARS injects into output.
 *
 * <p>Configurable per-session or globally. Higher levels produce
 * more quips, metaphors, and self-aware commentary.</p>
 */
public enum HumorLevel {

    /** Minimal personality — professional tone only. */
    MINIMAL(0),

    /** Light seasoning of wit. Good for production logs. */
    LOW(25),

    /** Default. Balanced humor and utility. */
    MEDIUM(50),

    /** Full TARS personality. Expect dolphin metaphors. */
    HIGH(75),

    /** Unhinged. Every message is a performance. */
    MAXIMUM(100);

    private final int intensity;

    HumorLevel(int intensity) {
        this.intensity = intensity;
    }

    /** Numeric intensity value (0–100) for interpolation in templates. */
    public int getIntensity() {
        return intensity;
    }
}
