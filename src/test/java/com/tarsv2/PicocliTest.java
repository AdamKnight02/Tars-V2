package com.tarsv2;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class PicocliTest {

    @Test
    void codexSubcommandParsesFileAndTaskFlags() {
        TarsCli cli = new TarsCli();
        CommandLine commandLine = new CommandLine(cli);

        commandLine.parseArgs("codex", "--file", "path.java", "--task", "task");

        CodexCommand codexCommand = (CodexCommand) commandLine.getSubcommands().get("codex").getCommand();
        assertEquals("path.java", codexCommand.filePath);
        assertEquals("task", codexCommand.task);
    }
}
