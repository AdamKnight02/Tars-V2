package com.tarsv2;

import com.tarsv2.agent.*;
import com.tarsv2.approval.ApprovalGate;
import com.tarsv2.approval.ProposalRegistry;
import com.tarsv2.chunk.ChunkLearningEngine;
import com.tarsv2.connector.*;
import com.tarsv2.context.ContextBudget;
import com.tarsv2.context.ContextSummarizer;
import com.tarsv2.environment.*;
import com.tarsv2.improvement.SelfImprovementLoop;
import com.tarsv2.improvement.SupervisedDevLoop;
import com.tarsv2.learning.LearningEngine;
import com.tarsv2.llm.*;
import com.tarsv2.codex.CodexOrchestrator;
import com.tarsv2.codex.PatchProposal;
import com.tarsv2.codex.PatchValidator;
import com.tarsv2.memory.MemorySystem;
import com.tarsv2.metrics.ObservationMetrics;
import com.tarsv2.model.*;
import com.tarsv2.model.config.ModelConfig;
import com.tarsv2.model.router.DefaultModelRouter;
import com.tarsv2.model.router.ModelRouter;
import com.tarsv2.workforce.WorkforceBootstrap;
import com.tarsv2.workforce.orchestration.WorkforceManager;
import com.tarsv2.openclaw.OpenClawClient;
import com.tarsv2.personality.*;
import com.tarsv2.podman.PodmanController;
import com.tarsv2.sandbox.GitStagingService;
import com.tarsv2.sandbox.JGitSandboxService;
import com.tarsv2.sandbox.SandboxGitService;
import com.tarsv2.sandbox.SandboxEnvironment;
import com.tarsv2.security.SecretManager;
import com.tarsv2.sudo.SudoManager;
import com.tarsv2.task.DepopScrapingTask;
import com.tarsv2.task.ExampleMockScrapeTask;
import com.tarsv2.web.ProposalWebServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.InvalidPathException;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

/**
 * TARS v2 — Main CLI entry point.
 *
 * <p>Boots the full agent system: personality engine, approval gate,
 * sandbox, Podman controller, dual-LLM orchestrator, agents,
 * self-improvement loop, OpenClaw execution model, environment system,
 * dev connectors, memory system, chunk learning, and sudo management.</p>
 *
 * <p>Example startup output:</p>
 * <pre>
 * ╔═══════════════════════════════════════════════════════╗
 * ║          TARS v2 — Autonomous Agent System            ║
 * ║   "Spinning up like a caffeinated dolphin" 🐬☕        ║
 * ╚═══════════════════════════════════════════════════════╝
 *
 * [TARS | mood=CURIOUS] Systems online. What are we working on today?
 * </pre>
 */
