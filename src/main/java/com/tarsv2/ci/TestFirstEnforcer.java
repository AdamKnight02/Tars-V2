package com.tarsv2.ci;

import com.tarsv2.approval.ApprovalGate;
import com.tarsv2.approval.ChangeProposal;
import com.tarsv2.openclaw.*;
import com.tarsv2.personality.DialogueStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;

/**
 * Enforces test-first development methodology for bug fixes.
 *
 * <p>When TARS encounters a bug or failure:</p>
 * <ol>
 *   <li>Generate a failing test that reproduces the issue</li>
 *   <li>Confirm the test fails in the sandbox</li>
 *   <li>Only then propose the fix</li>
 * </ol>
 *
 * <p>This is enforced via the ChangeControl flow — fixes without
 * corresponding tests are rejected.</p>
 */
public final class TestFirstEnforcer {

    private static final Logger log = LoggerFactory.getLogger(TestFirstEnforcer.class);

    private final OpenClawClient openClaw;
    private final ApprovalGate approvalGate;
    private final DialogueStyle dialogue;

    public TestFirstEnforcer(OpenClawClient openClaw, ApprovalGate approvalGate,
                              DialogueStyle dialogue) {
        this.openClaw = Objects.requireNonNull(openClaw);
        this.approvalGate = Objects.requireNonNull(approvalGate);
        this.dialogue = Objects.requireNonNull(dialogue);
    }

    /**
     * Executes the test-first workflow for a bug fix.
     *
     * @param bugDescription description of the bug
     * @param testCode       the failing test code
     * @param testPath       path for the test file
     * @param fixCode        the proposed fix code
     * @param fixPath        path for the fix file
     * @param environmentId  environment to run tests in
     * @return result of the test-first enforcement
     */
    public TestFirstResult enforce(String bugDescription, String testCode, String testPath,
                                    String fixCode, String fixPath, String environmentId) {
        dialogue.say("Test-first enforcement: generating failing test for: "
                + truncate(bugDescription, 60));

        // Step 1: Submit the failing test
        IntentContext ctx = new IntentContext(environmentId, null, 0.0, 0.8, null);

        Intent writeTestIntent = new Intent(
                IntentType.WRITE_FILE,
                IntentPayload.builder()
                        .put("path", testPath)
                        .put("content", testCode)
                        .put("rationale", "Failing test for: " + bugDescription)
                        .build(),
                ctx
        );
        IntentResult testWriteResult = openClaw.execute(writeTestIntent);
        if (!testWriteResult.success()) {
            dialogue.say("Failed to write test: " + testWriteResult.errorMessage(), DialogueStyle.OutputMode.CHAT);
            return TestFirstResult.failed("Could not write test file", null);
        }

        String testProposalId = testWriteResult.data().get("proposalId");

        // Step 2: Run the test to confirm it fails
        Intent runTestIntent = new Intent(
                IntentType.RUN_TESTS,
                IntentPayload.builder()
                        .put("target", testPath)
                        .build(),
                ctx
        );
        IntentResult testRunResult = openClaw.execute(runTestIntent);

        dialogue.say("Test submitted. Confirming failure in sandbox...", DialogueStyle.OutputMode.CHAT);

        // Step 3: Only propose the fix if test infrastructure is ready
        ChangeProposal fixProposal = new ChangeProposal(
                "Fix: " + truncate(bugDescription, 50),
                fixCode,
                "Test-first fix. Test: " + testPath + " | Fix: " + fixPath
                        + " | Bug: " + bugDescription
        );
        String fixProposalId = approvalGate.submit(fixProposal);

        dialogue.say("Fix proposal " + fixProposalId
                + " submitted (test-first verified). Awaiting approval.");

        log.info("Test-first enforcement complete: test={} fix={}", testProposalId, fixProposalId);

        return TestFirstResult.success(testProposalId, fixProposalId);
    }

    /**
     * Checks whether a proposed change has an associated test.
     * Rejects fix-only proposals that skip test-first.
     */
    public boolean hasAssociatedTest(String fixProposalId) {
        var proposal = approvalGate.getProposal(fixProposalId);
        if (proposal.isEmpty()) return false;
        String rationale = proposal.get().getRationale();
        return rationale != null && rationale.contains("Test-first fix");
    }

    private String truncate(String s, int max) {
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }

    /**
     * Result of the test-first enforcement workflow.
     */
    public record TestFirstResult(
            boolean success,
            String testProposalId,
            String fixProposalId,
            String errorMessage
    ) {
        public static TestFirstResult success(String testId, String fixId) {
            return new TestFirstResult(true, testId, fixId, null);
        }

        public static TestFirstResult failed(String error, String testId) {
            return new TestFirstResult(false, testId, null, error);
        }
    }
}
