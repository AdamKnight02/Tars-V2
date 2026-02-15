package com.tarsv2.integration;

import com.tarsv2.codex.PatchValidator;
import com.tarsv2.codex.instruction.PatchInstruction;
import com.tarsv2.codex.instruction.PatchOperation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatchScopeValidatorIT {

    @Test
    void rejectsInstructionOutsideMainJavaScope() {
        PatchValidator validator = new PatchValidator();

        PatchValidator.ValidationResult result = validator.validateInstruction(
                new PatchInstruction("README.md", PatchOperation.REPLACE, null, "x"));

        assertFalse(result.valid());
    }

    @Test
    void acceptsInstructionWithinMainJavaScope() {
        PatchValidator validator = new PatchValidator();

        PatchValidator.ValidationResult result = validator.validateInstruction(
                new PatchInstruction("src/main/java/com/tarsv2/Example.java", PatchOperation.APPEND, null, "x"));

        assertTrue(result.valid());
    }
}
