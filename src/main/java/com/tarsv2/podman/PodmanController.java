package com.tarsv2.podman;

import com.tarsv2.personality.DialogueStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * ╔══════════════════════════════════════════════════════════════════╗
 * ║                   PODMAN CONTROLLER                              ║
 * ║                                                                  ║
 * ║  Single point of access for ALL container operations.            ║
 * ║                                                                  ║
 * ║  NON-NEGOTIABLE RULES:                                           ║
 * ║  1. Only whitelisted images may be used.                         ║
 * ║  2. No --privileged flag. Ever.                                  ║
 * ║  3. No host filesystem mounts except read-only temp dirs.        ║
 * ║  4. All containers are time-limited and auto-cleaned.            ║
 * ║  5. All executions are logged with personality-aware narration.   ║
 * ╚══════════════════════════════════════════════════════════════════╝
 */
public final class PodmanController {

    private static final Logger log = LoggerFactory.getLogger(PodmanController.class);

    /** Maximum allowed container runtime in seconds. */
    private static final int MAX_TIMEOUT_SECONDS = 300;

    /** Default timeout if none specified. */
    private static final int DEFAULT_TIMEOUT_SECONDS = 60;

    /**
     * Whitelist of allowed container images.
     * Only these images may be executed through this controller.
     */
    private static final Set<String> ALLOWED_IMAGES = Set.of(
            "python:3.12-slim",
            "node:20-slim",
            "alpine:3.19",
            "curlimages/curl:latest",
            "tarsv2/scraper:latest",
            "tarsv2/resume-parser:latest",
            "tarsv2/depop-analyzer:latest"
    );

    private final DialogueStyle dialogue;

    /**
     * @param dialogue personality formatter for narrating container actions
     */
    public PodmanController(DialogueStyle dialogue) {
        this.dialogue = Objects.requireNonNull(dialogue, "dialogue");
    }

    /**
     * Validates and creates a Podman command. Rejects disallowed images.
     *
     * @param image          container image name
     * @param arguments      command to run inside the container
     * @param timeoutSeconds max runtime (capped at {@value MAX_TIMEOUT_SECONDS}s)
     * @return a validated PodmanCommand
     * @throws SecurityException if the image is not whitelisted
     */
    public PodmanCommand createCommand(String image, List<String> arguments, int timeoutSeconds) {
        // SECURITY: Reject non-whitelisted images
        if (!ALLOWED_IMAGES.contains(image)) {
            String msg = String.format("BLOCKED: Image '%s' is not whitelisted. Allowed: %s",
                    image, ALLOWED_IMAGES);
            log.error(msg);
            throw new SecurityException(msg);
        }

        // SECURITY: Cap timeout
        int safTimeout = Math.min(Math.max(timeoutSeconds, 1), MAX_TIMEOUT_SECONDS);

        // SECURITY: Reject dangerous arguments
        for (String arg : arguments) {
            validateArgument(arg);
        }

        PodmanCommand cmd = new PodmanCommand(image, arguments, safTimeout);
        log.info(dialogue.narrate("Validated command: " + cmd));
        return cmd;
    }

    /**
     * Creates a command with default timeout.
     *
     * @param image     container image name
     * @param arguments command to run inside the container
     * @return a validated PodmanCommand
     */
    public PodmanCommand createCommand(String image, List<String> arguments) {
        return createCommand(image, arguments, DEFAULT_TIMEOUT_SECONDS);
    }

    /**
     * Executes a validated Podman command and returns the result.
     *
     * @param command the validated command to execute
     * @return execution result including stdout, stderr, and exit code
     * @throws IOException          if process creation fails
     * @throws InterruptedException if execution is interrupted
     */
    public PodmanResult execute(PodmanCommand command) throws IOException, InterruptedException {
        List<String> cmdArray = command.toCommandArray();
        log.info(dialogue.narrate(
                String.format("Spinning up container [%s] — like a caffeinated dolphin", command.getImage())));

        Instant start = Instant.now();

        ProcessBuilder pb = new ProcessBuilder(cmdArray)
                .redirectErrorStream(false);

        Process process = pb.start();

        // Read stdout and stderr
        String stdout = new String(process.getInputStream().readAllBytes());
        String stderr = new String(process.getErrorStream().readAllBytes());

        boolean finished = process.waitFor(command.getTimeoutSeconds(), TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            log.warn(dialogue.narrate("Container timed out after " + command.getTimeoutSeconds() + "s — force killed."));
        }

        Duration duration = Duration.between(start, Instant.now());
        PodmanResult result = new PodmanResult(
                process.exitValue(),
                stdout,
                stderr,
                duration
        );

        log.info(dialogue.narrate("Container finished: " + result));
        return result;
    }

    /**
     * Returns the set of currently allowed images.
     *
     * @return unmodifiable set of whitelisted image names
     */
    public Set<String> getAllowedImages() {
        return ALLOWED_IMAGES;
    }

    /**
     * Validates a single command argument against known dangerous patterns.
     *
     * @param arg the argument to validate
     * @throws SecurityException if the argument is dangerous
     */
    private void validateArgument(String arg) {
        // Block shell injection attempts
        List<String> forbidden = List.of(
                "--privileged",
                "--pid=host",
                "--network=host",
                "--cap-add",
                "--security-opt",
                "-v /",         // host root mount
                "-v /etc",
                "-v /var",
                "-v /home",
                "--mount"
        );

        String lower = arg.toLowerCase();
        for (String pattern : forbidden) {
            if (lower.contains(pattern.toLowerCase())) {
                String msg = String.format("BLOCKED: Argument '%s' matches forbidden pattern '%s'", arg, pattern);
                log.error(msg);
                throw new SecurityException(msg);
            }
        }
    }
}
