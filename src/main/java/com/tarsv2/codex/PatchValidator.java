package com.tarsv2.codex;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PatchValidator {

    private static final String ALLOWED_PREFIX = "src/main/java/com/tarsv2/";
    private static final Pattern DIFF_PATH = Pattern.compile("^diff --git a/(.+) b/(.+)$");

    public ValidationResult validate(String diff) {
        if (diff == null || diff.isBlank()) {
            return ValidationResult.failed("Empty diff", List.of());
        }
        if (!diff.contains("diff --git ")) {
            return ValidationResult.failed("Diff is not unified format", List.of());
        }

        List<String> files = new ArrayList<>();
        for (String line : diff.split("\\R")) {
            Matcher matcher = DIFF_PATH.matcher(line);
            if (!matcher.matches()) {
                continue;
            }
            String file = matcher.group(2);
            files.add(file);

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

        if (files.isEmpty()) {
            return ValidationResult.failed("No file changes found in diff", files);
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
