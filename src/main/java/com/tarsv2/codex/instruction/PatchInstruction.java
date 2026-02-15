package com.tarsv2.codex.instruction;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PatchInstruction(String file, PatchOperation operation, String location, String content) {
}
