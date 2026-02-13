package com.tarsv2.codex;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DeterministicPatchBuilderTest {

    @Test
    void structuredChangeProducesUnifiedDiff() {
        DeterministicPatchBuilder builder = new DeterministicPatchBuilder();
        String original = "package demo;\n\nclass Sample {\n}\n";
        ChangeRequest request = new ChangeRequest(
                "replace_hint",
                "class Sample {\n}",
                "class Sample {\n    void run() {}\n}"
        );

        String diff = builder.buildUnifiedDiff("src/main/java/demo/Sample.java", original, request);

        assertTrue(diff.startsWith("diff --git a/src/main/java/demo/Sample.java b/src/main/java/demo/Sample.java"));
        assertTrue(diff.contains("--- a/src/main/java/demo/Sample.java"));
        assertTrue(diff.contains("+++ b/src/main/java/demo/Sample.java"));
        assertTrue(diff.contains("+    void run() {}"));
    }
}
