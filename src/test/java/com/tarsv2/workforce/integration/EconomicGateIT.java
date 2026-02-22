package com.tarsv2.workforce.integration;

import com.tarsv2.workforce.economics.CostLedger;
import com.tarsv2.workforce.persistence.DatabaseManager;
import com.tarsv2.workforce.persistence.h2.H2LedgerRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EconomicGateIT {

    @Test
    void costLedgerRoundTrip() {
        DatabaseManager db = new DatabaseManager();
        db.initialize();
        H2LedgerRepository repo = new H2LedgerRepository(db.getDataSource());

        CostLedger entry = new CostLedger(UUID.randomUUID(), UUID.randomUUID(), "MiniMax-M2.5", "MiniMax",
                5000, 2000, 1, 0.003, Instant.now());
        repo.save(entry);

        double total = repo.totalCost();
        assertTrue(total > 0.0);
    }
}
