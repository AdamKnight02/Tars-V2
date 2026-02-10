package com.tarsv2.schema;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.time.Instant;
import java.util.List;

/**
 * Structured output schema for sandbox test execution results.
 * JSON-serializable via Jackson.
 */
public record TestResult(
        @JsonProperty("test_id") String testId,
        @JsonProperty("proposal_id") String proposalId,
        @JsonProperty("passed") boolean passed,
        @JsonProperty("total_tests") int totalTests,
        @JsonProperty("passed_tests") int passedTests,
        @JsonProperty("failed_tests") int failedTests,
        @JsonProperty("test_details") List<TestDetail> testDetails,
        @JsonProperty("execution_ms") long executionMs,
        @JsonProperty("timestamp") Instant timestamp
) {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    public record TestDetail(
            @JsonProperty("name") String name,
            @JsonProperty("passed") boolean passed,
            @JsonProperty("message") String message
    ) {}

    public static TestResult allPassed(String testId, String proposalId,
                                        List<TestDetail> details, long executionMs) {
        return new TestResult(testId, proposalId, true,
                details.size(), details.size(), 0, details, executionMs, Instant.now());
    }

    public static TestResult withFailures(String testId, String proposalId,
                                           List<TestDetail> details, long executionMs) {
        int passed = (int) details.stream().filter(TestDetail::passed).count();
        int failed = details.size() - passed;
        return new TestResult(testId, proposalId, failed == 0,
                details.size(), passed, failed, details, executionMs, Instant.now());
    }

    public String toJson() {
        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(this);
        } catch (Exception e) {
            return "{\"error\": \"serialization_failed\"}";
        }
    }

    public static TestResult fromJson(String json) throws Exception {
        return MAPPER.readValue(json, TestResult.class);
    }
}
