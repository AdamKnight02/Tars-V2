package com.tarsv2;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

@Command(name = "codex", description = "Generate a diff for a target file using a task description.")
public final class CodexCommand implements Runnable {

    @ParentCommand
    private TarsCli parent;

    @Option(names = "--file", required = true, description = "Project-relative path to Java file")
    String filePath;

    @Option(names = "--task", required = true, description = "Description of desired change")
    String task;

    @Override
    public void run() {
        if (filePath == null || filePath.isBlank() || task == null || task.isBlank()) {
            System.out.println("[codex] Friendly error: missing --file or --task argument.");
            return;
        }
        try {
            parent.executeCodex(filePath, task);
        } catch (Exception e) {
            System.out.println("[codex] Friendly error: " + e.getMessage());
        }
    }
}
