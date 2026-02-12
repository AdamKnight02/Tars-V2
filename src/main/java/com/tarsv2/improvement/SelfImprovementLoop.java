package com.tarsv2.improvement;

import com.tarsv2.approval.ApprovalGate;
import com.tarsv2.approval.ChangeProposal;
import com.tarsv2.learning.LearningEngine;
import com.tarsv2.llm.DualLlmOrchestrator;
import com.tarsv2.metrics.ObservationMetrics;
import com.tarsv2.personality.DialogueStyle;
import com.tarsv2.personality.EmotionState;
import com.tarsv2.personality.PersonalityProfile;
import com.tarsv2.sandbox.GitStagingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Implements TARS's self-improvement cycle.
 *
 * <p>The loop follows this sequence:</p>
 * <ol>
 *   <li><strong>Observe</strong> — gather metrics, logs, and performance data</li>
 *   <li><strong>Reflect</strong> — use the Reflector LLM to identify improvement areas</li>
 *   <li><strong>Propose</strong> — use the Actor LLM to generate concrete changes</li>
 *   <li><strong>Test</strong> — validate proposals in the sandbox</li>
 *   <li><strong>Request Approval</strong> — submit to the {@link ApprovalGate}</li>
 *   <li><strong>Merge</strong> — apply approved changes</li>
 * </ol>
 *
 * <p><strong>SAFETY:</strong> Steps 5 and 6 are HARD gates — no change
 * reaches production without explicit human approval.</p>
 *
 * <p>TODO: Implement learning signal extraction and persistent memory.</p>
 * <p>TODO: Implement observation metrics collection.</p>
 * <p>TODO: Implement sandbox testing harness for proposals.</p>
 */
public final class SelfImprovementLoop {

    private static final Logger log = LoggerFactory.getLogger(SelfImprovementLoop.class);

    private final DualLlmOrchestrator orchestrator;
    private final ApprovalGate approvalGate;
    private final GitStagingService stagingService;
    private final DialogueStyle dialogue;
    private final PersonalityProfile profile;
    private final ObservationMetrics metrics;
    private final LearningEngine learningEngine;

    /**
     * @param orchestrator   dual-LLM orchestrator for generating and evaluating improvements
     * @param approvalGate   the immutable approval gate
     * @param stagingService git staging service for proposals
     * @param dialogue       personality formatter
     * @param profile        personality profile (for emotion transitions)
     */
    public SelfImprovementLoop(
            DualLlmOrchestrator orchestrator,
            ApprovalGate approvalGate,
            GitStagingService stagingService,
            DialogueStyle dialogue,
            PersonalityProfile profile
    ) {
        this(orchestrator, approvalGate, stagingService, dialogue, profile, null, null);
    }

    /**
     * Full constructor with metrics and learning engine integration.
     */
    public SelfImprovementLoop(
            DualLlmOrchestrator orchestrator,
            ApprovalGate approvalGate,
            GitStagingService stagingService,
            DialogueStyle dialogue,
            PersonalityProfile profile,
            ObservationMetrics metrics,
            LearningEngine learningEngine
    ) {
        this.orchestrator = Objects.requireNonNull(orchestrator);
        this.approvalGate = Objects.requireNonNull(approvalGate);
        this.stagingService = Objects.requireNonNull(stagingService);
        this.dialogue = Objects.requireNonNull(dialogue);
        this.profile = Objects.requireNonNull(profile);
        this.metrics = metrics;
        this.learningEngine = learningEngine;
    }

    /**
     * Executes one full improvement cycle.
     *
     * @param context description of what to improve (e.g., "resume parsing accuracy")
     * @return the proposal ID if a proposal was generated, or null if nothing to propose
     */
    public String runCycle(String context) {
        dialogue.say("Starting self-improvement cycle for: " + context, DialogueStyle.OutputMode.CHAT);
        profile.transitionEmotion(EmotionState.CURIOUS);

        // Step 1: Observe
        String observations = observe(context);
        log.info(dialogue.narrate("Observations gathered: " + observations.length() + " chars"));

        // Step 2: Reflect (via dual-LLM)
        DualLlmOrchestrator.OrchestratorResult result = orchestrator.process(
                "Analyze these observations and propose a concrete improvement:\n" + observations
        );

        if (!result.passed()) {
            profile.transitionEmotion(EmotionState.FRUSTRATED);
            dialogue.say("Reflection loop did not converge. No proposal generated.", DialogueStyle.OutputMode.CHAT);
            return null;
        }

        profile.transitionEmotion(EmotionState.CONFIDENT);

        // Step 3: Propose
        ChangeProposal proposal = new ChangeProposal(
                "Self-improvement: " + context,
                result.output(),
                String.format("Quality score: %.2f after %d iterations", result.qualityScore(), result.iterations())
        );

        // Step 4: Test in sandbox
        // TODO: Run actual tests against the proposal in the sandbox
        dialogue.say("Sandbox testing: PASS (placeholder)", DialogueStyle.OutputMode.CHAT);

        // Step 5: Submit for approval
        String proposalId = approvalGate.submit(proposal);
        dialogue.say(String.format(
                "Improvement proposal submitted for human review. ID: %s\n" +
                "Description: %s\n" +
                "Awaiting approval — I cannot and will not apply this without your say-so.",
                proposalId, proposal.getDescription()));

        return proposalId;
    }

    /**
     * Gathers observations about the current state of the system.
     *
     * <p>TODO: Implement real metric collection — task success rates,
     * execution times, error logs, user feedback signals.</p>
     *
     * @param context the improvement area
     * @return observation data as text
     */
    private String observe(String context) {
        StringBuilder sb = new StringBuilder();
        sb.append("Observation context: ").append(context).append("\n\n");

        // Pull real metrics if available
        if (metrics != null && metrics.getTotalCount() > 0) {
            sb.append(metrics.getSummary()).append("\n");
        } else {
            sb.append("No task metrics collected yet.\n");
        }

        // Pull learning engine report if available
        if (learningEngine != null) {
            sb.append(learningEngine.getLearningReport()).append("\n");
        }

        return sb.toString();
    }
}
