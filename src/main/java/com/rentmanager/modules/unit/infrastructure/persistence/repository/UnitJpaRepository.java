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

import java.util.List;
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

    /**
     * Occupied and total unit counts per property, for one landlord, in a
     * single grouped query.
     *
     * <p>Returns raw rows — {@code [propertyId, totalUnits, occupiedUnits]} —
     * rather than a JPQL constructor expression. A {@code new ...} expression
     * has to match a constructor by type, and {@code COUNT}/{@code SUM} yield
     * {@code Long} while the projection record takes primitives; getting that
     * wrong fails at context load rather than at the call site, which is a
     * needlessly sharp edge for a read query. The adapter does the mapping.
     *
     * <p>ARCHIVED units are excluded from both figures, matching
     * {@code PropertyOccupancyRollupListener}, which excludes them when it
     * derives a property's OccupancyStatus. If the two disagreed, a property
     * could read "fully occupied" beside counts that said otherwise.
     *
     * <p>One query for the whole portfolio rather than one per property: the
     * dashboard renders every property at once.
     */
    @Query("""
            SELECT u.propertyId,
                   COUNT(u.id),
                   SUM(CASE WHEN u.occupancyStatus = com.rentmanager.modules.unit.domain.enums.UnitOccupancyStatus.OCCUPIED
                            THEN 1 ELSE 0 END)
            FROM UnitJpaEntity u
            WHERE u.tenantId = :tenantId
              AND u.status <> com.rentmanager.modules.unit.domain.enums.UnitStatus.ARCHIVED
            GROUP BY u.propertyId
            """)
    List<Object[]> countUnitsByPropertyRaw(@Param("tenantId") UUID tenantId);

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
    /**
     * The search a renter uses to find somewhere to live.
     *
     * <h2>What it used to match, and why that was the problem</h2>
     * Only {@code u.unitNumber} and {@code u.description}. A unit number is
     * "A101" — meaningless to somebody looking for a home — which left the
     * free-text description as the only real search surface. There was no
     * location match at all, so a renter typing "Kilimani" found nothing
     * unless a landlord happened to have typed that word into a description,
     * and no way whatsoever to filter by price.
     *
     * <p>Location and budget are the first two questions any renter asks. The
     * data to answer both was already here — {@code p.address.city},
     * {@code p.address.state} and {@code u.rentAmount} — and simply unused.
     *
     * <h2>Filters</h2>
     * Every filter is null-tolerant: a null means "no constraint", so the
     * same query serves an unfiltered browse and a fully specified search.
     *
     * <p>{@code propertyType} stands in for bedroom count. No bedroom column
     * exists, and in this market BEDSITTER / STUDIO / APARTMENT / MAISONETTE
     * is how supply is actually described — inventing a bedroom number from a
     * type would be guessing at data nobody entered.
     *
     * <h2>The CAST is load-bearing</h2>
     * Every string parameter is wrapped in {@code CAST(... AS string)} for the
     * reason documented above: a null bound into {@code LOWER(CONCAT(...))}
     * makes the Postgres driver infer {@code bytea} and fail with
     * "function lower(bytea) does not exist". That bug was found by an
     * integration test once already; each new string filter here would
     * reintroduce it without the cast.
     */
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
                OR LOWER(u.label) LIKE LOWER(CONCAT('%', CAST(:keyword AS string), '%'))
                OR LOWER(p.name) LIKE LOWER(CONCAT('%', CAST(:keyword AS string), '%'))
                OR LOWER(p.address.city) LIKE LOWER(CONCAT('%', CAST(:keyword AS string), '%'))
                OR LOWER(p.address.state) LIKE LOWER(CONCAT('%', CAST(:keyword AS string), '%'))
              )
          AND (
                :city IS NULL
                OR LOWER(p.address.city) LIKE LOWER(CONCAT('%', CAST(:city AS string), '%'))
                OR LOWER(p.address.state) LIKE LOWER(CONCAT('%', CAST(:city AS string), '%'))
              )
          AND (:minRent IS NULL OR u.rentAmount >= :minRent)
          AND (:maxRent IS NULL OR u.rentAmount <= :maxRent)
          AND (:propertyType IS NULL OR p.propertyType = :propertyType)
    """)
    Page<UnitJpaEntity> searchPubliclyVisible(
            @Param("keyword") String keyword,
            @Param("city") String city,
            @Param("minRent") java.math.BigDecimal minRent,
            @Param("maxRent") java.math.BigDecimal maxRent,
            @Param("propertyType") com.rentmanager.modules.property.domain.enums.PropertyType propertyType,
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

    // =====================================================
    // PUBLIC LISTINGS: VACANCY-BACKED PROPERTY SEARCH
    // =====================================================

    /**
     * Ids of properties that actually have somewhere to rent.
     *
     * <p>Answers {@code PublicVacancyPort} for the property module. The public
     * listings page previously ran {@code findByStatus(ACTIVE)}, which listed
     * fully-occupied properties beside genuinely available ones under the
     * heading "Available Properties". Requiring a matching vacant unit here
     * makes the page's promise true in the query rather than in the copy.
     *
     * <p>Ordered by property name because the page is paged: an unordered
     * Postgres result can return the same row on two different pages and drop
     * another entirely, which reads to a renter as listings that flicker in
     * and out as they browse.
     *
     * <p>The {@code CAST(... AS string)} on every string parameter is
     * load-bearing for the reason documented on {@code searchPubliclyVisible}:
     * a null bound into {@code LOWER(CONCAT(...))} makes the Postgres driver
     * infer {@code bytea} and fail at runtime.
     */
    @Query(value = """
        SELECT p.id FROM PropertyJpaEntity p
        WHERE p.status = :propertyStatus
          AND (:propertyType IS NULL OR p.propertyType = :propertyType)
          AND (
                :keyword IS NULL
                OR LOWER(p.name) LIKE LOWER(CONCAT('%', CAST(:keyword AS string), '%'))
                OR LOWER(p.address.city) LIKE LOWER(CONCAT('%', CAST(:keyword AS string), '%'))
                OR LOWER(p.address.state) LIKE LOWER(CONCAT('%', CAST(:keyword AS string), '%'))
                OR LOWER(p.address.addressLine1) LIKE LOWER(CONCAT('%', CAST(:keyword AS string), '%'))
                OR LOWER(p.address.addressLine2) LIKE LOWER(CONCAT('%', CAST(:keyword AS string), '%'))
              )
          AND (
                :location IS NULL
                OR LOWER(p.address.city) LIKE LOWER(CONCAT('%', CAST(:location AS string), '%'))
                OR LOWER(p.address.state) LIKE LOWER(CONCAT('%', CAST(:location AS string), '%'))
                OR LOWER(p.address.addressLine1) LIKE LOWER(CONCAT('%', CAST(:location AS string), '%'))
                OR LOWER(p.address.addressLine2) LIKE LOWER(CONCAT('%', CAST(:location AS string), '%'))
              )
          AND EXISTS (
                SELECT 1 FROM UnitJpaEntity u
                WHERE u.propertyId = p.id
                  AND u.occupancyStatus = :occupancyStatus
                  AND u.status = :unitStatus
                  AND (:minRent IS NULL OR u.rentAmount >= :minRent)
                  AND (:maxRent IS NULL OR u.rentAmount <= :maxRent)
              )
        ORDER BY p.name ASC
    """,
            countQuery = """
        SELECT COUNT(p.id) FROM PropertyJpaEntity p
        WHERE p.status = :propertyStatus
          AND (:propertyType IS NULL OR p.propertyType = :propertyType)
          AND (
                :keyword IS NULL
                OR LOWER(p.name) LIKE LOWER(CONCAT('%', CAST(:keyword AS string), '%'))
                OR LOWER(p.address.city) LIKE LOWER(CONCAT('%', CAST(:keyword AS string), '%'))
                OR LOWER(p.address.state) LIKE LOWER(CONCAT('%', CAST(:keyword AS string), '%'))
                OR LOWER(p.address.addressLine1) LIKE LOWER(CONCAT('%', CAST(:keyword AS string), '%'))
                OR LOWER(p.address.addressLine2) LIKE LOWER(CONCAT('%', CAST(:keyword AS string), '%'))
              )
          AND (
                :location IS NULL
                OR LOWER(p.address.city) LIKE LOWER(CONCAT('%', CAST(:location AS string), '%'))
                OR LOWER(p.address.state) LIKE LOWER(CONCAT('%', CAST(:location AS string), '%'))
                OR LOWER(p.address.addressLine1) LIKE LOWER(CONCAT('%', CAST(:location AS string), '%'))
                OR LOWER(p.address.addressLine2) LIKE LOWER(CONCAT('%', CAST(:location AS string), '%'))
              )
          AND EXISTS (
                SELECT 1 FROM UnitJpaEntity u
                WHERE u.propertyId = p.id
                  AND u.occupancyStatus = :occupancyStatus
                  AND u.status = :unitStatus
                  AND (:minRent IS NULL OR u.rentAmount >= :minRent)
                  AND (:maxRent IS NULL OR u.rentAmount <= :maxRent)
              )
    """)
    Page<UUID> searchPropertyIdsWithVacancy(
            @Param("keyword") String keyword,
            @Param("location") String location,
            @Param("minRent") java.math.BigDecimal minRent,
            @Param("maxRent") java.math.BigDecimal maxRent,
            @Param("propertyType") com.rentmanager.modules.property.domain.enums.PropertyType propertyType,
            @Param("occupancyStatus") UnitOccupancyStatus occupancyStatus,
            @Param("unitStatus") UnitStatus unitStatus,
            @Param("propertyStatus") PropertyStatus propertyStatus,
            Pageable pageable
    );

    /**
     * How many units are available in each property, and the asking-price
     * range across them — one grouped query for a whole page of cards.
     *
     * <p>Deliberately does not join properties: callers pass ids that came
     * from a query which already enforced {@code PropertyStatus.ACTIVE}, and
     * re-checking here would only hide a caller that had not.
     */
    @Query("""
        SELECT u.propertyId AS propertyId,
               COUNT(u.id) AS availableUnits,
               MIN(u.rentAmount) AS minRent,
               MAX(u.rentAmount) AS maxRent
        FROM UnitJpaEntity u
        WHERE u.propertyId IN :propertyIds
          AND u.occupancyStatus = :occupancyStatus
          AND u.status = :unitStatus
        GROUP BY u.propertyId
    """)
    List<VacancySummaryRow> summariseVacancy(
            @Param("propertyIds") List<UUID> propertyIds,
            @Param("occupancyStatus") UnitOccupancyStatus occupancyStatus,
            @Param("unitStatus") UnitStatus unitStatus
    );

    /** Projection for {@link #summariseVacancy}. */
    interface VacancySummaryRow {
        UUID getPropertyId();

        long getAvailableUnits();

        java.math.BigDecimal getMinRent();

        java.math.BigDecimal getMaxRent();
    }
}
