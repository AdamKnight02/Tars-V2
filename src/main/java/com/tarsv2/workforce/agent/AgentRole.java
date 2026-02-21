package com.tarsv2.workforce.agent;

import com.tarsv2.model.router.RoutingMode;

public enum AgentRole {
    ENGINEER(RoutingMode.CODEX),
    RESEARCHER(RoutingMode.RESEARCH),
    SALES(RoutingMode.CHAT),
    FINANCE(RoutingMode.RESEARCH);

    private final RoutingMode routingMode;

    AgentRole(RoutingMode routingMode) {
        this.routingMode = routingMode;
    }

    public RoutingMode routingMode() {
        return routingMode;
    }
}
