package com.tarsv2.codex.instruction;

import com.fasterxml.jackson.databind.ObjectMapper;

public final class PatchInstructionParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public PatchInstruction parse(String json) {
        try {
            return MAPPER.readValue(json, PatchInstruction.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid patch instruction JSON", e);
        }
    }
}
