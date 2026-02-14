package com.tarsv2.improvement;

import com.tarsv2.approval.ApprovalGate;
import com.tarsv2.approval.ChangeProposal;
import com.tarsv2.chunk.ChunkLearningEngine;
import com.tarsv2.llm.SingleModelOrchestrator;
import com.tarsv2.metrics.ObservationMetrics;
import com.tarsv2.personality.DialogueStyle;
import com.tarsv2.sandbox.JGitSandboxService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Supervised development loop that mirrors a Codex/Claude workflow while preserving
 * hard human-approval gates.
 *
 * <p>Pipeline: Observe → Analyze → Generate Tests → Propose Patch → Submit Proposal → Await Approval.</p>
 */
public final class SupervisedDevLoop {

    private static final Logger log = LoggerFactory.getLogger(SupervisedDevLoop.class);

    private static final int MAX_ITERATIONS = 3;
    private static final int DEFAULT_DIFF_LIMIT = 12000;

    private final ObservationMetrics metrics;
    private final ChunkLearningEngine chunkLearning;
    private final SingleModelOrchestrator orchestrator;
    private final JGitSandboxService jgitSandboxService;
    private final ApprovalGate approvalGate;
    private final DialogueStyle dialogue;
    private final Path repoRoot;
    private final int diffSizeLimit;

    public SupervisedDevLoop(
            ObservationMetrics metrics,
            ChunkLearningEngine chunkLearning,
            SingleModelOrchestrator orchestrator,
            JGitSandboxService jgitSandboxService,
            ApprovalGate approvalGate,
            DialogueStyle dialogue,
            Path repoRoot
    ) {
        this.metrics = Objects.requireNonNull(metrics);
        this.chunkLearning = Objects.requireNonNull(chunkLearning);
        this.orchestrator = Objects.requireNonNull(orchestrator);
        this.jgitSandboxService = Objects.requireNonNull(jgitSandboxService);
        this.approvalGate = Objects.requireNonNull(approvalGate);
        this.dialogue = Objects.requireNonNull(dialogue);
        this.repoRoot = Objects.requireNonNull(repoRoot);
        this.diffSizeLimit = Integer.parseInt(System.getenv().getOrDefault("TARS_DEV_DIFF_LIMIT", String.valueOf(DEFAULT_DIFF_LIMIT)));
    }

    /** Runs the full supervised loop with optional scope text. */
    public String run(String scope) {
        ImprovementObservation observation = observe(scope);
        AnalyzedImprovement analyzed = analyze(observation, scope);
        if (analyzed == null) {
            return null;
        }

        for (int i = 1; i <= MAX_ITERATIONS; i++) {
            GeneratedTest test = generateFailingTest(analyzed, i);
            if (test == null) {
                return null;
            }

            PatchProposal proposal = proposePatch(analyzed, test, i);
            if (proposal == null) {
                continue;
            }

            String proposalId = submitProposal(proposal, analyzed, scope);
            if (proposalId != null) {
                awaitApproval(proposalId);
            }
            return proposalId;
        }

        dialogue.say("Dev loop aborted: max iterations reached without valid proposal.", DialogueStyle.OutputMode.SYSTEM);
        return null;
    }

    public ImprovementObservation observe(String scope) {
        String observationText = "Metrics:\n" + metrics.getSummary() + "\n"
                + "Failure logs:\n" + recentFailureLogs() + "\n"
                + "Open TODOs:\n" + openTodos() + "\n"
                + "Low-performing strategies:\n" + lowStrategies();

        dialogue.say("Observed metrics, TODOs, failures, and strategy signals.", DialogueStyle.OutputMode.SYSTEM);
        return new ImprovementObservation(scope, observationText);
    }

    public AnalyzedImprovement analyze(ImprovementObservation observation, String scope) {
        String task = "Identify one concrete, bounded improvement for scope: " + scope
                + "\n\nObservation data:\n" + observation.observationText;

        SingleModelOrchestrator.OrchestratorResult result = orchestrator.process(task, DialogueStyle.OutputMode.SYSTEM);
        if (!result.passed() || isVague(result.output())) {
            dialogue.say("Analyze phase rejected by validator quality constraints.", DialogueStyle.OutputMode.SYSTEM);
            return null;
        }

        return new AnalyzedImprovement(result.output());
    }

    public GeneratedTest generateFailingTest(AnalyzedImprovement analyzed, int iteration) {
        String testPath = "src/test/java/com/tarsv2/improvement/GeneratedLoopFailingTest.java";
        String testContent = orchestrator.process(
                "Write one focused JUnit 5 failing test for this improvement: " + analyzed.improvement,
                DialogueStyle.OutputMode.SYSTEM
        ).output();

        try {
            jgitSandboxService.writeSandboxFile(testPath, testContent);
            dialogue.say("Generated pre-fix failing test in sandbox workspace.", DialogueStyle.OutputMode.SYSTEM);
        } catch (IOException e) {
            log.warn("Unable to write generated test", e);
            return null;
        }

        boolean failed = runTestAndExpectFailure();
        if (!failed) {
            dialogue.say("Generated test did not fail before fix. Rejecting proposal.", DialogueStyle.OutputMode.SYSTEM);
            return null;
        }

        return new GeneratedTest(testPath, testContent, iteration);
    }

