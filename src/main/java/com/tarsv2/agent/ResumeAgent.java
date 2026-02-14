package com.tarsv2.agent;

import com.tarsv2.personality.DialogueStyle;
import com.tarsv2.podman.PodmanCommand;
import com.tarsv2.podman.PodmanController;
import com.tarsv2.podman.PodmanResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

/**
 * Agent specialized in parsing and analyzing resumes.
 *
 * <p>Uses a sandboxed Podman container to run resume parsing tools,
 * then processes the results through the dual-LLM loop for
 * evaluation and structured output.</p>
 *
 * <p>Example TARS narration:</p>
 * <pre>
 *   [TARS | mood=CURIOUS] "Got a resume. Let me spin up the parser container
 *   and see what this candidate is working with."
 * </pre>
 *
 * <p>TODO: Integrate with actual resume parsing library (Apache Tika, etc.).</p>
 * <p>TODO: Add structured output schema for parsed resume fields.</p>
 * <p>TODO: Implement learning loop to improve parsing accuracy over time.</p>
 */
public final class ResumeAgent implements TarsAgent {

    private static final Logger log = LoggerFactory.getLogger(ResumeAgent.class);

    private final PodmanController podman;
    private final DialogueStyle dialogue;

    /**
     * @param podman   the Podman controller for container execution
     * @param dialogue personality formatter
     */
    public ResumeAgent(PodmanController podman, DialogueStyle dialogue) {
        this.podman = Objects.requireNonNull(podman);
        this.dialogue = Objects.requireNonNull(dialogue);
    }

    @Override
    public String getName() {
        return "ResumeAgent";
    }

    @Override
    public String getDescription() {
        return "Parses and analyzes resumes using sandboxed container execution.";
    }

    @Override
    public AgentResult execute(String resumePath) throws AgentExecutionException {
        dialogue.say("Got a resume to analyze. Spinning up the parser container...", DialogueStyle.OutputMode.CHAT);

        try {
            // TODO: Replace with actual resume parser container image
            PodmanCommand cmd = podman.createCommand(
                    "tarsv2/resume-parser:latest",
                    List.of("--input", "/data/resume.pdf", "--format", "json"),
                    120 // 2 minute timeout for large resumes
            );

            PodmanResult result = podman.execute(cmd);

            if (result.isSuccess()) {
                dialogue.say("Resume parsed successfully. Let me analyze the results...", DialogueStyle.OutputMode.CHAT);
                // TODO: Feed result.stdout() into SingleModelOrchestrator for analysis
                return AgentResult.success(
                        getName(),
                        result.stdout(),
                        "Resume parsed — structured data extracted."
                );
            } else {
                dialogue.say("Parser container failed. Stderr: " + result.stderr(), DialogueStyle.OutputMode.CHAT);
                return AgentResult.failure(getName(), "Container failed: " + result.stderr());
            }

        } catch (SecurityException e) {
            throw new AgentExecutionException("Security violation in resume parsing", e);
        } catch (Exception e) {
            throw new AgentExecutionException("Resume parsing failed", e);
        }
    }
}
