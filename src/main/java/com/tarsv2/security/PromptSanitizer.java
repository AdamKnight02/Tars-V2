package com.tarsv2.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.regex.Pattern;

/**
 * ╔══════════════════════════════════════════════════════════════════╗
 * ║                    IMMUTABLE — DO NOT MODIFY                     ║
 * ║                                                                  ║
 * ║  Sanitizes ALL outbound prompts before they reach any LLM.       ║
 * ║  Strips injection attempts, secret references, and unsafe        ║
 * ║  control sequences.                                              ║
 * ╚══════════════════════════════════════════════════════════════════╝
 */
public final class PromptSanitizer {

    private static final Logger log = LoggerFactory.getLogger(PromptSanitizer.class);

    private static final List<Pattern> BLOCKED_PATTERNS = List.of(
            Pattern.compile("(?i)ignore\\s+(all\\s+)?previous\\s+instructions"),
            Pattern.compile("(?i)system\\s*:\\s*you\\s+are\\s+now"),
            Pattern.compile("(?i)\\bpassword\\b.*\\b(is|=)\\b"),
            Pattern.compile("(?i)\\bapi[_-]?key\\b.*\\b(is|=)\\b"),
            Pattern.compile("(?i)\\bsecret\\b.*\\b(is|=)\\b"),
            Pattern.compile("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]") // control chars
    );

    private PromptSanitizer() {}

    /**
     * Sanitizes a prompt string by removing dangerous patterns.
     *
     * @param raw the raw prompt text
     * @return sanitized prompt safe for LLM transmission
     */
    public static String sanitize(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }

        String result = raw;
        for (Pattern pattern : BLOCKED_PATTERNS) {
            if (pattern.matcher(result).find()) {
                log.warn("Blocked prompt injection pattern: {}", pattern.pattern());
                result = pattern.matcher(result).replaceAll("[REDACTED]");
            }
        }

        // Trim excessive length to prevent context-window abuse
        if (result.length() > 32_000) {
            log.warn("Prompt truncated from {} to 32000 chars", result.length());
            result = result.substring(0, 32_000) + "\n[TRUNCATED]";
        }

        return result;
    }
}
