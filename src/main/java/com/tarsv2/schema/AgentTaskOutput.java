package com.tarsv2.schema;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Structured output schema for agent task execution results.
 * JSON-serializable via Jackson.
 */
public record AgentTaskOutput(
        @JsonProperty("task_id") String taskId,
        @JsonProperty("agent_name") String agentName,
        @JsonProperty("status") TaskStatus status,
        @JsonProperty("output_data") Map<String, Object> outputData,
        @JsonProperty("confidence_score") double confidenceScore,
        @JsonProperty("latency_ms") long latencyMs,
        @JsonProperty("errors") List<String> errors,
        @JsonProperty("timestamp") Instant timestamp
) {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    public enum TaskStatus {
        SUCCESS, FAILURE, PARTIAL, TIMEOUT
    }

    public static AgentTaskOutput success(String taskId, String agentName,
                                          Map<String, Object> data, double confidence, long latencyMs) {
        return new AgentTaskOutput(taskId, agentName, TaskStatus.SUCCESS,
                data, confidence, latencyMs, List.of(), Instant.now());
    }

    public static AgentTaskOutput failure(String taskId, String agentName,
                                          List<String> errors, long latencyMs) {
        return new AgentTaskOutput(taskId, agentName, TaskStatus.FAILURE,
                Map.of(), 0.0, latencyMs, errors, Instant.now());
    }

    public String toJson() {
        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(this);
        } catch (Exception e) {
            return "{\"error\": \"serialization_failed\"}";
        }
    }

    public static AgentTaskOutput fromJson(String json) throws Exception {
        return MAPPER.readValue(json, AgentTaskOutput.class);
    }
}
