package com.tarsv2.context;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Enforces strict token budget per LLM reasoning pass.
 *
 * <p>Rules:</p>
 * <ul>
 *   <li>Never pass raw files or logs to the LLM</li>
 *   <li>Always summarize and structure tool results</li>
 *   <li>If budget exceeded: summarize → retry → or defer</li>
 * </ul>
 */
public final class ContextBudget {

    private static final Logger log = LoggerFactory.getLogger(ContextBudget.class);

    private final int maxTokens;
    private int currentTokens;

    public ContextBudget(int maxTokens) {
        if (maxTokens <= 0) throw new IllegalArgumentException("maxTokens must be > 0");
        this.maxTokens = maxTokens;
        this.currentTokens = 0;
    }

    /**
     * Checks whether adding the given content would exceed the budget.
     */
    public boolean wouldExceed(String content) {
        return currentTokens + estimateTokens(content) > maxTokens;
    }

    /**
     * Adds content to the budget tracker. Returns true if within budget.
     */
    public boolean consume(String content) {
        int tokens = estimateTokens(content);
        if (currentTokens + tokens > maxTokens) {
            log.warn("Context budget exceeded: current={} + new={} > max={}",
                    currentTokens, tokens, maxTokens);
            return false;
        }
        currentTokens += tokens;
        return true;
    }

    /**
     * Returns remaining token budget.
     */
    public int getRemaining() {
        return maxTokens - currentTokens;
    }

    /**
     * Returns current usage as a fraction of max.
     */
    public double getUsageFraction() {
        return (double) currentTokens / maxTokens;
    }

    /**
     * Resets the budget for a new reasoning pass.
     */
    public void reset() {
        this.currentTokens = 0;
    }

    /**
     * Estimates token count from string content.
     * Uses ~4 chars per token heuristic.
     */
    static int estimateTokens(String content) {
        if (content == null || content.isEmpty()) return 0;
        return (content.length() + 3) / 4;
    }

    @Override
    public String toString() {
        return String.format("ContextBudget[%d/%d tokens, %.0f%% used]",
                currentTokens, maxTokens, getUsageFraction() * 100);
    }
}
