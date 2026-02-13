package com.tarsv2.codex;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PatchValidator {

    private static final String ALLOWED_PREFIX = "src/main/java/com/tarsv2/";
    private static final Pattern DIFF_GIT_PATH = Pattern.compile("^diff --git a/(.+) b/(.+)$");
    private static final Pattern MINUS_HEADER = Pattern.compile("^--- a/(.+)$");
    private static final Pattern PLUS_HEADER = Pattern.compile("^\\+\\+\\+ b/(.+)$");
    private static final Pattern HUNK_HEADER = Pattern.compile(
            "^@@ -\\d+(?:,\\d+)? \\+\\d+(?:,\\d+)? @@");

    public ValidationResult validate(String diff) {
        if (diff == null || diff.isBlank()) {
            return ValidationResult.failed("Empty diff", List.of());
        }

        String[] lines = diff.split("\\R");

        // Check for required --- / +++ headers
        boolean hasMinusHeader = false;
        boolean hasPlusHeader = false;
        boolean hasHunkHeader = false;
        boolean hasContextLine = false;

        List<String> files = new ArrayList<>();

        for (String line : lines) {
            // Extract file paths from diff --git (if present)
            Matcher gitMatcher = DIFF_GIT_PATH.matcher(line);
            if (gitMatcher.matches()) {
                String file = gitMatcher.group(2);
                if (!files.contains(file)) {
                    files.add(file);
                }
            }

            // Extract file paths from --- a/ header
            Matcher minusMatcher = MINUS_HEADER.matcher(line);
            if (minusMatcher.matches()) {
                hasMinusHeader = true;
            }

            // Extract file paths from +++ b/ header
            Matcher plusMatcher = PLUS_HEADER.matcher(line);
            if (plusMatcher.matches()) {
                hasPlusHeader = true;
                String file = plusMatcher.group(1);
                if (!files.contains(file)) {
                    files.add(file);
                }
            }

            if (HUNK_HEADER.matcher(line).find()) {
                hasHunkHeader = true;
            }

            if (line.startsWith(" ")) {
                hasContextLine = true;
            }
        }

        // Structural validation: require --- / +++ headers
        if (!hasMinusHeader || !hasPlusHeader) {
            return ValidationResult.failed("Diff is not unified format — missing --- a/ and +++ b/ headers", files);
        }

        // Structural validation: require @@ hunk headers
        if (!hasHunkHeader) {
            return ValidationResult.failed("Diff has no @@ hunk headers", files);
        }

        // Structural validation: require context lines
        if (!hasContextLine) {
            return ValidationResult.failed("Diff has no context lines", files);
        }

        if (files.isEmpty()) {
            return ValidationResult.failed("No file changes found in diff", files);
        }

        // Security policy checks on referenced files
        for (String file : files) {
            if (!file.startsWith(ALLOWED_PREFIX)) {
                return ValidationResult.failed("Patch modifies disallowed path: " + file, files);
            }
            if (file.equals("pom.xml") || file.contains("build.gradle") || file.contains("settings.gradle")) {
                return ValidationResult.failed("Build system modifications are blocked: " + file, files);
            }
            if (file.endsWith("ProposalRegistry.java")) {
                return ValidationResult.failed("ProposalRegistry self-modification blocked", files);
            }
            String lowered = file.toLowerCase();
            if (lowered.contains("authentication") || lowered.contains("sandbox") || lowered.contains("kill")) {
                return ValidationResult.failed("Security-sensitive path is blocked: " + file, files);
            }
        }

        return ValidationResult.passed(files);
    }

    public record ValidationResult(boolean valid, String message, List<String> referencedFiles) {
        static ValidationResult passed(List<String> files) {
            return new ValidationResult(true, "PASS", List.copyOf(files));
        }

        static ValidationResult failed(String message, List<String> files) {
            return new ValidationResult(false, message, List.copyOf(files));
        }
    }
}
