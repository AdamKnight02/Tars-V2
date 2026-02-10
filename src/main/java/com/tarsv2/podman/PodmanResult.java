package com.tarsv2.podman;

import java.time.Duration;

/**
 * Captures the result of a Podman container execution.
 *
 * @param exitCode  process exit code (0 = success)
 * @param stdout    standard output from the container
 * @param stderr    standard error from the container
 * @param duration  wall-clock execution time
 */
public record PodmanResult(
        int exitCode,
        String stdout,
        String stderr,
        Duration duration
) {

    /** Returns true if the container exited successfully. */
    public boolean isSuccess() {
        return exitCode == 0;
    }

    @Override
    public String toString() {
        return String.format("PodmanResult[exit=%d, duration=%s, stdout=%d chars, stderr=%d chars]",
                exitCode, duration, stdout.length(), stderr.length());
    }
}
