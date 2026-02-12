package com.tarsv2.codex;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Extracts and validates unified diff content from raw LLM output.
 *
 * <p>Keeps only lines that are valid unified diff syntax:
 * {@code ---}, {@code +++}, {@code @@}, context lines (space-prefixed),
 * additions ({@code +}), and deletions ({@code -}).</p>
 */
public final class DiffParser {

    private static final Logger log = LoggerFactory.getLogger(DiffParser.class);

    private DiffParser() {}

    /**
     * Extracts only valid unified diff lines from raw LLM output.
     * Discards commentary, JSON, explanations, and any other non-diff content.
     *
     * @param raw the raw LLM output
     * @return cleaned diff containing only valid unified diff lines, or empty string
     */
    public static String extractDiffLines(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }

        List<String> validLines = new ArrayList<>();
        boolean inDiffBlock = false;

        for (String line : raw.split("\\R")) {
            if (line.startsWith("--- ")) {
                inDiffBlock = true;
                validLines.add(line);
            } else if (line.startsWith("+++ ")) {
                validLines.add(line);
            } else if (line.startsWith("@@ ")) {
                validLines.add(line);
            } else if (inDiffBlock && line.startsWith("+")) {
                validLines.add(line);
            } else if (inDiffBlock && line.startsWith("-")) {
                validLines.add(line);
            } else if (inDiffBlock && line.startsWith(" ")) {
                validLines.add(line);
            } else if (line.startsWith("diff --git ")) {
                inDiffBlock = false;
                validLines.add(line);
            }
        }

        return String.join("\n", validLines);
    }

    /**
     * Validates that the diff contains at least one valid unified diff header
     * ({@code --- a/} and {@code +++ b/} pair).
     *
     * @param diff the diff string to validate
     * @return true if the diff contains valid headers
     */
    public static boolean hasValidDiffHeader(String diff) {
        if (diff == null || diff.isBlank()) {
            return false;
        }
        boolean hasMinus = false;
        boolean hasPlus = false;
        for (String line : diff.split("\\R")) {
            if (line.startsWith("--- a/")) {
                hasMinus = true;
            } else if (line.startsWith("+++ b/") && hasMinus) {
                hasPlus = true;
                break;
            }
        }
        return hasMinus && hasPlus;
    }
}
