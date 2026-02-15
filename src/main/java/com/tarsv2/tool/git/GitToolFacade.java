package com.tarsv2.tool.git;

import com.tarsv2.codex.PatchValidator;

public final class GitToolFacade {

    private final PatchValidator validator;

    public GitToolFacade(PatchValidator validator) {
        this.validator = validator;
    }

    public PatchValidator.ValidationResult validateForApply(String diff) {
        return validator.validate(diff);
    }
}
