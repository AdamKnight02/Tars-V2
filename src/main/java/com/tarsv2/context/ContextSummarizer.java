package com.tarsv2.context;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Summarizes tool results and raw content before passing to the LLM.
 *
 * <p>Enforces context discipline: never pass raw files, logs, or
 * unstructured output to the LLM. Everything is summarized and
 * structured first.</p>
 */
public final class ContextSummarizer {

    private static final Logger log = LoggerFactory.getLogger(ContextSummarizer.class);

    /** Maximum lines to include from a file summary. */
    private static final int MAX_FILE_SUMMARY_LINES = 30;
    /** Maximum characters for a tool result summary. */
    private static final int MAX_SUMMARY_CHARS = 2000;

    /**
     * Summarizes a file's content for LLM consumption.
     * Returns structured metadata, not raw content.
     */
    public String summarizeFile(String path, String content) {
        String[] lines = content.split("\n", -1);
        int lineCount = lines.length;
        int charCount = content.length();

        StringBuilder summary = new StringBuilder();
        summary.append(String.format("File: %s (%d lines, %d chars)\n", path, lineCount, charCount));

        // Include first N lines as preview
        int previewLines = Math.min(MAX_FILE_SUMMARY_LINES, lineCount);
        summary.append("Preview:\n");
        for (int i = 0; i < previewLines; i++) {
            summary.append("  ").append(i + 1).append(": ").append(truncateLine(lines[i])).append("\n");
        }
        if (lineCount > previewLines) {
            summary.append("  ... (").append(lineCount - previewLines).append(" more lines)\n");
        }

        return truncate(summary.toString(), MAX_SUMMARY_CHARS);
    }

    /**
     * Summarizes a diff for LLM consumption.
     */
    public String summarizeDiff(String diff) {
        String[] lines = diff.split("\n", -1);
        long additions = 0, deletions = 0;
        for (String line : lines) {
            if (line.startsWith("+") && !line.startsWith("+++")) additions++;
            if (line.startsWith("-") && !line.startsWith("---")) deletions++;
        }

        return String.format("Diff: +%d/-%d lines (%d total)\n%s",
                additions, deletions, lines.length,
                truncate(diff, MAX_SUMMARY_CHARS));
    }

    /**
     * Summarizes CI logs — extracts structure, discards raw output.
     */
    public String summarizeCILog(String rawLog) {
        // Never pass raw logs — extract key signals only
        String[] lines = rawLog.split("\n", -1);
        int errorCount = 0, warningCount = 0;
        StringBuilder errors = new StringBuilder();

        for (String line : lines) {
            String lower = line.toLowerCase();
            if (lower.contains("error") || lower.contains("fail")) {
                errorCount++;
                if (errors.length() < 500) {
                    errors.append("  ").append(truncateLine(line)).append("\n");
                }
            }
            if (lower.contains("warning") || lower.contains("warn")) {
                warningCount++;
            }
        }

        return String.format("CI Log Summary: %d errors, %d warnings, %d total lines\n%s",
                errorCount, warningCount, lines.length, errors);
    }

    /**
     * Summarizes a tool result map for context injection.
     */
    public String summarizeToolResult(Map<String, String> data) {
        return data.entrySet().stream()
                .map(e -> e.getKey() + ": " + truncate(e.getValue(), 200))
                .collect(Collectors.joining("\n"));
    }

    /**
     * Checks if content needs summarization based on budget.
     */
    public boolean needsSummarization(String content, ContextBudget budget) {
        return budget.wouldExceed(content);
    }

    /**
     * Produces a summarized version that fits within budget.
     */
    public String fitToBudget(String content, ContextBudget budget) {
        if (!budget.wouldExceed(content)) {
            return content;
        }

        int targetChars = budget.getRemaining() * 4; // ~4 chars per token
        if (targetChars <= 0) {
            return "[Context budget exhausted — content deferred]";
        }
        return truncate(content, targetChars);
    }

    private String truncateLine(String line) {
        return line.length() > 120 ? line.substring(0, 120) + "..." : line;
    }

    private String truncate(String s, int max) {
        return s.length() > max ? s.substring(0, max) + "...[truncated]" : s;
    }
}
