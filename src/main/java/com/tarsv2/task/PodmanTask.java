package com.tarsv2.task;

import com.tarsv2.podman.PodmanCommand;
import com.tarsv2.podman.PodmanController;
import com.tarsv2.podman.PodmanResult;

import java.io.IOException;
import java.util.Objects;

/**
 * A concrete, executable task that runs inside a Podman container.
 *
 * <p>Wraps a {@link PodmanCommand} with metadata and execution logic.
 * Tasks are the atomic unit of work that agents schedule through
 * the {@link PodmanController}.</p>
 */
public final class PodmanTask {

    private final String name;
    private final String description;
    private final PodmanCommand command;
    private final PodmanController controller;

    /**
     * @param name        task name for logging and tracking
     * @param description human-readable description
     * @param command     the validated Podman command
     * @param controller  the controller to execute through
     */
    public PodmanTask(String name, String description, PodmanCommand command, PodmanController controller) {
        this.name = Objects.requireNonNull(name);
        this.description = Objects.requireNonNull(description);
        this.command = Objects.requireNonNull(command);
        this.controller = Objects.requireNonNull(controller);
    }

    /**
     * Runs the Podman task and returns the result.
     *
     * @return container execution result
     * @throws IOException          if process creation fails
     * @throws InterruptedException if the thread is interrupted
     */
    public PodmanResult run() throws IOException, InterruptedException {
        return controller.execute(command);
    }

    public String getName() { return name; }
    public String getDescription() { return description; }

    @Override
    public String toString() {
        return String.format("PodmanTask[%s: %s]", name, description);
    }
}
