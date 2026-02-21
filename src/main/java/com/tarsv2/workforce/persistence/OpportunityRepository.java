package com.tarsv2.workforce.persistence;

import com.tarsv2.workforce.revenue.Opportunity;
import com.tarsv2.workforce.revenue.OpportunityStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OpportunityRepository {
    void save(Opportunity entity);

    Optional<Opportunity> findById(UUID id);

    List<Opportunity> findAll();

    List<Opportunity> findByStatus(OpportunityStatus status);

    void update(Opportunity entity);

    void delete(UUID id);
}
