package com.tarsv2.codex;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PatchValidator {

    private static final Logger log = LoggerFactory.getLogger(PatchValidator.class);
    private static final String REQUIRED_PREFIX = "--- a/src/";
    private static final String ALLOWED_PREFIX = "src/main/java/com/tarsv2/";
    private static final Pattern DIFF_GIT_PATH = Pattern.compile("^diff --git a/(.+) b/(.+)$");
    private static final Pattern MINUS_HEADER = Pattern.compile("^--- a/(.+)$");
    private static final Pattern PLUS_HEADER = Pattern.compile("^\\+\\+\\+ b/(.+)$");
    private static final Pattern HUNK_HEADER = Pattern.compile(
            "^@@ -\\d+(?:,\\d+)? \\+\\d+(?:,\\d+)? @@");

    public ValidationResult validate(String diff) {
        log.info("Raw diff for validation:\n{}", diff);

        ValidationResult result;
        if (diff == null || diff.isBlank()) {
            result = ValidationResult.failed("Empty diff", List.of());
            log.info("Validation result: {}", result.message());
            return result;
        }

        if (!diff.startsWith(REQUIRED_PREFIX)) {
            result = ValidationResult.failed("Diff must start with '--- a/src/'", List.of());
            log.info("Validation result: {}", result.message());
            return result;
        }

        String[] lines = diff.split("\\R");

        boolean hasMinusHeader = false;
        boolean hasPlusHeader = false;
        boolean hasHunkHeader = false;
        boolean hasContextLine = false;

        List<String> files = new ArrayList<>();

        for (String line : lines) {
            Matcher gitMatcher = DIFF_GIT_PATH.matcher(line);
            if (gitMatcher.matches()) {
                String file = gitMatcher.group(2);
                if (!files.contains(file)) {
                    files.add(file);
                }
            }

            Matcher minusMatcher = MINUS_HEADER.matcher(line);
            if (minusMatcher.matches()) {
                hasMinusHeader = true;
            }

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

        if (!hasMinusHeader || !hasPlusHeader) {
            result = ValidationResult.failed("Diff is not unified format — missing --- a/ and +++ b/ headers", files);
            log.info("Validation result: {}", result.message());
            return result;
        }

        if (!hasHunkHeader) {
            result = ValidationResult.failed("Diff has no @@ hunk headers", files);
            log.info("Validation result: {}", result.message());
            return result;
        }

        if (!hasContextLine) {
            result = ValidationResult.failed("Diff has no context lines", files);
            log.info("Validation result: {}", result.message());
            return result;
        }

        if (files.isEmpty()) {
            result = ValidationResult.failed("No file changes found in diff", files);
            log.info("Validation result: {}", result.message());
            return result;
        }

        for (String file : files) {
            if (!file.startsWith(ALLOWED_PREFIX)) {
                result = ValidationResult.failed("Patch modifies disallowed path: " + file, files);
                log.info("Validation result: {}", result.message());
                return result;
            }
            if (file.equals("pom.xml") || file.contains("build.gradle") || file.contains("settings.gradle")) {
                result = ValidationResult.failed("Build system modifications are blocked: " + file, files);
                log.info("Validation result: {}", result.message());
                return result;
            }
            if (file.endsWith("ProposalRegistry.java")) {
                result = ValidationResult.failed("ProposalRegistry self-modification blocked", files);
                log.info("Validation result: {}", result.message());
                return result;
            }
            String lowered = file.toLowerCase();
            if (lowered.contains("authentication") || lowered.contains("sandbox") || lowered.contains("kill")) {
                result = ValidationResult.failed("Security-sensitive path is blocked: " + file, files);
                log.info("Validation result: {}", result.message());
                return result;
            }
        }

        result = ValidationResult.passed(files);
        log.info("Validation result: {}", result.message());
        return result;
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
