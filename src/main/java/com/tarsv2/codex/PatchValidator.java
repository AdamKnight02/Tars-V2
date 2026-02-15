package com.tarsv2.codex;

import com.tarsv2.codex.instruction.PatchInstruction;
import com.tarsv2.codex.instruction.PatchOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PatchValidator {

    private static final Logger log = LoggerFactory.getLogger(PatchValidator.class);
    private static final String ALLOWED_PREFIX = "src/main/java/";
    private static final Pattern DIFF_GIT_PATH = Pattern.compile("^diff --git a/(.+) b/(.+)$");

    public ValidationResult validateInstruction(PatchInstruction instruction) {
        if (instruction == null) {
            return ValidationResult.failed("Patch instruction cannot be null", List.of());
        }
        if (instruction.file() == null || instruction.file().isBlank()) {
            return ValidationResult.failed("Patch instruction file is required", List.of());
        }
        if (!instruction.file().startsWith(ALLOWED_PREFIX)) {
            return ValidationResult.failed("Patch modifies disallowed path: " + instruction.file(), List.of(instruction.file()));
        }
        if (instruction.operation() == null) {
            return ValidationResult.failed("Patch operation is required", List.of(instruction.file()));
        }
        if (!isOperationValid(instruction.operation(), instruction.location())) {
            return ValidationResult.failed("Patch operation/location is invalid: " + instruction.operation(), List.of(instruction.file()));
        }
        if (instruction.content() == null) {
            return ValidationResult.failed("Patch content is required", List.of(instruction.file()));
        }
        return ValidationResult.passed(List.of(instruction.file()));
    }

    public ValidationResult validate(String diff) {
        log.info("Raw diff for validation:\n{}", diff);

        if (diff == null || diff.isBlank()) {
            return ValidationResult.failed("Empty diff", List.of());
        }

        if (!diff.startsWith("diff --git a/")) {
            return ValidationResult.failed("Diff must begin with a valid diff header", List.of());
        }

        List<String> files = extractFiles(diff);
        if (files.isEmpty()) {
            return ValidationResult.failed("No file changes found in diff", files);
        }

        for (String file : files) {
            if (!file.startsWith(ALLOWED_PREFIX)) {
                return ValidationResult.failed("Patch modifies disallowed path: " + file, files);
            }
        }

        return ValidationResult.passed(files);
    }

    private boolean isOperationValid(PatchOperation operation, String location) {
        return switch (operation) {
            case REPLACE, APPEND -> true;
            case REPLACE_HINT, INSERT_AFTER_HINT -> location != null && !location.isBlank();
        };
    }

    private List<String> extractFiles(String diff) {
        List<String> files = new ArrayList<>();
        for (String line : diff.split("\\R")) {
            Matcher matcher = DIFF_GIT_PATH.matcher(line);
            if (matcher.matches()) {
                String file = matcher.group(2);
                if (!files.contains(file)) {
                    files.add(file);
                }
            }
        }
        return files;
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
