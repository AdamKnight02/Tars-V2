package com.tarsv2.codex;

import com.tarsv2.codex.instruction.PatchInstruction;
import com.tarsv2.codex.instruction.PatchInstructionParser;
import com.tarsv2.llm.ReasoningModelClient;
import com.tarsv2.model.ModelRequest;
import com.tarsv2.model.ModelResponse;
import com.tarsv2.model.TarsModel;
import com.tarsv2.model.config.ModelConfig;
import com.tarsv2.model.config.ModeBindings;
import com.tarsv2.model.router.DefaultModelRouter;
import com.tarsv2.model.router.ModelRouter;
import com.tarsv2.model.router.RoutingMode;
import com.tarsv2.tool.diff.DiffToolFacade;
import com.tarsv2.tool.git.GitToolFacade;
import com.tarsv2.tool.sandbox.SandboxToolFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

public final class CodexOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(CodexOrchestrator.class);

    private final ModelRouter modelRouter;
    private final SandboxToolFacade sandboxTool;
    private final DiffToolFacade diffTool;
    private final GitToolFacade gitTool;
    private final PatchInstructionParser parser;
    private final PatchValidator patchValidator;

    public CodexOrchestrator(ReasoningModelClient minimaxClient) {
        this(minimaxClient, Path.of("."));
    }

    public CodexOrchestrator(ReasoningModelClient minimaxClient, Path sandboxRoot) {
        this(buildRouter(minimaxClient), sandboxRoot, new DeterministicPatchBuilder(), new PatchValidator());
    }

    public CodexOrchestrator(ModelRouter modelRouter, Path sandboxRoot) {
        this(modelRouter, sandboxRoot, new DeterministicPatchBuilder(), new PatchValidator());
    }

    CodexOrchestrator(ModelRouter modelRouter,
                      Path sandboxRoot,
                      DeterministicPatchBuilder patchBuilder,
                      PatchValidator patchValidator) {
        this.modelRouter = Objects.requireNonNull(modelRouter);
        this.sandboxTool = new SandboxToolFacade(Objects.requireNonNull(sandboxRoot));
        this.diffTool = new DiffToolFacade(Objects.requireNonNull(patchBuilder));
        this.patchValidator = Objects.requireNonNull(patchValidator);
        this.gitTool = new GitToolFacade(patchValidator);
        this.parser = new PatchInstructionParser();
    }

    public String generateDiffOnly(String targetFilePath, String task) throws IOException {
        String safeTargetFilePath = requireTargetFilePath(targetFilePath);
        String safeTask = requireTask(task);
        String originalContent = sandboxTool.readFile(safeTargetFilePath);

        ModelResponse response = modelRouter.route(RoutingMode.CODEX,
                new ModelRequest("Deterministic patch planner", buildPrompt(safeTask, safeTargetFilePath, originalContent), true));
        if (response.status() != ModelResponse.Status.OK) {
            throw new IllegalStateException("CODEX model failed: " + response.message());
        }

        PatchInstruction instruction = parser.parse(response.content());
        PatchValidator.ValidationResult instructionValidation = patchValidator.validateInstruction(instruction);
        if (!instructionValidation.valid()) {
            throw new IllegalArgumentException("Patch instruction rejected: " + instructionValidation.message());
        }

        String diff = diffTool.buildUnifiedDiff(safeTargetFilePath, originalContent, instruction);
        PatchValidator.ValidationResult diffValidation = gitTool.validateForApply(diff);
        if (!diffValidation.valid()) {
            throw new IllegalArgumentException("Patch diff rejected: " + diffValidation.message());
        }

        log.info("Deterministic diff generated for {} ({} chars)", safeTargetFilePath, diff.length());
        return diff;
    }

    private static ModelRouter buildRouter(ReasoningModelClient minimaxClient) {
        TarsModel m25 = request -> {
            String raw = minimaxClient.generateDeterministicDiff(request.userPrompt());
            if (raw == null || raw.startsWith("[ERROR]")) {
                return ModelResponse.unavailable(raw == null ? "MiniMax unavailable" : raw);
            }
            return ModelResponse.ok(raw);
        };

        ModelConfig base = ModelConfig.fromEnvironment();
        ModelConfig config = new ModelConfig(new ModeBindings(base.bindings().chatModel(), "m2.5", base.bindings().researchModel()),
                base.profiles(),
                base.providers());

        return new DefaultModelRouter(config, Map.of("m2.5", m25));
    }

    private String buildPrompt(String instruction, String targetFilePath, String fileContent) {
        return "Return JSON only in this exact schema: "
                + "{\"file\":string,\"operation\":\"REPLACE|APPEND|REPLACE_HINT|INSERT_AFTER_HINT\",\"location\":string,\"content\":string}.\n"
                + "Target file path:\n" + requireTargetFilePath(targetFilePath)
                + "\n\nCurrent file contents:\n" + Objects.requireNonNull(fileContent)
                + "\n\nTask:\n" + instruction
                + "\nDo not return markdown and do not return a unified diff.";
    }

    private String requireTask(String task) {
        if (task == null || task.isBlank()) {
            throw new IllegalArgumentException("Task description is required for diff generation.");
        }
        return task;
    }

    private String requireTargetFilePath(String targetFilePath) {
        if (targetFilePath == null || targetFilePath.isBlank()) {
            throw new IllegalArgumentException("Target file path is required for deterministic diff generation.");
        }
        return targetFilePath;
    }
}
