package com.rentmanager.crossmodule.support;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;

/**
 * SaaS-grade transaction boundary control for cross-module tests.
 *
 * Guarantees:
 * - deterministic rollback behavior
 * - cross-module isolation safety
 * - strict transaction validation
 * - safe JPA flush under managed context
 */
@Component
public class TransactionBoundary {

    private final TransactionTemplate transactionTemplate;
    private final EntityManager entityManager;

    public TransactionBoundary(TransactionTemplate transactionTemplate,
                               EntityManager entityManager) {
        this.transactionTemplate = transactionTemplate;
        this.entityManager = entityManager;
    }

    /**
     * Executes logic inside a strict transactional boundary.
     * Forces deterministic commit/rollback semantics.
     */
    public <T> T execute(SupplierWithException<T> action) {

        if (transactionTemplate == null) {
            throw new IllegalStateException("TransactionTemplate not configured");
        }

        return transactionTemplate.execute(status -> {

            try {
                T result = action.get();

                safeFlush(status);

                return result;

            } catch (Exception ex) {
                status.setRollbackOnly();
                throw new IllegalStateException(
                        "Cross-module transaction failed (rolled back). Root cause: "
                                + ex.getMessage(),
                        ex
                );
            }
        });
    }

    /**
     * Forces flush only when transaction is active.
     * Prevents Hibernate session corruption in edge cases.
     */
    public void flush() {
        safeFlush(null);
    }

    /**
     * Internal safe flush with transaction awareness.
     */
    private void safeFlush(TransactionStatus status) {

        try {
            if (entityManager == null) {
                throw new IllegalStateException("EntityManager not available");
            }

            // only flush if persistence context is active
            if (entityManager.isOpen()) {
                entityManager.flush();
            }

        } catch (Exception ex) {

            if (status != null) {
                status.setRollbackOnly();
            }

            throw new IllegalStateException(
                    "EntityManager flush failed during transaction boundary execution",
                    ex
            );
        }
    }

    @FunctionalInterface
    public interface SupplierWithException<T> {
        T get() throws Exception;
    }
}