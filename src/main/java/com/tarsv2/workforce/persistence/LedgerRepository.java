package com.tarsv2.workforce.persistence;

import com.tarsv2.workforce.economics.CostLedger;
import com.tarsv2.workforce.economics.RevenueLedger;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LedgerRepository {
    void save(CostLedger entity);

    void save(RevenueLedger entity);

    Optional<CostLedger> findCostById(UUID id);

    Optional<RevenueLedger> findRevenueById(UUID id);

    List<CostLedger> findAllCosts();

    List<RevenueLedger> findAllRevenue();

    List<CostLedger> findByTaskId(UUID taskId);

    List<RevenueLedger> findByTaskId(UUID taskId);

    void update(CostLedger entity);

    void update(RevenueLedger entity);

    void deleteCost(UUID id);

    void deleteRevenue(UUID id);

    double totalCost();

    double totalRevenue();
}