@Command(
        name = "tars",
        mixinStandardHelpOptions = true,
        version = "TARS v2 0.1.0-SNAPSHOT",
        description = "Human-approved, self-improving autonomous agent with personality."
)
public final class TarsCli implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(TarsCli.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private record ProposedChange(String summary, String diff, String rationale) {}

    @Option(names = {"-H", "--humor"}, description = "Humor level: MINIMAL, LOW, MEDIUM, HIGH, MAXIMUM",
            defaultValue = "MEDIUM")
    private HumorLevel humorLevel;

    @Option(names = {"--sandbox-dir"}, description = "Sandbox working directory",
            defaultValue = "/tmp/tars-sandbox")
    private String sandboxDir;

    @Option(names = {"--minimax-url"}, description = "MiniMax API endpoint")
    private String minimaxUrl = envOrDefault("TARS_MINIMAX_API_URL", "https://api.minimax.chat/v1/text/chatcompletion_v2");

    @Option(names = {"--minimax-model"}, description = "MiniMax model name")
    private String minimaxModel = envOrDefault("TARS_MINIMAX_MODEL", "MiniMax-M2.5");

    @Option(names = {"--web-port"}, description = "Web UI port for proposal review",
            defaultValue = "8080")
    private int webPort;

    @Option(names = {"--vscode-port"}, description = "VS Code connector port",
            defaultValue = "8089")
    private int vscodePort;

    @Option(names = {"--metrics-dir"}, description = "Directory for persistent metrics",
            defaultValue = "/tmp/tars-metrics")
    private String metricsDir;

    @Option(names = {"--file"}, description = "Target file path for Codex diff generation")
    private String targetFile;

    @Option(names = {"--task"}, description = "Task prompt for Codex diff generation")
    private String task;

    private WorkforceManager workforceManager;

    /**
     * Main entry point.
     *
     * @param args CLI arguments
     */
    public static void main(String[] args) {
        new CommandLine(new TarsCli()).execute(args);
    }


    private static String envOrDefault(String key, String fallback) {
        String value = System.getenv(key);
        return (value == null || value.isBlank()) ? fallback : value;
    }


    @Override
    public void run() {
        // ── Personality ──────────────────────────────────────────
        PersonalityProfile profile = new PersonalityProfile("TARS", humorLevel, EmotionState.NEUTRAL);
        DialogueStyle dialogue = new DialogueStyle(profile);

        printBanner(dialogue);
        profile.transitionEmotion(EmotionState.CURIOUS);
        dialogue.say("Systems online. What are we working on today?", DialogueStyle.OutputMode.CHAT);

        // ── Safety: Immutable Approval Gate ──────────────────────
        ApprovalGate approvalGate = new ApprovalGate();
        ProposalRegistry proposalRegistry = new ProposalRegistry();

        // ── Sandbox ──────────────────────────────────────────────
        SandboxEnvironment sandbox = new SandboxEnvironment(Path.of(sandboxDir));
        try {
            sandbox.initialize();
            dialogue.say("Sandbox initialized at " + sandboxDir, DialogueStyle.OutputMode.CHAT);
        } catch (Exception e) {
            log.error("Failed to initialize sandbox", e);
            dialogue.say("Sandbox init failed. That's... not great.", DialogueStyle.OutputMode.CHAT);
            return;
        }

        // ── Git Staging ──────────────────────────────────────────
        GitStagingService staging = new GitStagingService(sandbox, approvalGate);

        // ── JGit Sandbox ────────────────────────────────────────
        JGitSandboxService jgitService = new JGitSandboxService(sandbox, approvalGate, dialogue);
        try {
            jgitService.initialize();
        } catch (Exception e) {
            log.warn("JGit sandbox init failed (non-fatal): {}", e.getMessage());
            dialogue.say("JGit sandbox init skipped — file-based staging still active.", DialogueStyle.OutputMode.CHAT);
        }

        // ── Secrets ─────────────────────────────────────────────
        SecretManager secretManager = new SecretManager();
        var scraperTokenHandle = secretManager.registerFromEnv("scraper-token", "TARS_SCRAPER_TOKEN");
        var githubTokenHandle = secretManager.registerFromEnv("github-token", "TARS_GITHUB_TOKEN");

        // ── Podman ───────────────────────────────────────────────
        PodmanController podman = new PodmanController(dialogue);
        dialogue.say("Podman controller armed. Whitelisted images: " + podman.getAllowedImages().size(), DialogueStyle.OutputMode.CHAT);

        // ── MiniMax Primary Model Clients ─────────────────────
        ModelRegistry modelRegistry = ModelRegistry.fromEnvironment();
        MinimaxClient minimaxClient = new MinimaxClient(minimaxUrl, minimaxModel, modelRegistry.modelTimeout());
        ReasoningModelClient glmClient = new GlmClient();
        ModelRouter modelRouter = new DefaultModelRouter(ModelConfig.fromEnvironment(), Map.of(
                "m2.5", request -> ModelResponse.ok(minimaxClient.generateDeterministicDiff(request.userPrompt())),
                "minimax-chat", request -> ModelResponse.ok(minimaxClient.chat(request.userPrompt())),
                "glm-5", request -> ModelResponse.ok(glmClient.chat(request.userPrompt()))
        ));
        SingleModelOrchestrator orchestrator = new SingleModelOrchestrator(minimaxClient, dialogue);

        // ── Metrics & Learning ──────────────────────────────────
        ObservationMetrics metrics = new ObservationMetrics(
                Path.of(metricsDir, "tars-metrics.json"));
        LearningEngine learningEngine = new LearningEngine(metrics, dialogue);
        dialogue.say("Metrics engine online. " + metrics.getTotalCount() + " historical records loaded.", DialogueStyle.OutputMode.CHAT);

        // ── Self-Improvement Loops ───────────────────────────────
        SelfImprovementLoop improvementLoop = new SelfImprovementLoop(
                orchestrator, approvalGate, staging, dialogue, profile, metrics, learningEngine);

        // ── Agents ───────────────────────────────────────────────
        AgentRegistry registry = new AgentRegistry();
        registry.register(new ResumeAgent(podman, dialogue));
        registry.register(new DepopAgent(podman, dialogue));
        dialogue.say("Agents online: " + registry.getAll().size() + " registered.", DialogueStyle.OutputMode.CHAT);

        // ── Permission Model ────────────────────────────────────
        DevPermissionModel permissions = DevPermissionModel.fromEnvironment();
        dialogue.say("Dev capabilities: " + permissions.getGranted(), DialogueStyle.OutputMode.CHAT);

        // ── Audit Log ───────────────────────────────────────────
        DevAuditLog auditLog = new DevAuditLog(dialogue);

        // ── Environment Registry (ALE-style) ────────────────────
        EnvironmentRegistry envRegistry = new EnvironmentRegistry();
        SandboxDispatcher sandboxDispatcher = new SandboxDispatcher(sandbox, approvalGate);
        LLMDispatcher llmDispatcher = new LLMDispatcher();
        CompositeDispatcher compositeDispatcher = new CompositeDispatcher(llmDispatcher, sandboxDispatcher);
        EnvironmentFactory.registerAll(envRegistry, compositeDispatcher);
        dialogue.say("Environments: " + envRegistry.size() + " registered (ALE-pinned).", DialogueStyle.OutputMode.CHAT);

        // ── OpenClaw Execution Layer ────────────────────────────
        com.tarsv2.workforce.persistence.DatabaseManager openClawDb = new com.tarsv2.workforce.persistence.DatabaseManager();
        openClawDb.initialize();
        com.tarsv2.workforce.economics.EconomicEngine openClawEconomicEngine = new com.tarsv2.workforce.economics.EconomicEngine(
                new com.tarsv2.workforce.economics.CostEstimator(),
                new com.tarsv2.workforce.economics.ProfitabilityGate(),
                new com.tarsv2.workforce.economics.ModelUsageTracker(),
                new com.tarsv2.workforce.persistence.h2.H2LedgerRepository(openClawDb.getDataSource())
        );
        OpenClawClient openClaw = new OpenClawClient(envRegistry, permissions, dialogue, openClawEconomicEngine);
        dialogue.say("OpenClaw execution layer online. TARS produces intents, OpenClaw executes.", DialogueStyle.OutputMode.CHAT);

        // ── Memory System ───────────────────────────────────────
        MemorySystem memorySystem = new MemorySystem(dialogue);
        dialogue.say("Memory system online. Active memories: " + memorySystem.getActiveCount(), DialogueStyle.OutputMode.CHAT);

        // ── Chunk Learning Engine ───────────────────────────────
        ChunkLearningEngine chunkLearning = new ChunkLearningEngine(
                dialogue, Path.of(metricsDir, "trajectories.jsonl"));
        dialogue.say("Chunk learning engine online. Strategy weights initialized.", DialogueStyle.OutputMode.CHAT);
        SupervisedDevLoop supervisedDevLoop = new SupervisedDevLoop(
                metrics, chunkLearning, orchestrator, jgitService, approvalGate, dialogue, Path.of("."));


        // ── Context Discipline ──────────────────────────────────
        ContextBudget contextBudget = new ContextBudget(8000);
        ContextSummarizer contextSummarizer = new ContextSummarizer();
        ChatOrchestrator chatOrchestrator = new ChatOrchestrator(
                minimaxClient, contextSummarizer, contextBudget);
        ResearchOrchestrator researchOrchestrator = new ResearchOrchestrator(glmClient);
        CodexOrchestrator codexOrchestrator = new CodexOrchestrator(minimaxClient);
        PatchValidator patchValidator = new PatchValidator();
        SandboxGitService sandboxGitService = new SandboxGitService(Path.of("."));

        // ── Sudo Manager ────────────────────────────────────────
        String sudoPass = System.getenv("TARS_SUDO_PASS");
        SudoManager sudoManager = new SudoManager(dialogue,
                sudoPass != null ? sudoPass : "tars-sudo-default");
        dialogue.say("Sudo manager armed. Kill switch ready.", DialogueStyle.OutputMode.CHAT);

        // ── VS Code Connector ───────────────────────────────────
        VSCodeConnector vscodeConnector = new VSCodeConnector(
                sandbox.getRoot(), approvalGate, dialogue, permissions, auditLog, vscodePort);
        try {
            vscodeConnector.start();
        } catch (Exception e) {
            log.warn("VS Code connector failed to start on port {}: {}", vscodePort, e.getMessage());
            dialogue.say("VS Code connector unavailable — API-only mode.", DialogueStyle.OutputMode.CHAT);
        }

        // ── Web UI ──────────────────────────────────────────────
        ProposalWebServer webServer = new ProposalWebServer(proposalRegistry, dialogue, webPort);
        try {
            webServer.start();
        } catch (Exception e) {
            log.warn("Web UI failed to start on port {}: {}", webPort, e.getMessage());
            dialogue.say("Web UI unavailable — CLI approval still works.", DialogueStyle.OutputMode.CHAT);
        }

        if (targetFile != null || task != null) {
            if (targetFile == null || targetFile.isBlank()) {
                System.out.println("[codex] Error: --file is required when using --task.");
                return;
            }
            if (task == null || task.isBlank()) {
                System.out.println("[codex] Error: --task is required when using --file.");
                return;
            }
            runCodexDiffMode(codexOrchestrator);
            return;
        }

        // ── Interactive Loop ─────────────────────────────────────
        dialogue.say("Entering interactive mode. Type 'help' for commands, 'quit' to exit.", DialogueStyle.OutputMode.CHAT);
        runInteractiveLoop(dialogue, profile, approvalGate, registry,
                improvementLoop, supervisedDevLoop, podman, staging, metrics, learningEngine,
                scraperTokenHandle, webServer, vscodeConnector,
                auditLog, memorySystem, chunkLearning, sudoManager,
                envRegistry, openClaw, contextBudget, chatOrchestrator, researchOrchestrator,
                codexOrchestrator, patchValidator, sandboxGitService, proposalRegistry, modelRouter);
    }

    /**
     * Interactive command loop with all subsystem integration.
     */
    private void runInteractiveLoop(
            DialogueStyle dialogue,
            PersonalityProfile profile,
            ApprovalGate approvalGate,
            AgentRegistry registry,
            SelfImprovementLoop improvementLoop,
            SupervisedDevLoop supervisedDevLoop,
            PodmanController podman,
            GitStagingService staging,
            ObservationMetrics metrics,
            LearningEngine learningEngine,
            SecretManager.SecretHandle scraperTokenHandle,
            ProposalWebServer webServer,
            VSCodeConnector vscodeConnector,
            DevAuditLog auditLog,
            MemorySystem memorySystem,
            ChunkLearningEngine chunkLearning,
            SudoManager sudoManager,
            EnvironmentRegistry envRegistry,
            OpenClawClient openClaw,
            ContextBudget contextBudget,
            ChatOrchestrator chatOrchestrator,
            ResearchOrchestrator researchOrchestrator,
            CodexOrchestrator codexOrchestrator,
            PatchValidator patchValidator,
            SandboxGitService sandboxGitService,
            ProposalRegistry proposalRegistry,
            ModelRouter modelRouter
    ) {
        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.print("\n[tars]> ");
            if (!scanner.hasNextLine()) break;
            String input = scanner.nextLine().trim();

            // ── Sudo detection ──────────────────────────────────
            if (sudoManager.isSudoRequest(input)) {
                dialogue.say("[SUDO] Elevated command detected. Enter sudo password:", DialogueStyle.OutputMode.CHAT);
                System.out.print("[sudo]> ");
                if (!scanner.hasNextLine()) break;
                String password = scanner.nextLine().trim();
                var token = sudoManager.authenticate(password,
                        Set.of("large-refactor", "multi-repo", "increased-retries"));
                if (token.isPresent()) {
                    String cmd = sudoManager.extractCommand(input);
                    dialogue.say("[SUDO] Executing elevated: " + cmd, DialogueStyle.OutputMode.CHAT);
                    // Route to normal command handling with sudo context
                    input = cmd;
                } else {
                    continue;
                }
            }

            String lowered = input.toLowerCase();
            if (lowered.startsWith("dev-loop ")) {
                String scope = input.substring("dev-loop ".length()).trim();
                String proposalId = supervisedDevLoop.run(scope.isEmpty() ? "general" : scope);
                if (proposalId != null) {
                    dialogue.say("Dev-loop proposal " + proposalId + " submitted.", DialogueStyle.OutputMode.SYSTEM);
                }
                continue;
            }

            switch (lowered) {
                case "quit", "exit" -> {
                    dialogue.say("Shutting down. It's been real.", DialogueStyle.OutputMode.CHAT);
                    if (webServer != null) webServer.stop();
                    if (vscodeConnector != null) vscodeConnector.stop();
                    if (workforceManager != null) workforceManager.shutdown();
                    return;
                }
                case "help" -> printHelp(dialogue);
                case "status" -> {
                    dialogue.say("Profile: " + profile, DialogueStyle.OutputMode.CHAT);
                    dialogue.say("Pending proposals: " + proposalRegistry.getPending().size(), DialogueStyle.OutputMode.CHAT);
                    dialogue.say("Registered agents: " + registry.getAll().size(), DialogueStyle.OutputMode.CHAT);
                    dialogue.say("Metrics recorded: " + metrics.getTotalCount(), DialogueStyle.OutputMode.CHAT);
                    dialogue.say("Environments: " + envRegistry.size(), DialogueStyle.OutputMode.CHAT);
                    dialogue.say("Active memories: " + memorySystem.getActiveCount(), DialogueStyle.OutputMode.CHAT);
                    dialogue.say("Sudo sessions: " + sudoManager.getActiveCount(), DialogueStyle.OutputMode.CHAT);
                    dialogue.say("Context budget: " + contextBudget, DialogueStyle.OutputMode.CHAT);
                }
                case "agents" -> registry.getAll().forEach(a ->
                        dialogue.say("  " + a.getName() + " — " + a.getDescription(), DialogueStyle.OutputMode.CHAT));
                case "proposals" -> {
                    var pending = proposalRegistry.getPending();
                    if (pending.isEmpty()) {
                        dialogue.say("No pending proposals. I'm behaving.", DialogueStyle.OutputMode.CHAT);
                    } else {
                        pending.forEach(p -> dialogue.say("  " + p, DialogueStyle.OutputMode.CHAT));
                    }
                }
                case "metrics" -> {
                    dialogue.say(metrics.getSummary(), DialogueStyle.OutputMode.CHAT);
                }
                case "environments" -> {
                    envRegistry.getAll().forEach(env ->
                            dialogue.say("  " + env, DialogueStyle.OutputMode.CHAT));
                }
                case "memory" -> {
                    dialogue.say(memorySystem.getSummary(), DialogueStyle.OutputMode.CHAT);
                }
                case "memory-decay" -> {
                    dialogue.say("Running memory decay cycle...", DialogueStyle.OutputMode.CHAT);
                    var report = memorySystem.runDecayCycle();
                    dialogue.say("Decay complete: " + report.toSummary(), DialogueStyle.OutputMode.CHAT);
                }
                case "chunks" -> {
                    dialogue.say(chunkLearning.getSummary(), DialogueStyle.OutputMode.CHAT);
                }
                case "audit" -> {
                    dialogue.say(auditLog.getSummary(), DialogueStyle.OutputMode.CHAT);
                }
                case "kill-switch" -> {
                    dialogue.say("[SECURITY] Activating kill switch...", DialogueStyle.OutputMode.CHAT);
                    sudoManager.killSwitch();
                }
                case "learn" -> {
                    dialogue.say("Running learning analysis...", DialogueStyle.OutputMode.CHAT);
                    var recommendations = learningEngine.analyze();
                    if (recommendations.isEmpty()) {
                        dialogue.say("No improvement recommendations at this time.", DialogueStyle.OutputMode.CHAT);
                    } else {
                        recommendations.forEach(r -> dialogue.say(
                                "  [" + r.type() + "] " + r.agentName() + ": " + r.description(), DialogueStyle.OutputMode.CHAT));
                    }
                }
                case "depop-scrape" -> {
                    dialogue.say("Running Depop scraping task...", DialogueStyle.OutputMode.CHAT);
                    var task = new DepopScrapingTask(podman, dialogue, scraperTokenHandle);
                    var output = task.run("vintage denim", 20);
                    dialogue.say("Depop scrape result: " + output.status(), DialogueStyle.OutputMode.CHAT);
                    if (output.status() == com.tarsv2.schema.AgentTaskOutput.TaskStatus.SUCCESS) {
                        dialogue.say("Output: " + output.toJson(), DialogueStyle.OutputMode.CHAT);
                    }
                }
                case "mock-scrape" -> {
                    dialogue.say("Running example mock scrape task...", DialogueStyle.OutputMode.CHAT);
                    try {
                        var task = new ExampleMockScrapeTask(podman, dialogue);
                        var result = task.run();
                        dialogue.say("Scrape result: " + (result.isSuccess() ? "SUCCESS" : "FAILED"), DialogueStyle.OutputMode.CHAT);
                    } catch (Exception e) {
                        dialogue.say("Mock scrape failed: " + e.getMessage(), DialogueStyle.OutputMode.CHAT);
                    }
                }
                case "improve" -> {
                    dialogue.say("Triggering self-improvement cycle...", DialogueStyle.OutputMode.CHAT);
                    String proposalId = improvementLoop.runCycle("general performance");
                    if (proposalId != null) {
                        dialogue.say("Proposal " + proposalId + " awaiting your approval.", DialogueStyle.OutputMode.CHAT);
                    }
                }
                case "dev-loop" -> {
                    String proposalId = supervisedDevLoop.run("general");
                    if (proposalId != null) {
                        dialogue.say("Dev-loop proposal " + proposalId + " submitted.", DialogueStyle.OutputMode.SYSTEM);
                    }
                }
                case "dev-analyze" -> {
                    var obs = supervisedDevLoop.observe("general");
                    var result = supervisedDevLoop.analyze(obs, "general");
                    dialogue.say(result == null ? "No valid bounded improvement found." : result.improvement(),
                            DialogueStyle.OutputMode.SYSTEM);
                }
                case "dev-generate-tests" -> {
                    var obs = supervisedDevLoop.observe("general");
                    var result = supervisedDevLoop.analyze(obs, "general");
                    if (result != null) {
                        supervisedDevLoop.generateFailingTest(result, 1);
                    }
                }
                case "dev-propose-fix" -> {
                    var obs = supervisedDevLoop.observe("general");
                    var result = supervisedDevLoop.analyze(obs, "general");
                    if (result != null) {
                        var test = supervisedDevLoop.generateFailingTest(result, 1);
                        if (test != null) {
                            var patch = supervisedDevLoop.proposePatch(result, test, 1);
                            if (patch != null) {
                                supervisedDevLoop.submitProposal(patch, result, "general");
                            }
                        }
                    }
                }
                case "workforce" -> {
                    if (workforceManager == null) {
                        dialogue.say("Initializing Workforce OS...", DialogueStyle.OutputMode.SYSTEM);
                        try {
                            workforceManager = WorkforceBootstrap.initialize(
                                    modelRouter, codexOrchestrator, researchOrchestrator, chatOrchestrator,
                                    approvalGate, proposalRegistry, patchValidator, openClaw
                            );
                            workforceManager.start();
                            dialogue.say("Workforce OS online. Use 'goal <description>' to submit work.", DialogueStyle.OutputMode.SYSTEM);
                        } catch (Exception e) {
                            dialogue.say("Workforce init failed: " + e.getMessage(), DialogueStyle.OutputMode.SYSTEM);
                            log.error("Workforce bootstrap failed", e);
                        }
                    } else {
                        dialogue.say(workforceManager.getStatus().toString(), DialogueStyle.OutputMode.SYSTEM);
                    }
                }
                case "economics" -> {
                    if (workforceManager == null) {
                        dialogue.say("Workforce not started. Run 'workforce' first.", DialogueStyle.OutputMode.SYSTEM);
                    } else {
                        dialogue.say(workforceManager.getEconomicSnapshot().toString(), DialogueStyle.OutputMode.SYSTEM);
                    }
                }
                case "scan" -> {
                    if (workforceManager == null) {
                        dialogue.say("Workforce not started. Run 'workforce' first.", DialogueStyle.OutputMode.SYSTEM);
                    } else {
                        String domain = input.length() > 5 ? input.substring(5).trim() : "freelance software development";
                        var opportunities = workforceManager.scanOpportunities(domain);
                        dialogue.say("Found " + opportunities.size() + " opportunities.", DialogueStyle.OutputMode.SYSTEM);
                        opportunities.forEach(o -> dialogue.say("  " + o.title() + " — $" + o.estimatedValue(), DialogueStyle.OutputMode.SYSTEM));
                    }
                }
                default -> {
                    if (lowered.startsWith("goal ")) {
                        if (workforceManager == null) {
                            dialogue.say("Workforce not started. Run 'workforce' first.", DialogueStyle.OutputMode.SYSTEM);
                        } else {
                            String goalText = input.substring("goal ".length()).trim();
                            dialogue.say("Submitting goal: " + goalText, DialogueStyle.OutputMode.SYSTEM);
                            workforceManager.submitGoal(goalText);
                            dialogue.say("Goal decomposed and tasks queued.", DialogueStyle.OutputMode.SYSTEM);
                        }
                    } else if (input.startsWith("approve ")) {
                        String id = input.substring(8).trim();
                        java.util.UUID uuid;
                        try {
                            uuid = java.util.UUID.fromString(id);
                        } catch (IllegalArgumentException ex) {
                            dialogue.say("Invalid proposal ID format.", DialogueStyle.OutputMode.CHAT);
                            continue;
                        }
                        boolean ok = proposalRegistry.approve(uuid);
                        if (!ok) {
                            dialogue.say("Not found or already decided.", DialogueStyle.OutputMode.CHAT);
                            continue;
                        }
                        var proposal = proposalRegistry.get(uuid).orElse(null);
                        if (proposal == null) {
                            dialogue.say("Proposal missing.", DialogueStyle.OutputMode.CHAT);
                            continue;
                        }
                        boolean patchApplied = sandboxGitService.applyPatch(proposal.getDiff());
                        if (!patchApplied) {
                            proposalRegistry.updateStatus(uuid, PatchProposal.Status.FAILED, "git apply failed");
                            dialogue.say("Proposal " + id + " failed during patch apply.", DialogueStyle.OutputMode.CHAT);
                            continue;
                        }
                        boolean testsPassed = sandboxGitService.runTests();
                        if (!testsPassed) {
                            proposalRegistry.updateStatus(uuid, PatchProposal.Status.FAILED, "mvn test failed; rollback executed");
                            dialogue.say("Proposal " + id + " failed tests and was rolled back.", DialogueStyle.OutputMode.CHAT);
                            continue;
                        }
                        sandboxGitService.commit("Apply approved patch proposal " + id);
                        proposalRegistry.updateStatus(uuid, PatchProposal.Status.APPLIED, "Patch applied and committed");
                        dialogue.say("Proposal " + id + " applied.", DialogueStyle.OutputMode.CHAT);
                    } else if (input.startsWith("reject ")) {
                        String id = input.substring(7).trim();
                        java.util.UUID uuid;
                        try {
                            uuid = java.util.UUID.fromString(id);
                        } catch (IllegalArgumentException ex) {
                            dialogue.say("Invalid proposal ID format.", DialogueStyle.OutputMode.CHAT);
                            continue;
                        }
                        boolean ok = proposalRegistry.reject(uuid);
                        dialogue.say(ok ? "Proposal " + id + " rejected. Discarding." : "Not found or already decided.", DialogueStyle.OutputMode.CHAT);
                        if (ok) memorySystem.onRejection(id);
                    } else if (input.startsWith("run ")) {
                        String agentName = input.substring(4).trim();
                        registry.get(agentName).ifPresentOrElse(
                                agent -> {
                                    try {
                                        dialogue.say("Executing " + agent.getName() + "...", DialogueStyle.OutputMode.CHAT);
                                        AgentResult result = agent.execute("default-input");
                                        dialogue.say("Result: " + result.summary(), DialogueStyle.OutputMode.CHAT);
                                    } catch (AgentExecutionException e) {
                                        dialogue.say("Agent failed: " + e.getMessage(), DialogueStyle.OutputMode.CHAT);
                                    }
                                },
                                () -> dialogue.say("Unknown agent: " + agentName + ". Try 'agents' to see available agents.", DialogueStyle.OutputMode.CHAT)
                        );
                    } else if (input.startsWith("codex ")) {
                        String topic = input.substring("codex ".length()).trim();
                        if (targetFile == null || targetFile.isBlank()) {
                            System.out.println("[codex] Friendly error: missing --file argument. Run with --file <path> --task \"<task>\".");
                            continue;
                        }
                        try {
                            String diff = codexOrchestrator.generateDiffOnly(validatedCodexPath(targetFile), validatedTask(topic));
                            log.info("Codex raw diff:\n{}", diff);
                            PatchValidator.ValidationResult validationResult = patchValidator.validate(diff);
                            log.info("Codex validation result: {}", validationResult.message());
                            PatchProposal proposal = new PatchProposal(
                                    java.util.UUID.randomUUID(),
                                    diff,
                                    topic,
                                    targetFile,
                                    PatchProposal.Status.PENDING
                            );
                            proposalRegistry.submit(proposal, validationResult.referencedFiles(), validationResult.message());
                            if (!validationResult.valid()) {
                                proposalRegistry.updateStatus(proposal.getId(), PatchProposal.Status.FAILED, validationResult.message());
                                System.out.println("[codex] Proposal rejected by validator: " + validationResult.message());
                                continue;
                            }
                            System.out.println(diff);
                            System.out.println("[codex] Proposal created: " + proposal.getId());
                        } catch (IllegalArgumentException e) {
                            System.out.println("[codex] Friendly error: " + e.getMessage());
                        } catch (IOException e) {
                            System.out.println("[codex] Sorry, something went wrong while generating the diff.");
                            log.error("Codex diff generation failed", e);
                        }
                    } else if (input.startsWith("propose ")) {
                        String topic = input.substring("propose ".length()).trim();
                        if (topic.startsWith("\"") && topic.endsWith("\"") && topic.length() >= 2) {
                            topic = topic.substring(1, topic.length() - 1);
                        }
                        String researchJson = researchOrchestrator.research(topic);
                        var proposedChange = parseProposalStructure(researchJson);
                        if (proposedChange.isEmpty()) {
                            dialogue.say("Proposal generation failed: invalid structure", DialogueStyle.OutputMode.CHAT);
                            continue;
                        }

                        var proposal = new PatchProposal(
                                java.util.UUID.randomUUID(),
                                proposedChange.get().diff(),
                                proposedChange.get().summary(),
                                "src/main/java/com/tarsv2",
                                PatchProposal.Status.PENDING
                        );
                        var validation = patchValidator.validate(proposal.getDiff());
                        proposalRegistry.submit(proposal, validation.referencedFiles(), validation.message());
                        dialogue.say("Proposal created with ID: " + proposal.getId() + ". Awaiting approval.", DialogueStyle.OutputMode.CHAT);
                    } else if (input.startsWith("research ")) {
                        try {
                            String topic = validatedResearchTopic(input.substring("research ".length()));
                            String researchJson = researchOrchestrator.research(topic);
                            System.out.println(researchJson);
                        } catch (IllegalArgumentException ex) {
                            System.out.println("[research] Friendly error: " + ex.getMessage());
                        }
                    } else if (!input.isEmpty()) {
                        String reply = chatOrchestrator.chat(input);
                        System.out.println("[TARS] " + reply);
                    }
                }
            }
        }
    }


    private void runCodexDiffMode(CodexOrchestrator codexOrchestrator) {
        try {
            String safeFile = validatedCodexPath(targetFile);
            String safeTask = validatedTask(task);
            String diff = codexOrchestrator.generateDiffOnly(safeFile, safeTask);
            System.out.println(diff);
        } catch (IllegalArgumentException e) {
            System.out.println("[codex] Friendly error: " + e.getMessage());
        } catch (Exception e) {
            System.out.println("[codex] Sorry, something went wrong while generating the diff.");
            log.error("Codex diff mode failed", e);
        }
    }


    private String validatedResearchTopic(String rawInput) {
        String trimmed = rawInput == null ? "" : rawInput.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("missing research query. Run with research \"<topic>\".");
        }
        boolean starts = trimmed.startsWith("\"");
        boolean ends = trimmed.endsWith("\"");
        if (starts ^ ends) {
            throw new IllegalArgumentException("malformed quoted query: ensure quotes are properly closed.");
        }
        String topic = (starts && trimmed.length() >= 2) ? trimmed.substring(1, trimmed.length() - 1) : trimmed;
        topic = topic.trim();
        if (topic.isEmpty()) {
            throw new IllegalArgumentException("research query must not be empty.");
        }
        return topic;
    }

    private String validatedCodexPath(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            throw new IllegalArgumentException("missing --file argument. Run with --file <path> --task \"<task>\".");
        }
        try {
            var normalized = Paths.get(filePath).normalize();
            if (normalized.isAbsolute() || normalized.startsWith("..")) {
                throw new IllegalArgumentException("--file must be a relative path inside the repository.");
            }
            return normalized.toString().replace("\\", "/");
        } catch (InvalidPathException ex) {
            throw new IllegalArgumentException("--file value is not a valid path.");
        }
    }

    private String validatedTask(String taskValue) {
        if (taskValue == null || taskValue.isBlank()) {
            throw new IllegalArgumentException("missing --task argument. Run with --task \"<task>\".");
        }
        return taskValue.trim();
    }

    private void printHelp(DialogueStyle dialogue) {
        dialogue.say("Available commands:", DialogueStyle.OutputMode.CHAT);
        dialogue.say("  help              — Show this help", DialogueStyle.OutputMode.CHAT);
        dialogue.say("  status            — System status (all subsystems)", DialogueStyle.OutputMode.CHAT);
        dialogue.say("  agents            — List registered agents", DialogueStyle.OutputMode.CHAT);
        dialogue.say("  run <AgentName>   — Execute an agent", DialogueStyle.OutputMode.CHAT);
        dialogue.say("  mock-scrape       — Run the example Podman scrape task", DialogueStyle.OutputMode.CHAT);
        dialogue.say("  depop-scrape      — Run the Depop trend scraping task", DialogueStyle.OutputMode.CHAT);
        dialogue.say("  metrics           — View observation metrics", DialogueStyle.OutputMode.CHAT);
        dialogue.say("  learn             — Run learning engine analysis", DialogueStyle.OutputMode.CHAT);
        dialogue.say("  improve           — Trigger self-improvement cycle", DialogueStyle.OutputMode.CHAT);
        dialogue.say("  dev-loop [scope]  — Run supervised dev loop", DialogueStyle.OutputMode.SYSTEM);
        dialogue.say("  dev-analyze       — Run observe/analyze phases", DialogueStyle.OutputMode.SYSTEM);
        dialogue.say("  dev-generate-tests — Generate a failing test proposal", DialogueStyle.OutputMode.SYSTEM);
        dialogue.say("  dev-propose-fix   — Build a fix proposal from generated test", DialogueStyle.OutputMode.SYSTEM);
        dialogue.say("  propose <topic>   — Create a pending proposal from structured Actor output", DialogueStyle.OutputMode.SYSTEM);
        dialogue.say("  research <topic>  — Return deterministic JSON research proposal", DialogueStyle.OutputMode.SYSTEM);
        dialogue.say("  codex <topic>     — Generate diff-only codex proposal", DialogueStyle.OutputMode.SYSTEM);
        dialogue.say("  workforce         — Initialize/show Workforce OS status", DialogueStyle.OutputMode.SYSTEM);
        dialogue.say("  goal <text>       — Submit a workforce goal for decomposition", DialogueStyle.OutputMode.SYSTEM);
        dialogue.say("  economics         — Show workforce economics snapshot", DialogueStyle.OutputMode.SYSTEM);
        dialogue.say("  scan <domain>     — Scan for revenue opportunities", DialogueStyle.OutputMode.SYSTEM);
        dialogue.say("  proposals         — List pending change proposals", DialogueStyle.OutputMode.CHAT);
        dialogue.say("  approve <id>      — Approve a proposal", DialogueStyle.OutputMode.CHAT);
        dialogue.say("  reject <id>       — Reject a proposal", DialogueStyle.OutputMode.CHAT);
        dialogue.say("  environments      — List execution environments", DialogueStyle.OutputMode.CHAT);
        dialogue.say("  memory            — Memory system summary", DialogueStyle.OutputMode.CHAT);
        dialogue.say("  memory-decay      — Run memory decay cycle", DialogueStyle.OutputMode.CHAT);
        dialogue.say("  chunks            — Chunk learning summary", DialogueStyle.OutputMode.CHAT);
        dialogue.say("  audit             — Dev connector audit log", DialogueStyle.OutputMode.CHAT);
        dialogue.say("  kill-switch       — Revoke all sudo sessions", DialogueStyle.OutputMode.CHAT);
        dialogue.say("  sudo:<command>    — Execute with elevated privileges", DialogueStyle.OutputMode.CHAT);
        dialogue.say("  quit              — Shut down TARS", DialogueStyle.OutputMode.CHAT);
    }


    private java.util.Optional<ProposedChange> parseProposalStructure(String researchJson) {
        try {
            JsonNode root = MAPPER.readTree(researchJson);
            if (!root.isObject()) return java.util.Optional.empty();

            JsonNode summary = root.get("summary");
            JsonNode diff = root.get("diff");
            JsonNode rollback = root.get("rollback_instructions");

            if (summary == null || !summary.isTextual() || summary.asText().isBlank()) return java.util.Optional.empty();
            if (diff == null || !diff.isTextual() || diff.asText().isBlank()) return java.util.Optional.empty();
            if (rollback == null || !rollback.isTextual() || rollback.asText().isBlank()) return java.util.Optional.empty();

            return java.util.Optional.of(new ProposedChange(summary.asText(), diff.asText(), rollback.asText()));
        } catch (Exception e) {
            return java.util.Optional.empty();
        }
    }

    private void printBanner(DialogueStyle dialogue) {
        System.out.println();
        System.out.println("╔═══════════════════════════════════════════════════════╗");
        System.out.println("║          TARS v2 — Autonomous Agent System            ║");
        System.out.println("║   \"Spinning up like a caffeinated dolphin\" 🐬☕        ║");
        System.out.println("╚═══════════════════════════════════════════════════════╝");
        System.out.println();
    }
}
