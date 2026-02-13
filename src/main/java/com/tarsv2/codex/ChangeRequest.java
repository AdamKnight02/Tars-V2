package com.tarsv2.codex;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ChangeRequest(String action, String locationHint, String content) {
}
