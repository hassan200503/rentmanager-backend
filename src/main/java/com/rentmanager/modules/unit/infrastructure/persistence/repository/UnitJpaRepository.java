package com.rentmanager.modules.unit.infrastructure.persistence.repository;

import com.rentmanager.modules.unit.domain.enums.UnitOccupancyStatus;
import com.rentmanager.modules.unit.domain.enums.UnitStatus;
import com.rentmanager.modules.unit.infrastructure.persistence.entity.UnitJpaEntity;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UnitJpaRepository extends JpaRepository<UnitJpaEntity, UUID> {

    // =====================================================
    // FIND BY ID + TENANT (STRICT ISOLATION)
    // =====================================================
    Optional<UnitJpaEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    // =====================================================
    // FIND ALL BY TENANT
    // =====================================================
    Page<UnitJpaEntity> findAllByTenantId(UUID tenantId, Pageable pageable);

    // =====================================================
    // EXISTS CHECK
    // =====================================================
    boolean existsByTenantIdAndUnitNumber(UUID tenantId, String unitNumber);

    // =====================================================
    // FIND BY PROPERTY (TENANT SCOPED)
    // =====================================================
    Page<UnitJpaEntity> findByTenantIdAndPropertyId(UUID tenantId, UUID propertyId, Pageable pageable);

    // =====================================================
    // FIND BY STATUS (TENANT SCOPED)
    // =====================================================
    Page<UnitJpaEntity> findByTenantIdAndStatus(UUID tenantId, UnitStatus status, Pageable pageable);

    // =====================================================
    // SEARCH (CUSTOM QUERY)
    // =====================================================
    @Query("""
        SELECT u FROM UnitJpaEntity u
        WHERE u.tenantId = :tenantId
        AND (
            LOWER(u.unitNumber) LIKE LOWER(CONCAT('%', :keyword, '%'))
            OR LOWER(u.label) LIKE LOWER(CONCAT('%', :keyword, '%'))
            OR LOWER(u.description) LIKE LOWER(CONCAT('%', :keyword, '%'))
        )
    """)
    Page<UnitJpaEntity> search(UUID tenantId, String keyword, Pageable pageable);



    Page<UnitJpaEntity> findByOccupancyStatus(
            UnitOccupancyStatus occupancyStatus,
            Pageable pageable
    );

    Optional<UnitJpaEntity> findById(UUID id);

    /**
     * Row-locking read for the reservation flow. Acquires a
     * PESSIMISTIC_WRITE lock on the unit row for the duration of the
     * caller's transaction, so two concurrent reservation attempts on the
     * same unit serialize instead of both observing VACANT.
     *
     * Uses an explicit @Query rather than a derived method name because
     * @Lock is not reliably honored on derived queries across Hibernate
     * versions — it needs a JPQL query to attach to.
     *
     * The lock timeout hint bounds how long a blocked second request waits
     * before failing with a PessimisticLockException, rather than hanging
     * indefinitely. Callers must keep the enclosing transaction short —
     * never hold this lock across an external HTTP call (e.g. the Daraja
     * STK push).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000")})
    @Query("SELECT u FROM UnitJpaEntity u WHERE u.id = :id")
    Optional<UnitJpaEntity> findByIdForUpdate(@Param("id") UUID id);

    Page<UnitJpaEntity> findByPropertyIdAndOccupancyStatus(
            UUID propertyId,
            UnitOccupancyStatus occupancyStatus,
            Pageable pageable
    );




    @Query("""
    SELECT u
    FROM UnitJpaEntity u
    WHERE u.occupancyStatus = :occupancyStatus
      AND (
            :keyword IS NULL
            OR LOWER(u.unitNumber) LIKE LOWER(CONCAT('%', :keyword, '%'))
            OR LOWER(u.description) LIKE LOWER(CONCAT('%', :keyword, '%'))
          )
""")
    Page<UnitJpaEntity> searchPublic(
            @Param("keyword") String keyword,
            @Param("occupancyStatus") UnitOccupancyStatus occupancyStatus,
            Pageable pageable
    );



    long countByTenantId(UUID tenantId);

    long countByTenantIdAndOccupancyStatus(UUID tenantId, UnitOccupancyStatus occupancyStatus);



    @Query("""
    SELECT u FROM UnitJpaEntity u
    WHERE u.occupancyStatus = :occupancyStatus
      AND u.vacatedAt IS NOT NULL
    ORDER BY u.vacatedAt ASC
""")
    Page<UnitJpaEntity> findLongestVacant(
            @Param("occupancyStatus") UnitOccupancyStatus occupancyStatus,
            Pageable pageable
    );
}