package com.tarsv2.schema;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.time.Instant;

/**
 * Structured output schema for change proposal results.
 * JSON-serializable via Jackson.
 */
public record ProposalResult(
        @JsonProperty("proposal_id") String proposalId,
        @JsonProperty("description") String description,
        @JsonProperty("diff") String diff,
        @JsonProperty("rationale") String rationale,
        @JsonProperty("status") String status,
        @JsonProperty("quality_score") double qualityScore,
        @JsonProperty("iterations") int iterations,
        @JsonProperty("created_at") Instant createdAt
) {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    public String toJson() {
        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(this);
        } catch (Exception e) {
            return "{\"error\": \"serialization_failed\"}";
        }
    }

    public static ProposalResult fromJson(String json) throws Exception {
        return MAPPER.readValue(json, ProposalResult.class);
    }
}
