package com.rentmanager.modules.lease.infrastructure.persistence.repository;

import com.rentmanager.modules.lease.infrastructure.persistence.entity.LeaseEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Custom query implementation (optimized SQL/JPA)
 */
@Repository
public class LeaseQueryRepositoryImpl implements LeaseQueryRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public List<LeaseEntity> findActiveLeases(UUID tenantId) {
        return entityManager.createQuery("""
                SELECT l FROM LeaseEntity l
                WHERE l.tenantId = :tenantId
                AND l.status = 'ACTIVE'
                """, LeaseEntity.class)
                .setParameter("tenantId", tenantId)
                .getResultList();
    }

    @Override
    public List<LeaseEntity> findExpiredLeases(UUID tenantId) {
        return entityManager.createQuery("""
                SELECT l FROM LeaseEntity l
                WHERE l.tenantId = :tenantId
                AND l.endDate < CURRENT_DATE
                """, LeaseEntity.class)
                .setParameter("tenantId", tenantId)
                .getResultList();
    }

    @Override
    public List<LeaseEntity> findLeasesByProperty(UUID tenantId, UUID propertyId) {
        return entityManager.createQuery("""
                SELECT l FROM LeaseEntity l
                WHERE l.tenantId = :tenantId
                AND l.propertyId = :propertyId
                """, LeaseEntity.class)
                .setParameter("tenantId", tenantId)
                .setParameter("propertyId", propertyId)
                .getResultList();
    }

    @Override
    public List<LeaseEntity> findLeasesEndingBefore(UUID tenantId, LocalDate date) {
        return entityManager.createQuery("""
                SELECT l FROM LeaseEntity l
                WHERE l.tenantId = :tenantId
                AND l.endDate <= :date
                """, LeaseEntity.class)
                .setParameter("tenantId", tenantId)
                .setParameter("date", date)
                .getResultList();
    }
}