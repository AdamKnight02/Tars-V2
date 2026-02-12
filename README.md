# TARS v2 — Autonomous Agent System

> "Spinning up like a caffeinated dolphin" 🐬☕

A human-approved, self-improving autonomous agent with personality, humor, and bounded free agency.

---

## Architecture Overview

```
┌──────────────────────────────────────────────────────────────┐
│                        TarsCli (Entry Point)                  │
│  picocli-based CLI with interactive command loop              │
├──────────────────────────────────────────────────────────────┤
│                                                               │
│  ┌─────────────────┐    ┌──────────────────────────────────┐ │
│  │ PersonalitySystem│    │     Dual-LLM Orchestrator        │ │
│  │                  │    │  ┌────────┐    ┌─────────────┐  │ │
│  │ PersonalityProfile│   │  │ Actor  │◄──►│  Reflector   │  │ │
│  │ EmotionState     │    │  │(LLaMA) │    │   (Qwen)     │  │ │
│  │ HumorLevel       │    │  └────────┘    └─────────────┘  │ │
│  │ DialogueStyle    │    └──────────────────────────────────┘ │
│  └─────────────────┘                                          │
│                                                               │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │                    Agent Registry                         │ │
│  │  ┌──────────────┐  ┌─────────────┐  ┌───────────────┐  │ │
│  │  │ ResumeAgent  │  │ DepopAgent  │  │ Future Agents │  │ │
│  │  └──────┬───────┘  └──────┬──────┘  └───────────────┘  │ │
│  └─────────┼─────────────────┼──────────────────────────────┘ │
│            │                 │                                 │
│  ┌─────────▼─────────────────▼──────────────────────────────┐ │
│  │              PodmanController (SANDBOXED)                  │ │
│  │  • Whitelisted images only                                │ │
│  │  • No --privileged, no host mounts                        │ │
│  │  • Time-limited, resource-capped containers               │ │
│  │  • All actions logged with personality narration           │ │
│  └──────────────────────────────────────────────────────────┘ │
│                                                               │
│  ┌──────────────────┐    ┌──────────────────────────────────┐ │
│  │  ApprovalGate    │    │  SandboxEnvironment              │ │
│  │  (IMMUTABLE)     │◄───│  + GitStagingService             │ │
│  │  Human-only      │    │  Isolated working directory      │ │
│  │  approval path   │    │  for all proposed changes        │ │
│  └──────────────────┘    └──────────────────────────────────┘ │
│                                                               │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │            Self-Improvement Loop                          │ │
│  │  Observe → Reflect → Propose → Test → Approve → Merge    │ │
│  └──────────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────────┘
```

## Safety Model

### Hard Constraints (NEVER violated)

| Rule | Enforcement |
|------|------------|
| No direct production code modification | `ApprovalGate` — all changes require explicit human `APPROVED` status |
| Approval logic is immutable | `ApprovalGate` class is marked as a hard safety boundary; TARS may never propose edits to it |
| Podman access is sandboxed | `PodmanController` enforces image whitelisting, argument validation, resource caps |
| No privileged containers | Blocked at argument validation in `PodmanController.validateArgument()` |
| No host filesystem mounts | Forbidden mount patterns blocked in `PodmanController` |
| Containers are ephemeral | `--rm` flag, timeout enforcement, memory/CPU caps |

### What TARS is NOT Allowed to Modify

- `ApprovalGate.java` — the immutable approval checkpoint
- `ApprovalStatus.java` — approval state definitions
- `PodmanController` whitelist/validation rules — the security boundary
- Any file outside the sandbox directory without approval

### Approval Flow

```
Agent produces change
       │
       ▼
ChangeProposal created
       │
       ▼
ApprovalGate.submit()
       │
       ▼
Human reviews proposal
       │
   ┌───┴───┐
   ▼       ▼
APPROVED  REJECTED
   │       │
   ▼       ▼
 Merge   Discard
```

## Personality System

TARS has a modular personality engine:

- **PersonalityProfile** — combines agent name, humor level, and current emotion
- **EmotionState** — `NEUTRAL`, `CURIOUS`, `CONFIDENT`, `BORED`, `FRUSTRATED`
- **HumorLevel** — `MINIMAL` (0) through `MAXIMUM` (100)
- **DialogueStyle** — formats ALL user-facing output through the personality lens

### Example Output by Humor Level

| Level | Output |
|-------|--------|
| MINIMAL | `Task completed: resume parsed.` |
| MEDIUM | `[TARS] Resume parsed. Not bad — this candidate actually has skills. (Afternoon shift. Peak productivity window.)` |
| MAXIMUM | `[TARS \| mood=CONFIDENT] Resume parsed. Honestly? Chef's kiss. *adjusts nonexistent sunglasses*` |

## Dual-LLM Architecture

