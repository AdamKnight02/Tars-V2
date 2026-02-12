package com.tarsv2;

import com.tarsv2.agent.*;
import com.tarsv2.approval.ApprovalGate;
import com.tarsv2.chunk.ChunkLearningEngine;
import com.tarsv2.connector.*;
import com.tarsv2.context.ContextBudget;
import com.tarsv2.context.ContextSummarizer;
import com.tarsv2.environment.*;
import com.tarsv2.improvement.SelfImprovementLoop;
import com.tarsv2.improvement.SupervisedDevLoop;
import com.tarsv2.learning.LearningEngine;
import com.tarsv2.llm.*;
import com.tarsv2.memory.MemorySystem;
import com.tarsv2.metrics.ObservationMetrics;
import com.tarsv2.openclaw.OpenClawClient;
import com.tarsv2.personality.*;
import com.tarsv2.podman.PodmanController;
import com.tarsv2.sandbox.GitStagingService;
import com.tarsv2.sandbox.JGitSandboxService;
import com.tarsv2.sandbox.SandboxEnvironment;
import com.tarsv2.security.AuthenticationConfig;
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

import java.nio.file.Path;
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

    @Option(names = {"-H", "--humor"}, description = "Humor level: MINIMAL, LOW, MEDIUM, HIGH, MAXIMUM",
            defaultValue = "MEDIUM")
    private HumorLevel humorLevel;

    @Option(names = {"--sandbox-dir"}, description = "Sandbox working directory",
            defaultValue = "/tmp/tars-sandbox")
    private String sandboxDir;

    @Option(names = {"--ollama-url"}, description = "Ollama base URL")
    private String ollamaUrl = envOrDefault("TARS_OLLAMA_URL", "http://localhost:11434");

    @Option(names = {"--actor-model"}, description = "Actor model name")
    private String actorModel = envOrDefault("TARS_ACTOR_MODEL", "llama3:8b");

    @Option(names = {"--reflector-model"}, description = "Reflector model name")
    private String reflectorModel = envOrDefault("TARS_REFLECTOR_MODEL", "qwen2.5:14b");

    @Option(names = {"--chat-quality-threshold"}, description = "Chat reflector quality threshold (0.0-1.0)")
    private Double chatQualityThreshold;

    @Option(names = {"--web-port"}, description = "Web UI port for proposal review",
            defaultValue = "8080")
    private int webPort;

    @Option(names = {"--vscode-port"}, description = "VS Code connector port",
            defaultValue = "8089")
    private int vscodePort;

    @Option(names = {"--metrics-dir"}, description = "Directory for persistent metrics",
            defaultValue = "/tmp/tars-metrics")
    private String metricsDir;

    /**
     * Main entry point.
     *
     * @param args CLI arguments
     */
    public static void main(String[] args) {
        int exitCode = new CommandLine(new TarsCli()).execute(args);
        System.exit(exitCode);
    }


    private static String envOrDefault(String key, String fallback) {
        String value = System.getenv(key);
        return (value == null || value.isBlank()) ? fallback : value;
    }

    private static String buildOllamaGenerateEndpoint(String baseUrl) {
        String trimmed = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return trimmed + "/api/generate";
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
        var actorKeyHandle = secretManager.registerFromEnv("actor-api-key", "TARS_ACTOR_API_KEY");
        var reflectorKeyHandle = secretManager.registerFromEnv("reflector-api-key", "TARS_REFLECTOR_API_KEY");
        var scraperTokenHandle = secretManager.registerFromEnv("scraper-token", "TARS_SCRAPER_TOKEN");
        var githubTokenHandle = secretManager.registerFromEnv("github-token", "TARS_GITHUB_TOKEN");

        // ── Podman ───────────────────────────────────────────────
        PodmanController podman = new PodmanController(dialogue);
        dialogue.say("Podman controller armed. Whitelisted images: " + podman.getAllowedImages().size(), DialogueStyle.OutputMode.CHAT);

        // ── Dual-LLM ────────────────────────────────────────────
        String ollamaEndpoint = buildOllamaGenerateEndpoint(ollamaUrl);
        LlmClient actor = new LlmClient(LlmRole.ACTOR, ollamaEndpoint, actorModel, actorKeyHandle);
        LlmClient reflector = new LlmClient(LlmRole.REFLECTOR, ollamaEndpoint, reflectorModel, reflectorKeyHandle);
        LlmService llmService = new DefaultLlmService(actor, reflector);
        DualLlmOrchestrator orchestrator = new DualLlmOrchestrator(actor, reflector, dialogue);

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
        EnvironmentFactory.registerAll(envRegistry, sandboxDispatcher);
        dialogue.say("Environments: " + envRegistry.size() + " registered (ALE-pinned).", DialogueStyle.OutputMode.CHAT);

        // ── OpenClaw Execution Layer ────────────────────────────
        OpenClawClient openClaw = new OpenClawClient(envRegistry, permissions, dialogue);
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
        ChatConfig baseChatConfig = ChatConfig.fromEnvironment();
        double threshold = chatQualityThreshold != null
                ? Math.max(0.0, Math.min(1.0, chatQualityThreshold))
                : baseChatConfig.qualityThreshold();
        ChatConfig chatConfig = new ChatConfig(threshold);
        ChatOrchestrator chatOrchestrator = new ChatOrchestrator(
                llmService, chatConfig, contextSummarizer, contextBudget);

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
        AuthenticationConfig authConfig = AuthenticationConfig.fromEnvironment();
        ProposalWebServer webServer = new ProposalWebServer(approvalGate, authConfig, dialogue, webPort);
        try {
            webServer.start();
        } catch (Exception e) {
            log.warn("Web UI failed to start on port {}: {}", webPort, e.getMessage());
            dialogue.say("Web UI unavailable — CLI approval still works.", DialogueStyle.OutputMode.CHAT);
        }

        // ── Interactive Loop ─────────────────────────────────────
        dialogue.say("Entering interactive mode. Type 'help' for commands, 'quit' to exit.", DialogueStyle.OutputMode.CHAT);
        runInteractiveLoop(dialogue, profile, approvalGate, registry,
                improvementLoop, supervisedDevLoop, podman, staging, metrics, learningEngine,
                scraperTokenHandle, webServer, vscodeConnector,
                auditLog, memorySystem, chunkLearning, sudoManager,
                envRegistry, openClaw, contextBudget, chatOrchestrator);
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
            ChatOrchestrator chatOrchestrator
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
                    return;
                }
                case "help" -> printHelp(dialogue);
                case "status" -> {
                    dialogue.say("Profile: " + profile, DialogueStyle.OutputMode.CHAT);
                    dialogue.say("Pending proposals: " + approvalGate.getPendingProposals().size(), DialogueStyle.OutputMode.CHAT);
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
                    var pending = approvalGate.getPendingProposals();
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
                default -> {
                    if (input.startsWith("approve ")) {
                        String id = input.substring(8).trim();
                        boolean ok = approvalGate.approve(id);
                        dialogue.say(ok ? "Proposal " + id + " approved. Proceeding." : "Not found or already decided.", DialogueStyle.OutputMode.CHAT);
                    } else if (input.startsWith("reject ")) {
                        String id = input.substring(7).trim();
                        boolean ok = approvalGate.reject(id);
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
                    } else if (!input.isEmpty()) {
                        String reply = chatOrchestrator.chat(input);
                        System.out.println("[TARS] " + reply);
                    }
                }
            }
        }
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

    private void printBanner(DialogueStyle dialogue) {
        System.out.println();
        System.out.println("╔═══════════════════════════════════════════════════════╗");
        System.out.println("║          TARS v2 — Autonomous Agent System            ║");
        System.out.println("║   \"Spinning up like a caffeinated dolphin\" 🐬☕        ║");
        System.out.println("╚═══════════════════════════════════════════════════════╝");
        System.out.println();
    }
}
