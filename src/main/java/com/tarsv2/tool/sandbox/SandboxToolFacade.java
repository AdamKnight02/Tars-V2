package com.tarsv2.tool.sandbox;

import java.nio.file.Files;
import java.nio.file.Path;

public final class SandboxToolFacade {

    private final Path sandboxRoot;

    public SandboxToolFacade(Path sandboxRoot) {
        this.sandboxRoot = sandboxRoot;
    }

    public String readFile(String targetFilePath) {
        Path filePath = sandboxRoot.resolve(targetFilePath).normalize();
        Path normalizedRoot = sandboxRoot.toAbsolutePath().normalize();
        Path absoluteFile = filePath.toAbsolutePath().normalize();

        if (!absoluteFile.startsWith(normalizedRoot)) {
            throw new IllegalArgumentException("Target file path escapes sandbox root.");
        }

        if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            throw new IllegalArgumentException("Target file not found in sandbox: " + targetFilePath);
        }

        try {
            return Files.readString(filePath);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read sandbox file: " + targetFilePath, e);
        }
    }
}
