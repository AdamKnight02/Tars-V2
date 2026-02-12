package com.tarsv2.personality;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalTime;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Formats ALL user-facing output through TARS's personality lens.
 *
 * <p>Every message the user sees — CLI output, narration, status
 * updates — passes through this formatter. The {@link PersonalityProfile}
 * determines tone, and {@link EmotionState} can add situational flavor.</p>
 */
public final class DialogueStyle {

    private static final Logger log = LoggerFactory.getLogger(DialogueStyle.class);

    /**
     * Default probability of adding personality flair to CHAT responses.
     * Can be overridden with {@code TARS_PERSONALITY_RATE}.
     */
    private static final double PERSONALITY_PROBABILITY = 0.15;

    private final PersonalityProfile profile;
    private final double personalityProbability;

    /**
     * Controls if/how much personality flair can be injected for output.
     */
    public enum OutputMode {
        SYSTEM,
        NARRATION,
        CHAT
    }

    /**
     * @param profile the personality profile driving this formatter
     */
    public DialogueStyle(PersonalityProfile profile) {
        this.profile = Objects.requireNonNull(profile, "profile");
        this.personalityProbability = resolvePersonalityProbability();
    }

    /**
     * Formats a bare message with TARS's personality profile.
     *
     * @param rawMessage the unformatted message content
     * @param mode target output mode controlling flair injection behavior
     * @return personality-formatted string ready for display
     */
    public String format(String rawMessage, OutputMode mode) {
        if (profile.getHumorLevel().getIntensity() == 0) {
            return rawMessage;
        }

        String prefix = buildPrefix();
        String suffix = shouldInjectFlair(mode) ? buildMoodSuffix() : "";
        return prefix + " " + rawMessage + suffix;
    }

    /**
     * Convenience formatting overload that defaults to chat mode.
     */
    public String format(String rawMessage) {
        return format(rawMessage, OutputMode.CHAT);
    }

    /**
     * Formats and immediately prints to stdout.
     *
     * <p>Safety note: structured logs always receive deterministic, no-flair
     * SYSTEM formatting to avoid personality noise in audit trails.</p>
     *
     * @param rawMessage the unformatted message
     * @param mode target output mode
     */
    public void say(String rawMessage, OutputMode mode) {
        String formatted = format(rawMessage, mode);
        System.out.println(formatted);
        log.info(format(rawMessage, OutputMode.SYSTEM));
    }

    /**
     * Backward-compatible overload for safety-critical callsites that cannot be refactored in this patch.
     */
    public void say(String rawMessage) {
        say(rawMessage, OutputMode.SYSTEM);
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
            case CURIOUS -> " *analyzing options*";
            case CONFIDENT -> " *adjusts nonexistent sunglasses*";
            case BORED -> " *yawns in binary*";
            case FRUSTRATED -> " *deep digital sigh*";
            case NEUTRAL -> "";
        };
    }

    private boolean shouldInjectFlair(OutputMode mode) {
        if (mode == OutputMode.SYSTEM) {
            return false;
        }

        double jitter = ThreadLocalRandom.current().nextDouble(-0.05, 0.05);
        double baseRate = mode == OutputMode.NARRATION
                ? personalityProbability - 0.025
                : personalityProbability + 0.025;
        double adjusted = Math.max(0.0, Math.min(1.0, baseRate + jitter));
        return ThreadLocalRandom.current().nextDouble() < adjusted;
    }

    private double resolvePersonalityProbability() {
        String env = System.getenv("TARS_PERSONALITY_RATE");
        if (env == null || env.isBlank()) {
            return PERSONALITY_PROBABILITY;
        }
        try {
            return Math.max(0.0, Math.min(1.0, Double.parseDouble(env.trim())));
        } catch (NumberFormatException e) {
            log.warn("Invalid TARS_PERSONALITY_RATE '{}', using default {}", env, PERSONALITY_PROBABILITY);
            return PERSONALITY_PROBABILITY;
        }
    }

    private String getTimeBasedQuip() {
        int hour = LocalTime.now().getHour();
        if (hour < 6) return "(Why are we both up at this hour?)";
        if (hour < 12) return "(Morning ops — coffee protocol engaged.)";
        if (hour < 18) return "(Afternoon shift. Peak productivity window.)";
        return "(Night mode. Running on pure determination.)";
    }
}
