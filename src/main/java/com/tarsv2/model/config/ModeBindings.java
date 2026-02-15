package com.tarsv2.model.config;

import java.util.Objects;

public record ModeBindings(String chatModel, String codexModel, String researchModel) {

    public ModeBindings {
        Objects.requireNonNull(chatModel, "chatModel must not be null");
        Objects.requireNonNull(codexModel, "codexModel must not be null");
        Objects.requireNonNull(researchModel, "researchModel must not be null");
    }
}
