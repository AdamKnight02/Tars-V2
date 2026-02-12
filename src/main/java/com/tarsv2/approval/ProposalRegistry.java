package com.tarsv2.approval;

import com.tarsv2.codex.PatchProposal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ProposalRegistry {

    private static final Logger log = LoggerFactory.getLogger(ProposalRegistry.class);

    private final Map<UUID, PatchProposal> proposals = new ConcurrentHashMap<>();
    private final Map<UUID, List<String>> executionLogs = new ConcurrentHashMap<>();
    private final Map<UUID, List<String>> referencedFiles = new ConcurrentHashMap<>();
    private final Map<UUID, String> validationResults = new ConcurrentHashMap<>();
    private final List<String> auditLog = java.util.Collections.synchronizedList(new ArrayList<>());

    public UUID submit(PatchProposal proposal, List<String> files, String validationResult) {
        proposals.put(proposal.getId(), proposal);
        referencedFiles.put(proposal.getId(), files == null ? List.of() : List.copyOf(files));
        validationResults.put(proposal.getId(), validationResult == null ? "PENDING" : validationResult);
        logEvent("PROPOSAL_SUBMITTED", proposal.getId(), proposal.getDescription());
        return proposal.getId();
    }

    public Optional<PatchProposal> get(UUID id) {
        return Optional.ofNullable(proposals.get(id));
    }

    public List<PatchProposal> getAll() {
        return proposals.values().stream().sorted(java.util.Comparator.comparing(PatchProposal::getCreatedAt).reversed()).toList();
    }

    public List<PatchProposal> getPending() {
        return proposals.values().stream()
                .filter(p -> p.getStatus() == PatchProposal.Status.PENDING)
                .sorted(java.util.Comparator.comparing(PatchProposal::getCreatedAt).reversed())
                .toList();
    }

    public boolean approve(UUID id) {
        PatchProposal proposal = proposals.get(id);
        if (proposal == null || proposal.getStatus() != PatchProposal.Status.PENDING) {
            return false;
        }
        proposal.setStatus(PatchProposal.Status.APPROVED);
        logEvent("PROPOSAL_APPROVED", id, "Approved by human");
        return true;
    }

    public boolean reject(UUID id) {
        PatchProposal proposal = proposals.get(id);
        if (proposal == null || proposal.getStatus() != PatchProposal.Status.PENDING) {
            return false;
        }
        proposal.setStatus(PatchProposal.Status.REJECTED);
        logEvent("PROPOSAL_REJECTED", id, "Rejected by human");
        return true;
    }

    public void updateStatus(UUID id, PatchProposal.Status status, String detail) {
        PatchProposal proposal = proposals.get(id);
        if (proposal == null) {
            return;
        }
        proposal.setStatus(status);
        appendExecutionLog(id, status + " - " + detail);
        logEvent("PROPOSAL_STATUS", id, status + " - " + detail);
    }

    public void appendExecutionLog(UUID id, String message) {
        executionLogs.computeIfAbsent(id, ignored -> java.util.Collections.synchronizedList(new ArrayList<>()))
                .add(Instant.now() + " | " + message);
    }

    public List<String> getExecutionLogs(UUID id) {
        return executionLogs.getOrDefault(id, List.of());
    }

    public List<String> getReferencedFiles(UUID id) {
        return referencedFiles.getOrDefault(id, List.of());
    }

    public String getValidationResult(UUID id) {
        return validationResults.getOrDefault(id, "UNKNOWN");
    }

    public void setValidationResult(UUID id, String result) {
        validationResults.put(id, result);
    }

    public List<String> getAuditLog() {
        synchronized (auditLog) {
            return List.copyOf(auditLog);
        }
    }

    private void logEvent(String type, UUID id, String message) {
        String entry = Instant.now() + " | " + type + " | " + id + " | " + message;
        auditLog.add(entry);
        log.info(entry);
    }
}
