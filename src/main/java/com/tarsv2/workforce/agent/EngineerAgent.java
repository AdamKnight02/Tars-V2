package com.tarsv2.workforce.agent;

import com.tarsv2.approval.ProposalRegistry;
import com.tarsv2.codex.CodexOrchestrator;
import com.tarsv2.codex.PatchProposal;
import com.tarsv2.codex.PatchValidator;
import com.tarsv2.workforce.economics.CostEstimator;
import com.tarsv2.workforce.task.Task;
import com.tarsv2.workforce.task.TaskResult;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class EngineerAgent implements WorkforceAgent {

    private final CodexOrchestrator codexOrchestrator;
    private final PatchValidator patchValidator;
    private final ProposalRegistry proposalRegistry;
    private final CostEstimator costEstimator;

    public EngineerAgent(CodexOrchestrator codexOrchestrator,
                         PatchValidator patchValidator,
                         ProposalRegistry proposalRegistry,
                         CostEstimator costEstimator) {
        this.codexOrchestrator = Objects.requireNonNull(codexOrchestrator);
        this.patchValidator = Objects.requireNonNull(patchValidator);
        this.proposalRegistry = Objects.requireNonNull(proposalRegistry);
        this.costEstimator = Objects.requireNonNull(costEstimator);
    }

    @Override public String getName() { return "EngineerAgent"; }
    @Override public AgentRole getRole() { return AgentRole.ENGINEER; }
    @Override public String getDescription() { return "Generates deterministic patch proposals for human approval"; }
    @Override public CostEstimator.Estimate estimateCost(Task task) { return costEstimator.estimate(getRole(), task); }

    @Override
    public TaskResult execute(Task task) {
        String targetFile = extractTargetFile(task.description());
        try {
            String diff = codexOrchestrator.generateDiffOnly(targetFile, task.description());
            PatchValidator.ValidationResult validation = patchValidator.validate(diff);
            PatchProposal proposal = new PatchProposal(UUID.randomUUID(), diff, task.title(), targetFile, PatchProposal.Status.PENDING);
            proposalRegistry.submit(proposal, validation.referencedFiles(), validation.message());
            return new TaskResult(task.id(), true,
                    "Proposal submitted and awaiting approval: " + proposal.getId(), null, Instant.now(), 0, 0, "MiniMax-M2.5");
        } catch (IOException | IllegalArgumentException e) {
            return new TaskResult(task.id(), false, "", e.getMessage(), Instant.now(), 0, 0, "MiniMax-M2.5");
        }
    }

    @Override
    public boolean canHandle(Task task) {
        String lower = task.description().toLowerCase();
        return (lower.contains("code") || lower.contains("refactor") || lower.contains("java"))
                && lower.contains("src/main/java/");
    }

    private String extractTargetFile(String description) {
        return List.of(description.split("\\s+")).stream()
                .filter(s -> s.startsWith("src/main/java/") && s.endsWith(".java"))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No target file path found in task description"));
    }
}
