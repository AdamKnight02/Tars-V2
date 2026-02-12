package com.tarsv2.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tarsv2.personality.DialogueStyle;
import com.tarsv2.podman.PodmanCommand;
import com.tarsv2.podman.PodmanController;
import com.tarsv2.podman.PodmanResult;
import com.tarsv2.schema.AgentTaskOutput;
import com.tarsv2.security.PodmanPolicy;
import com.tarsv2.security.SecretManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Concrete Podman task: Depop trend scraping container.
 *
 * <p>Executes the Depop analyzer container with proper secret injection
 * (via env vars only) and timeout enforcement. Produces structured
 * {@link AgentTaskOutput} results.</p>
 *
 * <p>Security enforcement:</p>
 * <ul>
 *   <li>Uses only whitelisted image: tarsv2/depop-analyzer:latest</li>
 *   <li>Secrets injected via env vars (validated by PodmanPolicy)</li>
 *   <li>Timeout enforced (180s max)</li>
 *   <li>Auto-cleanup on completion</li>
 *   <li>No privileged flags, no writable host mounts</li>
 * </ul>
 */
public final class DepopScrapingTask {

    private static final Logger log = LoggerFactory.getLogger(DepopScrapingTask.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private static final String IMAGE = "tarsv2/depop-analyzer:latest";
    private static final int TIMEOUT_SECONDS = 180;

    private final PodmanController podman;
    private final DialogueStyle dialogue;
    private final SecretManager.SecretHandle scraperTokenHandle;

    /**
     * @param podman             the Podman controller
     * @param dialogue           personality formatter
     * @param scraperTokenHandle opaque handle to the scraper auth token (nullable)
     */
    public DepopScrapingTask(PodmanController podman, DialogueStyle dialogue,
                             SecretManager.SecretHandle scraperTokenHandle) {
        this.podman = Objects.requireNonNull(podman);
        this.dialogue = Objects.requireNonNull(dialogue);
        this.scraperTokenHandle = scraperTokenHandle;
    }

    /**
     * Runs the Depop scraping task for the given query.
     *
     * @param query  the search query (e.g., "vintage denim jacket")
     * @param limit  max items to scrape
     * @return structured task output
     */
    public AgentTaskOutput run(String query, int limit) {
        String taskId = UUID.randomUUID().toString().substring(0, 8);
        Instant start = Instant.now();

        dialogue.say("Launching Depop scraper for: '" + query + "' (limit: " + limit + ")", DialogueStyle.OutputMode.CHAT);

        try {
            // Build container arguments
            List<String> args = new ArrayList<>();
            args.add("--query");
            args.add(query);
            args.add("--format");
            args.add("json");
            args.add("--limit");
            args.add(String.valueOf(Math.min(limit, 100)));

            // Build env vars for secret injection (validated by PodmanPolicy)
            Map<String, String> envVars = new LinkedHashMap<>();
            envVars.put("TARS_TASK_ID", taskId);
            envVars.put("TARS_TIMEOUT", String.valueOf(TIMEOUT_SECONDS));
            if (scraperTokenHandle != null) {
                envVars.put("SCRAPER_TOKEN", scraperTokenHandle.resolve());
            }

            // Validate env vars against PodmanPolicy
            PodmanPolicy.validateEnvVars(envVars);

            // Add env var flags to arguments
            for (var entry : envVars.entrySet()) {
                args.add(0, entry.getKey() + "=" + entry.getValue());
                args.add(0, "-e");
            }

            // Create and execute command
            PodmanCommand cmd = podman.createCommand(IMAGE, args, TIMEOUT_SECONDS);
            PodmanResult result = podman.execute(cmd);

            long latencyMs = Duration.between(start, Instant.now()).toMillis();

            if (result.isSuccess()) {
                dialogue.say("Depop scraper returned data. Parsing structured output...", DialogueStyle.OutputMode.CHAT);
                Map<String, Object> outputData = parseScraperOutput(result.stdout());
                outputData.put("query", query);
                outputData.put("container_duration_ms", result.duration().toMillis());

                return AgentTaskOutput.success(taskId, "DepopScrapingTask",
                        outputData, 0.85, latencyMs);
            } else {
                dialogue.say("Depop scraper failed with exit code " + result.exitCode(), DialogueStyle.OutputMode.CHAT);
                return AgentTaskOutput.failure(taskId, "DepopScrapingTask",
                        List.of("Container exit code: " + result.exitCode(),
                                "Stderr: " + result.stderr()),
                        latencyMs);
            }

        } catch (SecurityException e) {
            long latencyMs = Duration.between(start, Instant.now()).toMillis();
            log.error("Security violation in Depop scraping task", e);
            return AgentTaskOutput.failure(taskId, "DepopScrapingTask",
                    List.of("Security violation: " + e.getMessage()), latencyMs);
        } catch (IOException | InterruptedException e) {
            long latencyMs = Duration.between(start, Instant.now()).toMillis();
            log.error("Depop scraping task failed", e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return AgentTaskOutput.failure(taskId, "DepopScrapingTask",
                    List.of("Execution error: " + e.getMessage()), latencyMs);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseScraperOutput(String stdout) {
        try {
            return mapper.readValue(stdout, Map.class);
        } catch (Exception e) {
            log.warn("Could not parse scraper JSON output, wrapping raw text");
            return Map.of("raw_output", stdout);
        }
    }
}
