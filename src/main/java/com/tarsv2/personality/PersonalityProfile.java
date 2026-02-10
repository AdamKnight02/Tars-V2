package com.tarsv2.personality;

import java.util.Objects;

/**
 * Immutable snapshot of TARS's personality configuration.
 *
 * <p>Combines humor level, current emotion, and identity metadata
 * into a single profile consumed by {@link DialogueStyle} to format
 * all user-facing output.</p>
 *
 * <p>Personality profiles can be swapped at runtime (e.g., "serious mode"
 * for production demos) without touching core logic.</p>
 */
public final class PersonalityProfile {

    private final String agentName;
    private final HumorLevel humorLevel;
    private volatile EmotionState currentEmotion;

    /**
     * Creates a new personality profile.
     *
     * @param agentName   display name for the agent (e.g., "TARS")
     * @param humorLevel  humor intensity setting
     * @param emotion     initial emotional state
     */
    public PersonalityProfile(String agentName, HumorLevel humorLevel, EmotionState emotion) {
        this.agentName = Objects.requireNonNull(agentName, "agentName");
        this.humorLevel = Objects.requireNonNull(humorLevel, "humorLevel");
        this.currentEmotion = Objects.requireNonNull(emotion, "emotion");
    }

    /** Creates the default TARS profile: medium humor, neutral mood. */
    public static PersonalityProfile defaultProfile() {
        return new PersonalityProfile("TARS", HumorLevel.MEDIUM, EmotionState.NEUTRAL);
    }

    public String getAgentName() {
        return agentName;
    }

    public HumorLevel getHumorLevel() {
        return humorLevel;
    }

    public EmotionState getCurrentEmotion() {
        return currentEmotion;
    }

    /**
     * Transitions TARS to a new emotional state.
     *
     * @param newState the new emotion
     */
    public void transitionEmotion(EmotionState newState) {
        this.currentEmotion = Objects.requireNonNull(newState, "newState");
    }

    @Override
    public String toString() {
        return String.format("[%s | humor=%s | mood=%s]", agentName, humorLevel, currentEmotion);
    }
}