    public PatchProposal proposePatch(AnalyzedImprovement analyzed, GeneratedTest test, int iteration) {
        SingleModelOrchestrator.OrchestratorResult patchResult = orchestrator.process(
                "Propose a minimal unified diff patch to satisfy this failing test only:\n"
                        + test.testContent + "\nImprovement context:\n" + analyzed.improvement,
                DialogueStyle.OutputMode.SYSTEM
        );


        String diff = patchResult.output();
        if (diff.length() > diffSizeLimit) {
            dialogue.say("Patch rejected: diff exceeds configured size limit.", DialogueStyle.OutputMode.SYSTEM);
            return null;
        }

        if (touchesForbiddenFiles(diff)) {
            dialogue.say("Patch rejected: forbidden file touched.", DialogueStyle.OutputMode.SYSTEM);
            return null;
        }

        return new PatchProposal(diff, iteration);
    }

    public String submitProposal(PatchProposal patch, AnalyzedImprovement analyzed, String scope) {
        ChangeProposal proposal = new ChangeProposal(
                "Supervised dev-loop improvement: " + scope,
                patch.diff,
                "Single-pass MiniMax patch, iteration=" + patch.iteration
        );

        String proposalId = approvalGate.submit(proposal);
        dialogue.say("Proposal submitted for human approval: " + proposalId, DialogueStyle.OutputMode.SYSTEM);
        return proposalId;
    }

    public boolean awaitApproval(String proposalId) {
        boolean approved = approvalGate.isApproved(proposalId);
        if (!approved) {
            dialogue.say("Awaiting human approval for proposal " + proposalId + ". No auto-merge performed.",
                    DialogueStyle.OutputMode.SYSTEM);
            return false;
        }

        dialogue.say("Proposal " + proposalId + " is approved and ready for supervised merge.",
                DialogueStyle.OutputMode.SYSTEM);
        return true;
    }

    private boolean runTestAndExpectFailure() {
        ProcessBuilder pb = new ProcessBuilder("mvn", "-q", "-Dtest=GeneratedLoopFailingTest", "test");
        pb.directory(repoRoot.toFile());
        pb.redirectErrorStream(true);

        try {
            Process process = pb.start();
            String output;
            try (BufferedReader reader = process.inputReader()) {
                output = reader.lines().collect(Collectors.joining("\n"));
            }
            int code = process.waitFor();
            log.info("Generated test run exit={} output={}", code, output);
            return code != 0;
        } catch (Exception e) {
            log.warn("Failed to run generated test (treating as failure)", e);
            return true;
        }
    }

    private String openTodos() {
        List<String> todos = new ArrayList<>();
        try {
            Files.walk(repoRoot.resolve("src/main/java"))
                    .filter(p -> p.toString().endsWith(".java"))
                    .limit(200)
                    .forEach(path -> {
                        try {
                            List<String> lines = Files.readAllLines(path);
                            for (int i = 0; i < lines.size(); i++) {
                                if (lines.get(i).contains("TODO")) {
                                    todos.add(path.getFileName() + ":" + (i + 1) + " " + lines.get(i).trim());
                                }
                            }
                        } catch (IOException ignored) {
                        }
                    });
        } catch (IOException e) {
            return "TODO scan unavailable: " + e.getMessage();
        }
        if (todos.isEmpty()) {
            return "No TODOs found.";
        }
        return todos.stream().limit(20).collect(Collectors.joining("\n"));
    }

    private String recentFailureLogs() {
        return metrics.getAll().stream()
                .filter(m -> !m.success())
                .sorted(Comparator.comparing(ObservationMetrics.TaskMetric::timestamp).reversed())
                .limit(10)
                .map(m -> m.taskName() + "|" + m.agentName() + " latency=" + m.latencyMs())
                .collect(Collectors.joining("\n"));
    }

    private String lowStrategies() {
        return chunkLearning.getWeights().getAll().entrySet().stream()
                .filter(e -> e.getValue() < 0.45)
                .sorted(Comparator.comparingDouble(e -> e.getValue()))
                .map(e -> e.getKey() + "=" + String.format("%.3f", e.getValue()))
                .collect(Collectors.joining(", "));
    }

    private boolean isVague(String text) {
        String normalized = text == null ? "" : text.toLowerCase();
        return normalized.isBlank() || normalized.contains("improve quality") || normalized.contains("refactor generally");
    }

    private boolean touchesForbiddenFiles(String diff) {
        String lower = diff.toLowerCase();
        return lower.contains("approvalgate") || lower.contains("changecontrol") || lower.contains("sudomanager");
    }

    /** Observation payload used by the loop. */
    public static final class ImprovementObservation {
        private final String scope;
        private final String observationText;

        public ImprovementObservation(String scope, String observationText) {
            this.scope = scope;
            this.observationText = observationText;
        }

        public String scope() { return scope; }
        public String observationText() { return observationText; }
    }

    /** Analyze phase output after single-pass validation. */
    public static final class AnalyzedImprovement {
        private final String improvement;
        public AnalyzedImprovement(String improvement) {
            this.improvement = improvement;
        }

        public String improvement() { return improvement; }
    }

    /** Generated failing test metadata. */
    public static final class GeneratedTest {
        private final String testPath;
        private final String testContent;
        private final int iteration;

        public GeneratedTest(String testPath, String testContent, int iteration) {
            this.testPath = testPath;
            this.testContent = testContent;
            this.iteration = iteration;
        }

        public String testPath() { return testPath; }
        public String testContent() { return testContent; }
        public int iteration() { return iteration; }
    }

    /** Patch proposal data before submission to ApprovalGate. */
    public static final class PatchProposal {
        private final String diff;
        private final int iteration;

        public PatchProposal(String diff, int iteration) {
            this.diff = diff;
            this.iteration = iteration;
        }

        public String diff() { return diff; }
        public int iteration() { return iteration; }
    }
}
