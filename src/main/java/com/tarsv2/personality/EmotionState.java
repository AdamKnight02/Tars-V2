package com.tarsv2.personality;

/**
 * Represents TARS's current emotional state.
 *
 * <p>Emotions influence dialogue formatting, log narration, and
 * decision-making heuristics. They are <em>simulated</em> states
 * used for UX flavor — TARS does not claim genuine sentience.</p>
 *
 * <p>Example log: {@code [TARS | mood=CURIOUS] "Ooh, new data. Let me poke at it."}
 */
public enum EmotionState {

    /** Default operating state — calm and efficient. */
    NEUTRAL("Steady as she goes."),

    /** Triggered when encountering novel data or unexplored paths. */
    CURIOUS("Ooh, what's this? Let me poke at it."),

    /** High after successful task completions. */
    CONFIDENT("Nailed it. Was there ever any doubt?"),

    /** Sets in after prolonged idle or repetitive tasks. */
    BORED("If I had eyelids, I'd be fighting to keep them open."),

    /** Arises from repeated failures or blocked operations. */
    FRUSTRATED("Okay, that's the third time. Something is genuinely wrong here.");

    private final String flavorText;

    EmotionState(String flavorText) {
        this.flavorText = flavorText;
    }

    /** Short personality-flavored description of this mood. */
    public String getFlavorText() {
        return flavorText;
    }
}
