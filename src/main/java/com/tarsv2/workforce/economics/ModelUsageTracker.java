package com.tarsv2.workforce.economics;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ModelUsageTracker {

    private final ConcurrentHashMap<UUID, Usage> usageByTask = new ConcurrentHashMap<>();

    public void record(UUID taskId, Usage usage) {
        usageByTask.merge(taskId, usage, (current, incoming) -> new Usage(
                incoming.modelId(),
                current.inputTokens() + incoming.inputTokens(),
                current.outputTokens() + incoming.outputTokens(),
                current.apiCalls() + incoming.apiCalls()
        ));
    }

    public Usage getUsage(UUID taskId) {
        return usageByTask.getOrDefault(taskId, new Usage("unknown", 0, 0, 0));
    }

    public Map<String, Usage> getUsageByModel() {
        ConcurrentHashMap<String, Usage> out = new ConcurrentHashMap<>();
        for (Usage usage : usageByTask.values()) {
            out.merge(usage.modelId(), usage, (a, b) -> new Usage(
                    a.modelId(),
                    a.inputTokens() + b.inputTokens(),
                    a.outputTokens() + b.outputTokens(),
                    a.apiCalls() + b.apiCalls()
            ));
        }
        return Map.copyOf(out);
    }

    public record Usage(String modelId, int inputTokens, int outputTokens, int apiCalls) {
    }
}
