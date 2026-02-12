package com.tarsv2.codex;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class PatchProposal {

    public enum Status {
        PENDING,
        APPROVED,
        REJECTED,
        APPLIED,
        FAILED
    }

    private final UUID id;
    private final String diff;
    private final String description;
    private final String targetPath;
    private final Instant createdAt;
    private volatile Status status;

    public PatchProposal(UUID id, String diff, String description, String targetPath, Status status) {
        this.id = Objects.requireNonNull(id);
        this.diff = Objects.requireNonNull(diff);
        this.description = Objects.requireNonNull(description);
        this.targetPath = Objects.requireNonNull(targetPath);
        this.status = Objects.requireNonNull(status);
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getDiff() { return diff; }
    public String getDescription() { return description; }
    public String getTargetPath() { return targetPath; }
    public Instant getCreatedAt() { return createdAt; }
    public Status getStatus() { return status; }

    public void setStatus(Status status) { this.status = Objects.requireNonNull(status); }
}
