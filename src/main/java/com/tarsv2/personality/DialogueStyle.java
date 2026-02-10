package com.tarsv2.personality;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalTime;
import java.util.Objects;

/**
 * Formats ALL user-facing output through TARS's personality lens.
 *
 * <p>Every message the user sees — CLI output, log narration, status
 * updates — passes through this formatter. The {@link PersonalityProfile}
 * determines tone, and {@link EmotionState} adds situational flavor.</p>
 *
 * <h3>Example outputs at different humor levels:</h3>
 * <pre>
 *   MINIMAL:  "Task completed: resume parsed."
 *   MEDIUM:   "[TARS] Resume parsed. Not bad — this candidate actually has skills."
 *   MAXIMUM:  "[TARS | mood=CONFIDENT] Resume parsed. Honestly? Chef's kiss. I'd hire them myself if I had a budget. Or hands."
 * </pre>
 */
public final class DialogueStyle {

    private static final Logger log = LoggerFactory.getLogger(DialogueStyle.class);

    private final PersonalityProfile profile;

    /**
     * @param profile the personality profile driving this formatter
     */
    public DialogueStyle(PersonalityProfile profile) {
        this.profile = Objects.requireNonNull(profile, "profile");
    }

    /**
     * Formats a bare message with TARS's personality.
     *
     * @param rawMessage the unformatted message content
     * @return personality-formatted string ready for display
     */
    public String format(String rawMessage) {
        if (profile.getHumorLevel().getIntensity() == 0) {
            return rawMessage;
        }

        String prefix = buildPrefix();
        String suffix = buildMoodSuffix();
        return prefix + " " + rawMessage + suffix;
    }

    /**
     * Formats and immediately prints to stdout.
     *
     * @param rawMessage the unformatted message
     */
    public void say(String rawMessage) {
        String formatted = format(rawMessage);
        System.out.println(formatted);
        log.info(formatted);
    }

    /**
     * Narrates an action in TARS's voice — used for log output.
     *
     * @param action what TARS is doing (e.g., "spinning up container")
     * @return narrated string
     */
    public String narrate(String action) {
        return switch (profile.getHumorLevel()) {
            case MINIMAL -> action;
            case LOW -> String.format("[%s] %s", profile.getAgentName(), action);
            case MEDIUM -> String.format("[%s] %s %s", profile.getAgentName(), action, getTimeBasedQuip());
            case HIGH, MAXIMUM -> String.format("[%s | mood=%s] %s %s",
                    profile.getAgentName(), profile.getCurrentEmotion(), action, getTimeBasedQuip());
        };
    }

    private String buildPrefix() {
        if (profile.getHumorLevel().getIntensity() >= 75) {
            return String.format("[%s | mood=%s]", profile.getAgentName(), profile.getCurrentEmotion());
        }
        return String.format("[%s]", profile.getAgentName());
    }

    private String buildMoodSuffix() {
        if (profile.getHumorLevel().getIntensity() < 50) {
            return "";
        }
        return switch (profile.getCurrentEmotion()) {
            case CURIOUS -> " *tilts head*";
            case CONFIDENT -> " *adjusts nonexistent sunglasses*";
            case BORED -> " *yawns in binary*";
            case FRUSTRATED -> " *deep digital sigh*";
            case NEUTRAL -> "";
        };
    }

    private String getTimeBasedQuip() {
        int hour = LocalTime.now().getHour();
        if (hour < 6) return "(Why are we both up at this hour?)";
        if (hour < 12) return "(Morning ops — coffee protocol engaged.)";
        if (hour < 18) return "(Afternoon shift. Peak productivity window.)";
        return "(Night mode. Running on pure determination.)";
    }
}
