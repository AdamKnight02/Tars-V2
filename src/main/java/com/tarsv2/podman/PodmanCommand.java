package com.tarsv2.podman;

import java.util.List;
import java.util.Objects;

/**
 * Represents a validated, ready-to-execute Podman command.
 *
 * <p>Instances are created only by {@link PodmanController} after
 * passing whitelist validation. Direct construction is package-private
 * to prevent bypass.</p>
 */
public final class PodmanCommand {

    private final String image;
    private final List<String> arguments;
    private final int timeoutSeconds;

    /**
     * Package-private constructor — only {@link PodmanController} creates these.
     *
     * @param image          container image to run
     * @param arguments      command arguments inside the container
     * @param timeoutSeconds maximum execution time before kill
     */
    PodmanCommand(String image, List<String> arguments, int timeoutSeconds) {
        this.image = Objects.requireNonNull(image);
        this.arguments = List.copyOf(arguments);
        this.timeoutSeconds = timeoutSeconds;
    }

    public String getImage() { return image; }
    public List<String> getArguments() { return List.copyOf(arguments); }
    public int getTimeoutSeconds() { return timeoutSeconds; }

    /**
     * Builds the full Podman CLI command array.
     *
     * @return command array ready for ProcessBuilder
     */
    List<String> toCommandArray() {
        var cmd = new java.util.ArrayList<String>();
        cmd.add("podman");
        cmd.add("run");
        cmd.add("--rm");                          // auto-cleanup
        cmd.add("--network=slirp4netns");         // rootless network isolation
        cmd.add("--timeout=" + timeoutSeconds);   // hard time limit
        cmd.add("--memory=512m");                 // memory cap
        cmd.add("--cpus=1.0");                    // CPU cap
        cmd.add("--read-only");                   // read-only rootfs
        cmd.add(image);
        cmd.addAll(arguments);
        return List.copyOf(cmd);
    }

    @Override
    public String toString() {
        return String.format("PodmanCommand[image=%s, args=%s, timeout=%ds]",
                image, arguments, timeoutSeconds);
    }
}