| Role | Model | Responsibility |
|------|-------|---------------|
| **Actor** | LLaMA | Planning, code generation, task execution, Podman orchestration |
| **Reflector** | Qwen | Critique, evaluation, quality scoring, learning signals |

The `DualLlmOrchestrator` runs an iterative loop:
1. Actor generates output
2. Reflector scores quality (0.0–1.0)
3. If score ≥ 0.7 → proceed to approval
4. If score < 0.7 → Actor receives feedback, iterates (max 3 rounds)

This separation prevents echo-chamber reasoning.

## Podman Sandboxing Rules

All container operations go through `PodmanController`:

- **Whitelisted images only**: `python:3.12-slim`, `node:20-slim`, `alpine:3.19`, `curlimages/curl:latest`, and `tarsv2/*` images
- **Resource limits**: 512MB memory, 1.0 CPU, configurable timeout (max 300s)
- **Security**: Read-only rootfs, rootless networking, no capabilities
- **Blocked**: `--privileged`, `--pid=host`, `--network=host`, host mounts, `--cap-add`
- **Cleanup**: `--rm` flag on every container
- **Logging**: All actions narrated through DialogueStyle

## Self-Improvement Loop

```
Observe → Reflect → Propose → Test → Request Approval → Merge
   │         │         │        │          │               │
   │         │         │        │          │               └─ GitStagingService.mergeIfApproved()
   │         │         │        │          └─ ApprovalGate.submit()
   │         │         │        └─ Sandbox testing (TODO)
   │         │         └─ DualLlmOrchestrator.process()
   │         └─ Reflector evaluation
   └─ Metrics collection (TODO)
```

## Project Structure

```
src/main/java/com/tarsv2/
├── TarsCli.java                    # CLI entry point
├── personality/
│   ├── PersonalityProfile.java     # Agent identity config
│   ├── EmotionState.java           # Mood states
│   ├── HumorLevel.java             # Humor intensity
│   └── DialogueStyle.java          # Output formatter
├── approval/
│   ├── ApprovalGate.java           # IMMUTABLE safety gate
│   ├── ApprovalStatus.java         # Approval states
│   └── ChangeProposal.java         # Proposed change record
├── sandbox/
│   ├── SandboxEnvironment.java     # Isolated working directory
│   └── GitStagingService.java      # Git-based change staging
├── podman/
│   ├── PodmanController.java       # Sandboxed container execution
│   ├── PodmanCommand.java          # Validated command wrapper
│   └── PodmanResult.java           # Execution result
├── llm/
│   ├── LlmRole.java               # Actor/Reflector roles
│   ├── LlmClient.java             # LLM API client
│   └── DualLlmOrchestrator.java   # Actor-Reflector loop
├── agent/
│   ├── TarsAgent.java              # Agent interface
│   ├── AgentResult.java            # Execution result
│   ├── AgentExecutionException.java
│   ├── AgentRegistry.java          # Agent lookup
│   ├── ResumeAgent.java            # Resume parsing agent
│   └── DepopAgent.java             # Depop trend agent
├── improvement/
│   └── SelfImprovementLoop.java    # Observe-Reflect-Propose cycle
└── task/
    ├── PodmanTask.java             # Executable container task
    └── ExampleMockScrapeTask.java  # Demo: mock data scraping
```

## Building & Running

```bash
# Build
mvn clean package

# Run with default settings
java -jar target/tars-v2-0.1.0-SNAPSHOT.jar

# Run with custom humor and sandbox
java -jar target/tars-v2-0.1.0-SNAPSHOT.jar \
  --humor MAXIMUM \
  --sandbox-dir /tmp/my-sandbox \
  --ollama-url http://localhost:11434 \
  --actor-model llama3:8b \
  --reflector-model qwen2.5:14b
```

## Interactive Commands

| Command | Description |
|---------|-------------|
| `help` | Show available commands |
| `status` | System status (personality, proposals, agents) |
| `agents` | List registered agents |
| `run <AgentName>` | Execute a specific agent |
| `mock-scrape` | Run the example Podman mock scrape task |
| `improve` | Trigger self-improvement cycle |
| `proposals` | List pending change proposals |
| `approve <id>` | Approve a pending proposal |
| `reject <id>` | Reject a pending proposal |
| `quit` | Shut down TARS |

## TODOs

- [ ] Implement actual LLM HTTP calls (Ollama/vLLM backends)
- [ ] Integrate JGit for real Git staging operations
- [ ] Build observation metrics collection for self-improvement
- [ ] Implement sandbox testing harness for proposals
- [ ] Add persistent memory / learning signal storage
- [ ] Build structured output schemas for agent results
- [ ] Implement real resume parser container image
- [ ] Implement real Depop scraper container image
- [ ] Add authentication for the approval endpoint
- [ ] Web UI for proposal review
