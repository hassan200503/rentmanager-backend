package com.rentmanager.modules.rentledger.infrastructure.persistence;

import com.rentmanager.modules.rentledger.domain.model.Disbursement;
import com.rentmanager.modules.rentledger.domain.repository.DisbursementRepository;
import com.rentmanager.modules.support.AbstractPostgresIntegrationTest;
import com.rentmanager.modules.support.MinimalTenantChainFixture;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.jpa.vendor.HibernateJpaDialect;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration test for V43__create_disbursements.sql's partial unique index.
 *
 * The index {@code uk_disbursements_originator_conversation_id} is defined as:
 *   CREATE UNIQUE INDEX ON disbursements (mpesa_originator_conversation_id)
 *   WHERE mpesa_originator_conversation_id IS NOT NULL;
 *
 * This test proves two properties that cannot be verified by unit tests
 * against a mocked repository:
 *   1. Multiple rows with NULL mpesa_originator_conversation_id do not collide.
 *   2. A duplicate non-NULL value is rejected by the constraint.
 */
class DisbursementMigrationIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private DisbursementRepository disbursementRepository;

    @Autowired
    private EntityManager entityManager;

    private UUID tenantId;
    private static final String OCID = "ocid-" + UUID.randomUUID();

    @BeforeEach
    void setUp() {
        tenantId = MinimalTenantChainFixture.persistTenant(entityManager);
    }

    /**
     * Flushes the persistence context and re-throws any constraint violation
     * translated into Spring's DataAccessException hierarchy. Mirrors the
     * identical helper in RentLedgerPersistenceIntegrationTest — see that
     * class's javadoc (PITFALL #1) for why HibernateJpaDialect is required
     * instead of EntityManagerFactoryUtils.
     */
    private void flushAndTranslate() {
        try {
            entityManager.flush();
        } catch (RuntimeException ex) {
            DataAccessException translated = new HibernateJpaDialect().translateExceptionIfPossible(ex);
            throw (translated != null) ? translated : ex;
        }
    }

    private Disbursement createDisbursement() {
        return Disbursement.create(
                tenantId,
                null, // leaseId — nullable; this test is only about the OCID unique index
                null, // ledgerEntryId — nullable, same reason
                new BigDecimal("5000.00"),
                "+254712345678",
                "Test Recipient",
                "BusinessPayment"
        );
    }

    @Test
    @Transactional
    void multipleNullOriginatorConversationIds_allowed() {
        Disbursement d1 = createDisbursement();
        Disbursement d2 = createDisbursement();
        Disbursement d3 = createDisbursement();

        disbursementRepository.save(d1);
        entityManager.flush();
        disbursementRepository.save(d2);
        entityManager.flush();
        disbursementRepository.save(d3);
        entityManager.flush();

        assertThat(d1.getMpesaOriginatorConversationId()).isNull();
        assertThat(d2.getMpesaOriginatorConversationId()).isNull();
        assertThat(d3.getMpesaOriginatorConversationId()).isNull();
    }

    @Test
    @Transactional
    void duplicateOriginatorConversationId_rejected() {
        Disbursement first = createDisbursement();
        first.markPending(OCID);
        disbursementRepository.save(first);
        entityManager.flush();

        Disbursement second = createDisbursement();
        second.markPending(OCID);

        assertThatThrownBy(() -> {
            disbursementRepository.save(second);
            flushAndTranslate();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }
}
