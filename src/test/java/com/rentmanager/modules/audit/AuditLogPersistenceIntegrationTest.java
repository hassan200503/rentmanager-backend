package com.rentmanager.modules.audit;

import com.rentmanager.modules.audit.domain.enums.AuditAction;
import com.rentmanager.modules.audit.domain.model.AuditLog;
import com.rentmanager.modules.audit.domain.repository.AuditLogRepository;
import com.rentmanager.modules.support.AbstractPostgresIntegrationTest;
import com.rentmanager.modules.support.MinimalTenantChainFixture;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves the audit trail actually reaches PostgreSQL.
 *
 * <p>This is not a formality. The previous implementation was:
 *
 * <pre>
 *     public AuditLog save(AuditLog auditLog) {
 *         // JPA implementation later
 *         return auditLog;
 *     }
 * </pre>
 *
 * <p>Every unit test that mocked {@code AuditLogRepository} would have passed
 * against it, because it satisfied the interface perfectly and returned what
 * it was given. Only a test that reads the row back from a real database can
 * tell the difference between an audit trail and the appearance of one.
 */
@Transactional
class AuditLogPersistenceIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private EntityManager entityManager;

    private UUID tenantId;

    @BeforeEach
    void setUp() {
        tenantId = MinimalTenantChainFixture.persistTenant(entityManager);
        entityManager.flush();
    }

    private AuditLog log(AuditAction action, String entityId, String correlationId) {
        return new AuditLog(
                tenantId,
                action.name(),
                "user_clerk_abc123",
                "ROLE_LANDLORD_OWNER",
                "DISBURSEMENT",
                entityId,
                correlationId,
                "SUCCESS",
                "{\"amount\":\"14250.00\"}",
                "196.201.214.1",
                "Mozilla/5.0"
        );
    }

    @Test
    void anAuditRecordIsActuallyReadableBackFromTheDatabase() {
        AuditLog saved = auditLogRepository.save(
                log(AuditAction.DISBURSEMENT_INITIATED, "d-1", "corr-1"));
        entityManager.flush();
        entityManager.clear();

        List<AuditLog> found = auditLogRepository.findByTenantId(tenantId);

        assertThat(found).hasSize(1);
        assertThat(found.get(0).getId()).isEqualTo(saved.getId());
        assertThat(found.get(0).getAction())
                .isEqualTo(AuditAction.DISBURSEMENT_INITIATED.name());
    }

    /**
     * The question a dispute actually asks. Every one of these was
     * unanswerable before, because nothing was written.
     */
    @Test
    void everyFieldNeededToAnswerWhoDidThisSurvivesTheRoundTrip() {
        auditLogRepository.save(log(AuditAction.DISBURSEMENT_INITIATED, "d-2", "corr-2"));
        entityManager.flush();
        entityManager.clear();

        AuditLog found = auditLogRepository.findByTenantId(tenantId).get(0);

        assertThat(found.getActorId()).isEqualTo("user_clerk_abc123");
        assertThat(found.getActorType()).isEqualTo("ROLE_LANDLORD_OWNER");
        assertThat(found.getEntityType()).isEqualTo("DISBURSEMENT");
        assertThat(found.getEntityId()).isEqualTo("d-2");
        assertThat(found.getCorrelationId()).isEqualTo("corr-2");
        assertThat(found.getStatus()).isEqualTo("SUCCESS");
        assertThat(found.getMetadata()).contains("14250.00");
        assertThat(found.getIpAddress()).isEqualTo("196.201.214.1");
        assertThat(found.getCreatedAt()).isNotNull();
    }

    @Test
    void correlationIdTiesAuditRecordsBackToTheMoneyThatCausedThem() {
        auditLogRepository.save(log(AuditAction.PAYMENT_RECORDED, "t-1", "rent-payment-9"));
        auditLogRepository.save(log(AuditAction.DISBURSEMENT_INITIATED, "d-3", "rent-payment-9"));
        auditLogRepository.save(log(AuditAction.DISBURSEMENT_INITIATED, "d-4", "unrelated"));
        entityManager.flush();
        entityManager.clear();

        assertThat(auditLogRepository.findByCorrelationId("rent-payment-9")).hasSize(2);
    }

    /**
     * A record of who authorised a payout is worth nothing if the person who
     * authorised it can edit the row afterwards.
     */
    @Test
    void anAuditRecordCannotBeUpdated() {
        auditLogRepository.save(log(AuditAction.DISBURSEMENT_INITIATED, "d-5", "corr-5"));
        entityManager.flush();

        assertThatThrownBy(() -> {
            entityManager.createNativeQuery(
                            "UPDATE audit_logs SET actor_id = 'someone_else' WHERE tenant_id = :t")
                    .setParameter("t", tenantId)
                    .executeUpdate();
            entityManager.flush();
        }).hasStackTraceContaining("append-only");
    }

    @Test
    void anAuditRecordCannotBeDeleted() {
        auditLogRepository.save(log(AuditAction.DISBURSEMENT_INITIATED, "d-6", "corr-6"));
        entityManager.flush();

        assertThatThrownBy(() -> {
            entityManager.createNativeQuery("DELETE FROM audit_logs WHERE tenant_id = :t")
                    .setParameter("t", tenantId)
                    .executeUpdate();
            entityManager.flush();
        }).hasStackTraceContaining("append-only");
    }

    /**
     * Platform-level actions belong to no landlord organisation, so tenant_id
     * has to accept null — unlike every other tenant-owned table.
     */
    @Test
    void platformLevelActionsCanBeRecordedWithoutATenant() {
        auditLogRepository.save(new AuditLog(
                null, AuditAction.SYSTEM_EVENT.name(), "SYSTEM", "SYSTEM",
                "PLATFORM_SETTINGS", "commission-default", null, "SUCCESS",
                "{}", null, null));
        entityManager.flush();
        entityManager.clear();

        assertThat(auditLogRepository.findByTenantId(tenantId))
                .as("a platform action must not appear under a landlord's history")
                .isEmpty();
    }

    @Test
    void statusIsConstrainedToTheTwoValuesTheColumnAllows() {
        assertThatThrownBy(() -> {
            entityManager.createNativeQuery(
                            "INSERT INTO audit_logs (id, tenant_id, action, actor_id, actor_type, "
                                    + "status, created_at) "
                                    + "VALUES (:id, :t, 'SYSTEM_EVENT', 'x', 'SYSTEM', 'MAYBE', NOW())")
                    .setParameter("id", UUID.randomUUID())
                    .setParameter("t", tenantId)
                    .executeUpdate();
            entityManager.flush();
        }).isInstanceOf(Exception.class);
    }
}
