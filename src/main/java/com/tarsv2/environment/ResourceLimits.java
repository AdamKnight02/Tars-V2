package com.tarsv2.environment;

/**
 * Resource limits for an execution environment.
 *
 * @param maxMemoryMb      maximum memory in megabytes
 * @param maxCpuPercent     maximum CPU percentage (0-100)
 * @param maxDiskMb         maximum disk usage in megabytes
 * @param timeoutSeconds    maximum execution time per intent
 * @param maxConcurrentOps  maximum concurrent operations
 */
public record ResourceLimits(
        int maxMemoryMb,
        int maxCpuPercent,
        int maxDiskMb,
        int timeoutSeconds,
        int maxConcurrentOps
) {
    public static ResourceLimits standard() {
        return new ResourceLimits(512, 50, 1024, 120, 4);
    }

    public static ResourceLimits restricted() {
        return new ResourceLimits(256, 25, 512, 60, 2);
    }

    public static ResourceLimits elevated() {
        return new ResourceLimits(1024, 75, 2048, 300, 8);
    }
}
