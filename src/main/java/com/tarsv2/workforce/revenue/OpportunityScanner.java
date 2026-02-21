package com.tarsv2.workforce.revenue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tarsv2.model.ModelRequest;
import com.tarsv2.model.ModelResponse;
import com.tarsv2.model.router.ModelRouter;
import com.tarsv2.model.router.RoutingMode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class OpportunityScanner {

    private final ModelRouter modelRouter;
    private final ObjectMapper mapper = new ObjectMapper();

    public OpportunityScanner(ModelRouter modelRouter) {
        this.modelRouter = Objects.requireNonNull(modelRouter);
    }

    public List<Opportunity> scan(String domain) {
        ModelResponse response = modelRouter.route(RoutingMode.CHAT,
                new ModelRequest("You find opportunities.", "Identify opportunities in domain: " + domain + " as JSON array", true));
        if (response.status() != ModelResponse.Status.OK) {
            return List.of();
        }
        try {
            JsonNode root = mapper.readTree(response.content());
            List<Opportunity> opportunities = new ArrayList<>();
            for (JsonNode node : root) {
                opportunities.add(new Opportunity(UUID.randomUUID(), node.path("title").asText("Opportunity"),
                        node.path("description").asText(""), domain, OpportunityStatus.IDENTIFIED,
                        node.path("estimatedValue").asDouble(0.0d), node.path("estimatedCost").asDouble(0.0d), Instant.now(), Instant.now()));
            }
            return opportunities;
        } catch (Exception e) {
            return List.of();
        }
    }

    public Opportunity qualify(Opportunity opportunity) {
        ModelResponse response = modelRouter.route(RoutingMode.CHAT,
                new ModelRequest("You qualify opportunities.", "Qualify this opportunity as JSON: " + opportunity, true));
        if (response.status() != ModelResponse.Status.OK) {
            return opportunity;
        }
        try {
            JsonNode node = mapper.readTree(response.content());
            return new Opportunity(opportunity.id(), opportunity.title(), opportunity.description(), opportunity.source(), OpportunityStatus.QUALIFIED,
                    node.path("estimatedValue").asDouble(opportunity.estimatedValue()), node.path("estimatedCost").asDouble(opportunity.estimatedCost()),
                    opportunity.createdAt(), Instant.now());
        } catch (Exception e) {
            return opportunity;
        }
    }
}
