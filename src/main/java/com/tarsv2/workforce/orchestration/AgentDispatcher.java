package com.tarsv2.workforce.orchestration;

import com.tarsv2.openclaw.Intent;
import com.tarsv2.workforce.agent.AgentRole;
import com.tarsv2.workforce.agent.WorkforceAgent;
import com.tarsv2.workforce.task.Task;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class AgentDispatcher {

    private final Map<AgentRole, WorkforceAgent> agents = new ConcurrentHashMap<>();

    public void register(WorkforceAgent agent) {
        agents.put(agent.getRole(), agent);
    }

    public Optional<WorkforceAgent> findAgent(Task task) {
        WorkforceAgent agent = agents.get(task.assignedRole());
        if (agent == null || !agent.canHandle(task)) {
            return Optional.empty();
        }
        return Optional.of(agent);
    }

    public List<Intent> plan(Task task) {
        return findAgent(task)
                .map(a -> a.plan(task))
                .orElse(List.of());
    }

    public int size() {
        return agents.size();
    }
}
