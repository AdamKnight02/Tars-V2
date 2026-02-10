package com.tarsv2.task;

import com.tarsv2.personality.DialogueStyle;
import com.tarsv2.podman.PodmanCommand;
import com.tarsv2.podman.PodmanController;
import com.tarsv2.podman.PodmanResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Example Podman task: scrape mock data inside a sandboxed container.
 *
 * <p>Demonstrates the full task lifecycle:</p>
 * <ol>
 *   <li>Create a validated command via {@link PodmanController}</li>
 *   <li>Execute in a sandboxed, time-limited, resource-capped container</li>
 *   <li>Capture and log results with personality narration</li>
 * </ol>
 *
 * <p>Expected TARS log output at MEDIUM humor:</p>
 * <pre>
 *   [TARS] Spinning up mock scraper container... (Afternoon shift. Peak productivity window.)
 *   [TARS] Container returned 42 lines of mock data. Not bad for a box with 512MB of RAM.
 * </pre>
 */
public final class ExampleMockScrapeTask {

    private static final Logger log = LoggerFactory.getLogger(ExampleMockScrapeTask.class);

    private final PodmanController podman;
    private final DialogueStyle dialogue;

    /**
     * @param podman   the Podman controller
     * @param dialogue personality formatter
     */
    public ExampleMockScrapeTask(PodmanController podman, DialogueStyle dialogue) {
        this.podman = podman;
        this.dialogue = dialogue;
    }

    /**
     * Runs the mock scrape task.
     *
     * <p>Uses a lightweight Alpine container to simulate scraping
     * by generating mock JSON data with a simple shell command.</p>
     *
     * @return the mock scrape result
     * @throws Exception if execution fails
     */
    public PodmanResult run() throws Exception {
        dialogue.say("Spinning up mock scraper container...");

        // Uses Alpine to echo mock JSON — simulates a real scraper
        PodmanCommand cmd = podman.createCommand(
                "alpine:3.19",
                List.of(
                        "sh", "-c",
                        "echo '{\"source\": \"mock\", \"items\": [" +
                        "{\"name\": \"Vintage Denim Jacket\", \"price\": 45.00, \"trending\": true}," +
                        "{\"name\": \"Y2K Cargo Pants\", \"price\": 32.00, \"trending\": true}," +
                        "{\"name\": \"Retro Band Tee\", \"price\": 18.50, \"trending\": false}" +
                        "], \"scraped_at\": \"2025-01-01T00:00:00Z\"}'"
                ),
                30 // 30 second timeout for this simple task
        );

        PodmanResult result = podman.execute(cmd);

        if (result.isSuccess()) {
            dialogue.say("Mock scraper returned data. Not bad for a box with 512MB of RAM.");
            log.info("Mock scrape output: {}", result.stdout());
        } else {
            dialogue.say("Mock scraper failed. Stderr: " + result.stderr());
        }

        return result;
    }
}
