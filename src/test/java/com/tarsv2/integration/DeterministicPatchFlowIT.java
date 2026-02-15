package com.tarsv2.integration;

import com.tarsv2.codex.CodexOrchestrator;
import com.tarsv2.llm.ReasoningModelClient;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeterministicPatchFlowIT {

    @Test
    void repeatedRunsProduceByteIdenticalDiffs() throws Exception {
        Path sandbox = Files.createTempDirectory("tars-codex-it");
        Path file = sandbox.resolve("src/main/java/demo/Sample.java");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "package demo;\n\nclass Sample {\n}\n");

        ReasoningModelClient deterministic = new ReasoningModelClient() {
            @Override
            public String chat(String prompt) {
                return "";
            }

            @Override
            public String generateDeterministicDiff(String prompt) {
                return "{\"file\":\"src/main/java/demo/Sample.java\",\"operation\":\"REPLACE_HINT\",\"location\":\"class Sample {\\n}\",\"content\":\"class Sample {\\n    void run() {}\\n}\"}";
            }
        };

        CodexOrchestrator orchestrator = new CodexOrchestrator(deterministic, sandbox);

        String diff1 = orchestrator.generateDiffOnly("src/main/java/demo/Sample.java", "Add run method");
        String diff2 = orchestrator.generateDiffOnly("src/main/java/demo/Sample.java", "Add run method");

        assertEquals(diff1, diff2);
        assertTrue(diff1.contains("+    void run() {}"));
    }
}
