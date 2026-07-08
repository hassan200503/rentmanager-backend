package com.rentmanager.modules.unit.infrastructure.persistence.repository;

import com.rentmanager.modules.property.domain.enums.PropertyStatus;
import com.rentmanager.modules.property.infrastructure.persistence.entity.PropertyJpaEntity;
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

    // NOTE: retained for backward compatibility — TODO confirm whether these
    // occupancy-only public methods still have callers anywhere before
    // removing them; the public query path now uses the *PubliclyVisible*
    // methods below, which additionally enforce UnitStatus.ACTIVE and the
    // parent Property's PropertyStatus.ACTIVE (see RentManager Public
    // Listings Hardening handoff, 2026-07-08).
    //
    // FLAGGED, NOT FIXED: this method's sibling below (searchPublic) has the
    // exact same null-keyword-vs-LOWER(CONCAT) type-inference bug that broke
    // searchPubliclyVisible (see fix there). It was never hit in production
    // because the old PublicUnitQueryServiceImpl branched around a null
    // keyword before ever calling searchPublic. If this method gains a new
    // caller that passes a null keyword directly, it will fail the same way.
    // Left untouched here since it's pre-existing code outside this task's
    // scope — flagging per the "no drift" rule rather than fixing unprompted.
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

    // NOTE: retained — see comment on findByOccupancyStatus above.
    Page<UnitJpaEntity> findByPropertyIdAndOccupancyStatus(
            UUID propertyId,
            UnitOccupancyStatus occupancyStatus,
            Pageable pageable
    );

    // NOTE: retained — see comment on findByOccupancyStatus above regarding
    // the same latent null-keyword bug in this method's WHERE clause.
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

    // NOTE: retained — see comment on findByOccupancyStatus above.
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

    // =====================================================
    // PUBLIC LISTING HARDENING (2026-07-08)
    //
    // UnitJpaEntity.propertyId is a plain UUID column with no @ManyToOne
    // mapping to PropertyJpaEntity, so the parent property's status cannot
    // be reached via relationship traversal. These queries use an explicit
    // ad-hoc JPQL join (JOIN PropertyJpaEntity p ON u.propertyId = p.id)
    // instead, matching the @Query-JPQL style already used elsewhere in
    // this interface (see searchPublic, findLongestVacant above) rather
    // than introducing a two-step "fetch active property IDs then filter
    // units" pattern.
    //
    // A unit is publicly visible only if ALL of the following hold:
    //   - u.status = UnitStatus.ACTIVE
    //   - u.occupancyStatus = UnitOccupancyStatus.VACANT
    //   - the parent property's status = PropertyStatus.ACTIVE
    // This is deliberate defense-in-depth, not something to simplify to a
    // single check — see handoff doc §4.
    // =====================================================

    // FIX (found via integration test failure, 2026-07-08): a null keyword
    // bound into LOWER(CONCAT('%', :keyword, '%')) causes Postgres's JDBC
    // driver to mis-infer the parameter type as `bytea` instead of text,
    // producing "ERROR: function lower(bytea) does not exist". This was
    // masked in the old service code by branching around a null keyword
    // before it ever reached a query; that branch was removed when this
    // method was introduced, exposing the bug. Fixed here with an explicit
    // CAST(:keyword AS string), which gives Postgres an unambiguous type
    // and avoids reintroducing service-layer branching.
    @Query("""
        SELECT u FROM UnitJpaEntity u
        JOIN PropertyJpaEntity p ON u.propertyId = p.id
        WHERE u.occupancyStatus = :occupancyStatus
          AND u.status = :unitStatus
          AND p.status = :propertyStatus
          AND (
                :keyword IS NULL
                OR LOWER(u.unitNumber) LIKE LOWER(CONCAT('%', CAST(:keyword AS string), '%'))
                OR LOWER(u.description) LIKE LOWER(CONCAT('%', CAST(:keyword AS string), '%'))
              )
    """)
    Page<UnitJpaEntity> searchPubliclyVisible(
            @Param("keyword") String keyword,
            @Param("occupancyStatus") UnitOccupancyStatus occupancyStatus,
            @Param("unitStatus") UnitStatus unitStatus,
            @Param("propertyStatus") PropertyStatus propertyStatus,
            Pageable pageable
    );

    @Query("""
        SELECT u FROM UnitJpaEntity u
        JOIN PropertyJpaEntity p ON u.propertyId = p.id
        WHERE u.propertyId = :propertyId
          AND u.occupancyStatus = :occupancyStatus
          AND u.status = :unitStatus
          AND p.status = :propertyStatus
    """)
    Page<UnitJpaEntity> findPubliclyVisibleByProperty(
            @Param("propertyId") UUID propertyId,
            @Param("occupancyStatus") UnitOccupancyStatus occupancyStatus,
            @Param("unitStatus") UnitStatus unitStatus,
            @Param("propertyStatus") PropertyStatus propertyStatus,
            Pageable pageable
    );

    @Query("""
        SELECT u FROM UnitJpaEntity u
        JOIN PropertyJpaEntity p ON u.propertyId = p.id
        WHERE u.id = :id
          AND u.occupancyStatus = :occupancyStatus
          AND u.status = :unitStatus
          AND p.status = :propertyStatus
    """)
    Optional<UnitJpaEntity> findPubliclyVisibleById(
            @Param("id") UUID id,
            @Param("occupancyStatus") UnitOccupancyStatus occupancyStatus,
            @Param("unitStatus") UnitStatus unitStatus,
            @Param("propertyStatus") PropertyStatus propertyStatus
    );

    @Query("""
        SELECT u FROM UnitJpaEntity u
        JOIN PropertyJpaEntity p ON u.propertyId = p.id
        WHERE u.occupancyStatus = :occupancyStatus
          AND u.status = :unitStatus
          AND p.status = :propertyStatus
          AND u.vacatedAt IS NOT NULL
        ORDER BY u.vacatedAt ASC
    """)
    Page<UnitJpaEntity> findLongestVacantPubliclyVisible(
            @Param("occupancyStatus") UnitOccupancyStatus occupancyStatus,
            @Param("unitStatus") UnitStatus unitStatus,
            @Param("propertyStatus") PropertyStatus propertyStatus,
            Pageable pageable
    );
}