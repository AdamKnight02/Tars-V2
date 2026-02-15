package com.tarsv2.tool.diff;

import com.tarsv2.codex.DeterministicPatchBuilder;
import com.tarsv2.codex.instruction.PatchInstruction;

import java.io.IOException;

public final class DiffToolFacade {

    private final DeterministicPatchBuilder patchBuilder;

    public DiffToolFacade(DeterministicPatchBuilder patchBuilder) {
        this.patchBuilder = patchBuilder;
    }

    public String buildUnifiedDiff(String targetFilePath, String originalContent, PatchInstruction instruction) throws IOException {
        return patchBuilder.buildUnifiedDiff(targetFilePath, originalContent, instruction);
    }
}
