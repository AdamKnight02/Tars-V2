package com.tarsv2.connector;

import com.tarsv2.personality.DialogueStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Audit log for all VS Code and GitHub connector actions.
 *
 * <p>Every action is logged with timestamp, intent, and files affected.
 * Logs are human-readable and personality-aware via DialogueStyle.</p>
 */
public final class DevAuditLog {

    private static final Logger log = LoggerFactory.getLogger(DevAuditLog.class);
    private static final int MAX_ENTRIES = 500;

    private final Deque<AuditEntry> entries = new ConcurrentLinkedDeque<>();
    private final DialogueStyle dialogue;

    public DevAuditLog(DialogueStyle dialogue) {
        this.dialogue = dialogue;
    }

    /**
     * Logs a connector action.
     */
    public void record(String connector, String action, String intent,
                        List<String> filesAffected) {
        AuditEntry entry = new AuditEntry(
                Instant.now(), connector, action, intent,
                filesAffected != null ? List.copyOf(filesAffected) : List.of()
        );
        entries.addLast(entry);
        while (entries.size() > MAX_ENTRIES) {
            entries.removeFirst();
        }

        log.info("[AUDIT] {} | {} | {} | files={}",
                connector, action, intent, filesAffected);

        // Personality-aware narration
        String narrated = dialogue.narrate(
                String.format("%s: %s — %s", connector, action, intent));
        log.debug(narrated);
    }

    /**
     * Returns all audit entries.
     */
    public List<AuditEntry> getEntries() {
        return List.copyOf(entries);
    }

    /**
     * Returns a human-readable summary of recent actions.
     */
    public String getSummary() {
        if (entries.isEmpty()) {
            return "No connector actions recorded yet.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("=== Dev Connector Audit Log ===\n");
        sb.append(String.format("Total actions: %d\n\n", entries.size()));

        // Show last 10
        List<AuditEntry> recent = new ArrayList<>(entries);
        int start = Math.max(0, recent.size() - 10);
        for (int i = start; i < recent.size(); i++) {
            AuditEntry e = recent.get(i);
            sb.append(String.format("  [%s] %s | %s | %s",
                    e.timestamp(), e.connector(), e.action(), e.intent()));
            if (!e.filesAffected().isEmpty()) {
                sb.append(" | files: ").append(String.join(", ", e.filesAffected()));
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * A single audit log entry.
     */
    public record AuditEntry(
            Instant timestamp,
            String connector,
            String action,
            String intent,
            List<String> filesAffected
    ) {}
}
