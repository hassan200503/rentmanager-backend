package com.rentmanager.crossmodule.support;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * SaaS-grade database cleanup utility for cross-module integration tests.
 *
 * Guarantees:
 * - deterministic test isolation
 * - FK-safe reset strategy
 * - fast execution using TRUNCATE
 * - sequence reset for reproducible IDs
 */
@Component
public class DatabaseCleaner {

    private final EntityManager em;

    public DatabaseCleaner(EntityManager em) {
        this.em = em;
    }

    /**
     * Fully resets database state between integration tests.
     *
     * Uses TRUNCATE CASCADE to ensure:
     * - foreign key safety
     * - fast execution
     * - sequence reset consistency
     */
    @Transactional
    public void cleanAll() {

        // IMPORTANT: order becomes irrelevant with CASCADE,
        // but we still keep logical grouping for readability

        em.createNativeQuery("""
                TRUNCATE TABLE 
                    leases,
                    units,
                    properties,
                    tenants
                RESTART IDENTITY CASCADE
                """).executeUpdate();
    }
}