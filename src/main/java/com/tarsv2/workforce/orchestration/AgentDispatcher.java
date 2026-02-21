package com.tarsv2.workforce.orchestration;

import com.tarsv2.workforce.agent.AgentRole;
import com.tarsv2.workforce.agent.WorkforceAgent;
import com.tarsv2.workforce.task.Task;
import com.tarsv2.workforce.task.TaskResult;

import java.time.Instant;
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

    public TaskResult dispatch(Task task) {
        return findAgent(task)
                .map(a -> a.execute(task))
                .orElseGet(() -> new TaskResult(task.id(), false, "", "No capable agent", Instant.now(), 0, 0, "none"));
    }

    public int size() {
        return agents.size();
    }
}
