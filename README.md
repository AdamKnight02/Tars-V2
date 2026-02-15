# TARS v2 — Deterministic Engineering Agent

> Deterministic patch generation with strict safety boundaries.

TARS v2 is a human-approved engineering agent focused on predictable, auditable code changes.
It uses a **mode-based model router** and a **structured patch pipeline** so generated changes are deterministic and constrained.

---

## What Changed

This repository no longer uses dual-LLM reflection loops for patch generation.

### Core design now

- **No dual reflection/retry loops** in the code-patch path.
- **No persona injection** in Codex patch prompts.
- **ModelRouter-based routing** by mode:
  - `CODEX` → MiniMax M2.5 (deterministic patch instructions)
  - `RESEARCH` → GLM (planning/reasoning only)
  - `CHAT` → MiniMax M2.5 (minimal direct responses)
- **Structured patch contract** from model:
  - `{ "file", "operation", "location", "content" }`
- **Deterministic Java diff construction** (model does not generate unified diffs directly).
- **Strict patch scope validation**: only `src/main/java/**` is allowed.

---

## Architecture Overview

```text
TarsCli
  ├─ ChatOrchestrator (CHAT mode)
  ├─ ResearchOrchestrator (RESEARCH mode)
  └─ CodexOrchestrator (CODEX mode)

Model Layer
  ├─ model/config
  │   ├─ ModelConfig
  │   ├─ ModeBindings
  │   ├─ ModelProfile
  │   └─ ProviderConfig
  └─ model/router
      ├─ ModelRouter
      ├─ DefaultModelRouter
      └─ RoutingMode (CHAT/CODEX/RESEARCH)

Codex Deterministic Patch Pipeline
  ├─ PatchInstructionParser
  ├─ PatchInstruction {file, operation, location, content}
  ├─ PatchValidator (instruction + diff validation)
  ├─ DeterministicPatchBuilder (pure diff generation)
  └─ tool facades
      ├─ SandboxToolFacade
      ├─ DiffToolFacade
      └─ GitToolFacade
```

---

## Deterministic Patch Flow

1. User provides `--file` and `--task`.
2. `CodexOrchestrator` routes request using `RoutingMode.CODEX`.
3. MiniMax M2.5 returns **JSON patch instruction only**.
4. `PatchInstructionParser` deserializes JSON.
5. `PatchValidator.validateInstruction()` enforces schema and path policy.
6. `DeterministicPatchBuilder` builds unified diff in Java.
7. `PatchValidator.validate()` verifies resulting diff paths.
8. Diff can then be staged/applied through tool/sandbox flow.

---

## Safety Model

### Hard constraints

| Rule | Enforcement |
|---|---|
| Automated patches can only target `src/main/java/**` | `PatchValidator` instruction + diff checks |
| No model-authored raw unified diffs accepted | model returns JSON instruction only |
| Human approval remains required for proposal merge flows | `ApprovalGate` |
| Sandbox path traversal blocked | `SandboxToolFacade` path normalization + root check |

---

## Model Modes

| Mode | Primary model | Usage |
|---|---|---|
| `CODEX` | MiniMax M2.5 | Deterministic patch instruction generation |
| `RESEARCH` | GLM-5 / GLM-4.7 family | Architecture reasoning/planning |
| `CHAT` | MiniMax M2.5 | Minimal interactive responses |

---

## Project Structure (key directories)

```text
src/main/java/com/tarsv2/
├── codex/
│   ├── CodexOrchestrator.java
│   ├── DeterministicPatchBuilder.java
│   ├── PatchValidator.java
│   └── instruction/
│       ├── PatchInstruction.java
│       ├── PatchInstructionParser.java
│       └── PatchOperation.java
├── llm/
│   ├── ChatOrchestrator.java
│   ├── ResearchOrchestrator.java
│   └── provider/
│       ├── minimax/MiniMaxM25Client.java
│       └── glm/GlmResearchClient.java
├── model/
│   ├── ModeRouter.java (compat adapter)
│   ├── config/
│   └── router/
├── tool/
│   ├── diff/DiffToolFacade.java
│   ├── git/GitToolFacade.java
│   └── sandbox/SandboxToolFacade.java
└── ... (agents, sandbox, approval, podman, etc.)

src/test/java/com/tarsv2/integration/
├── DeterministicPatchFlowIT.java
├── ModelRouterModeSelectionIT.java
└── PatchScopeValidatorIT.java
```

---

## Build & Run

```bash
# Build
mvn clean package

# Run interactive CLI
java -jar target/tars-v2-0.1.0-SNAPSHOT.jar

# Deterministic codex diff mode
java -jar target/tars-v2-0.1.0-SNAPSHOT.jar \
  --file src/main/java/com/tarsv2/TarsCli.java \
  --task "Add a helper method for ..."
```

---

## Integration Tests

The repo includes integration tests for the deterministic architecture:

- `DeterministicPatchFlowIT` → same input, byte-identical diff output.
- `ModelRouterModeSelectionIT` → verifies mode-to-model routing.
- `PatchScopeValidatorIT` → enforces `src/main/java/**` restriction.

---

## Environment Variables (model/config)

- `TARS_MODEL_CHAT` (default: `m2.5`)
- `TARS_MODEL_CODEX` (default: `m2.5`)
- `TARS_MODEL_RESEARCH` (default: `glm-5`)
- `TARS_MINIMAX_MODEL` (default: `MiniMax-M2.5`)
- `TARS_GLM_RESEARCH_MODEL` (default: `GLM-5`)
- `TARS_GLM_FUTURE_MODEL` (default: `GLM-future`)
- `TARS_MINIMAX_API_URL`
- `TARS_GLM_API_URL`
- `TARS_MODEL_TIMEOUT_SECONDS`

---

## Notes

- `ModeRouter` currently remains as a compatibility adapter around the new router package to support incremental migration.
- If Maven plugin resolution is blocked in the execution environment, CI should run tests in a network-enabled runner.
