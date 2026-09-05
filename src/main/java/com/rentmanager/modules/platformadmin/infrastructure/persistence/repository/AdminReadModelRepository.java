package com.rentmanager.modules.platformadmin.infrastructure.persistence.repository;

import com.rentmanager.modules.lease.domain.enums.LeaseStatus;
import com.rentmanager.modules.lease.infrastructure.persistence.entity.LeaseEntity;
import com.rentmanager.modules.platformadmin.infrastructure.persistence.projection.DisbursementStatusCount;
import com.rentmanager.modules.platformadmin.infrastructure.persistence.projection.PaymentRequestStatusCount;
import com.rentmanager.modules.platformadmin.infrastructure.persistence.projection.TenantIdCount;
import com.rentmanager.modules.platformadmin.infrastructure.persistence.projection.TenantIdLastActivity;
import com.rentmanager.modules.platformadmin.infrastructure.persistence.projection.TenantIdMoney;
import com.rentmanager.modules.platformadmin.infrastructure.persistence.projection.TenantStatusCount;
import com.rentmanager.modules.property.infrastructure.persistence.entity.PropertyJpaEntity;
import com.rentmanager.modules.rentledger.domain.enums.DisbursementStatus;
import com.rentmanager.modules.rentledger.domain.enums.RentPaymentRequestStatus;
import com.rentmanager.modules.rentledger.infrastructure.persistence.entity.DisbursementJpaEntity;
import com.rentmanager.modules.rentledger.infrastructure.persistence.entity.RentPaymentRequestJpaEntity;
import com.rentmanager.modules.rentledger.infrastructure.persistence.entity.RentTransactionJpaEntity;
import com.rentmanager.modules.tenant.infrastructure.persistence.entity.TenantEntity;
import com.rentmanager.modules.tenant.renter.infrastructure.persistence.entity.TenantProfileEntity;
import com.rentmanager.modules.unit.infrastructure.persistence.entity.UnitJpaEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Read-model aggregation queries for the Super Admin surface.
 *
 * Every query here is deliberately UNscoped — no {@code tenantId} in the
 * {@code WHERE} clause — because the platform owner legitimately reads across
 * all landlords. This is the ONLY sanctioned location for such queries;
 * the existing module repositories intentionally remain tenant-scoped via
 * their adapter layers. Root entity is {@link TenantEntity} purely so Spring
 * Data registers this as a repository (constructor-expression {@code @Query}
 * methods may reference any mapped entity).
 */
public interface AdminReadModelRepository extends JpaRepository<TenantEntity, UUID> {

    // ---------- Overview aggregates ----------

    @Query("select new com.rentmanager.modules.platformadmin.infrastructure.persistence.projection.TenantStatusCount(t.status, count(t.id)) "
            + "from TenantEntity t group by t.status")
    List<TenantStatusCount> countTenantsByStatus();

    @Query("select count(p.id) from PropertyJpaEntity p")
    long countProperties();

    @Query("select count(u.id) from UnitJpaEntity u")
    long countUnits();

    @Query("select count(l.id) from LeaseEntity l where l.status = :status")
    long countLeasesByStatus(@Param("status") LeaseStatus status);

    @Query("select count(tp.id) from TenantProfileEntity tp")
    long countRenters();

    @Query("select new com.rentmanager.modules.platformadmin.infrastructure.persistence.projection.PaymentRequestStatusCount(r.status, count(r.id)) "
            + "from RentPaymentRequestJpaEntity r group by r.status")
    List<PaymentRequestStatusCount> countPaymentRequestsByStatus();

    @Query("select new com.rentmanager.modules.platformadmin.infrastructure.persistence.projection.DisbursementStatusCount(d.status, count(d.id)) "
            + "from DisbursementJpaEntity d group by d.status")
    List<DisbursementStatusCount> countDisbursementsByStatus();

    @Query("select count(d.id) from DisbursementJpaEntity d where d.requiresManualAttention = true")
    long countDisbursementsRequiringManualAttention();

    @Query("select coalesce(sum(rt.amount), 0) from RentTransactionJpaEntity rt where rt.occurredAt >= :start and rt.occurredAt < :end")
    BigDecimal sumAmountBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("select coalesce(sum(rt.commissionAmount), 0) from RentTransactionJpaEntity rt where rt.occurredAt >= :start and rt.occurredAt < :end")
    BigDecimal sumCommissionBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    // ---------- Landlord list aggregates (grouped by tenant) ----------

    @Query("select new com.rentmanager.modules.platformadmin.infrastructure.persistence.projection.TenantIdCount(p.tenantId, count(p.id)) "
            + "from PropertyJpaEntity p group by p.tenantId")
    List<TenantIdCount> countPropertiesByTenantGrouped();

    @Query("select new com.rentmanager.modules.platformadmin.infrastructure.persistence.projection.TenantIdCount(u.tenantId, count(u.id)) "
            + "from UnitJpaEntity u group by u.tenantId")
    List<TenantIdCount> countUnitsByTenantGrouped();

    @Query("select new com.rentmanager.modules.platformadmin.infrastructure.persistence.projection.TenantIdCount(l.tenantId, count(l.id)) "
            + "from LeaseEntity l where l.status = :status group by l.tenantId")
    List<TenantIdCount> countLeasesByTenantGroupedByStatus(@Param("status") LeaseStatus status);

    @Query("select new com.rentmanager.modules.platformadmin.infrastructure.persistence.projection.TenantIdCount(tp.tenantId, count(tp.id)) "
            + "from TenantProfileEntity tp group by tp.tenantId")
    List<TenantIdCount> countRentersByTenantGrouped();

    @Query("select new com.rentmanager.modules.platformadmin.infrastructure.persistence.projection.TenantIdMoney(rt.tenantId, sum(rt.amount), sum(rt.commissionAmount)) "
            + "from RentTransactionJpaEntity rt group by rt.tenantId")
    List<TenantIdMoney> sumTransactionAmountsByTenantGrouped();

    @Query("select new com.rentmanager.modules.platformadmin.infrastructure.persistence.projection.TenantIdLastActivity(rt.tenantId, max(rt.occurredAt)) "
            + "from RentTransactionJpaEntity rt group by rt.tenantId")
    List<TenantIdLastActivity> lastTransactionDateByTenantGrouped();

    // ---------- Landlord detail ----------

    @Query("select p from PropertyJpaEntity p where p.tenantId = :tenantId order by p.createdAt desc")
    List<PropertyJpaEntity> findPropertiesByTenant(@Param("tenantId") UUID tenantId);

    @Query("select u from UnitJpaEntity u where u.tenantId = :tenantId order by u.id desc")
    List<UnitJpaEntity> findUnitsByTenant(@Param("tenantId") UUID tenantId);

    @Query("select l from LeaseEntity l where l.tenantId = :tenantId order by l.createdAt desc")
    List<LeaseEntity> findLeasesByTenant(@Param("tenantId") UUID tenantId);

    @Query("select tp from TenantProfileEntity tp where tp.tenantId = :tenantId order by tp.id desc")
    List<TenantProfileEntity> findTenantProfilesByTenant(@Param("tenantId") UUID tenantId);

    @Query("select r from RentPaymentRequestJpaEntity r where r.tenantId = :tenantId order by r.createdAt desc")
    List<RentPaymentRequestJpaEntity> findPaymentRequestsByTenant(@Param("tenantId") UUID tenantId);

    @Query("select d from DisbursementJpaEntity d where d.tenantId = :tenantId order by d.createdAt desc")
    List<DisbursementJpaEntity> findDisbursementsByTenant(@Param("tenantId") UUID tenantId);

    @Query("select rt from RentTransactionJpaEntity rt where rt.tenantId = :tenantId order by rt.occurredAt desc")
    List<RentTransactionJpaEntity> findRecentTransactionsByTenant(@Param("tenantId") UUID tenantId, Pageable pageable);

    // ---------- Platform-wide rent payment queue ----------
    //
    // The admin overview has always returned counts of pending/paid/failed
    // payment requests, and the dashboard's alert panel linked those counts
    // to /admin/payments — a page that did not exist, because nothing could
    // list the rows behind the count. An admin who saw "3 failed payments"
    // and clicked, at the moment they most needed to act, got a 404.

    @Query("select p from RentPaymentRequestJpaEntity p order by p.createdAt desc")
    org.springframework.data.domain.Page<RentPaymentRequestJpaEntity> findAllPaymentRequests(Pageable pageable);

    @Query("select p from RentPaymentRequestJpaEntity p where p.status = :status order by p.createdAt desc")
    org.springframework.data.domain.Page<RentPaymentRequestJpaEntity> findPaymentRequestsByStatus(
            @Param("status") RentPaymentRequestStatus status, Pageable pageable);

    @Query("select p from RentPaymentRequestJpaEntity p where p.tenantId = :tenantId order by p.createdAt desc")
    org.springframework.data.domain.Page<RentPaymentRequestJpaEntity> findPaymentRequestsByTenantPaged(
            @Param("tenantId") UUID tenantId, Pageable pageable);

    @Query("select p from RentPaymentRequestJpaEntity p where p.tenantId = :tenantId and p.status = :status order by p.createdAt desc")
    org.springframework.data.domain.Page<RentPaymentRequestJpaEntity> findPaymentRequestsByTenantAndStatus(
            @Param("tenantId") UUID tenantId,
            @Param("status") RentPaymentRequestStatus status,
            Pageable pageable);

    // ---------- Platform-wide disbursement queue ----------

    @Query("select d from DisbursementJpaEntity d order by d.createdAt desc")
    org.springframework.data.domain.Page<DisbursementJpaEntity> findAllDisbursements(Pageable pageable);

    @Query("select d from DisbursementJpaEntity d where d.status = :status order by d.createdAt desc")
    org.springframework.data.domain.Page<DisbursementJpaEntity> findDisbursementsByStatus(
            @Param("status") DisbursementStatus status, Pageable pageable);

    @Query("select d from DisbursementJpaEntity d where d.tenantId = :tenantId order by d.createdAt desc")
    org.springframework.data.domain.Page<DisbursementJpaEntity> findDisbursementsByTenant(
            @Param("tenantId") UUID tenantId, Pageable pageable);

    @Query("select d from DisbursementJpaEntity d where d.tenantId = :tenantId and d.status = :status order by d.createdAt desc")
    org.springframework.data.domain.Page<DisbursementJpaEntity> findDisbursementsByTenantAndStatus(
            @Param("tenantId") UUID tenantId, @Param("status") DisbursementStatus status, Pageable pageable);

    @Query("select d from DisbursementJpaEntity d where d.requiresManualAttention = true order by d.createdAt desc")
    org.springframework.data.domain.Page<DisbursementJpaEntity> findDisbursementsRequiringManualAttention(Pageable pageable);

    // ---------- Platform-wide property list ----------

    @Query("select p from PropertyJpaEntity p order by p.createdAt desc")
    org.springframework.data.domain.Page<PropertyJpaEntity> findAllProperties(Pageable pageable);

    @Query("select p from PropertyJpaEntity p where p.id = :propertyId")
    java.util.Optional<PropertyJpaEntity> findPropertyById(@Param("propertyId") UUID propertyId);

    @Query("select p from PropertyJpaEntity p where p.tenantId = :landlordId order by p.createdAt desc")
    org.springframework.data.domain.Page<PropertyJpaEntity> findPropertiesByLandlord(
            @Param("landlordId") UUID landlordId, Pageable pageable);

    @Query("select p from PropertyJpaEntity p where "
            + "(lower(p.name) like lower(concat('%', :search, '%')) "
            + "or lower(p.referenceCode) like lower(concat('%', :search, '%'))) "
            + "order by p.createdAt desc")
    org.springframework.data.domain.Page<PropertyJpaEntity> findPropertiesBySearch(
            @Param("search") String search, Pageable pageable);

    @Query("select p from PropertyJpaEntity p where "
            + "p.tenantId = :landlordId and "
            + "(lower(p.name) like lower(concat('%', :search, '%')) "
            + "or lower(p.referenceCode) like lower(concat('%', :search, '%'))) "
            + "order by p.createdAt desc")
    org.springframework.data.domain.Page<PropertyJpaEntity> findPropertiesByLandlordAndSearch(
            @Param("landlordId") UUID landlordId,
            @Param("search") String search,
            Pageable pageable);

    @Query("select u from UnitJpaEntity u where u.propertyId in :propertyIds")
    List<UnitJpaEntity> findUnitsByPropertyIds(@Param("propertyIds") List<UUID> propertyIds);

    // ---------- Platform-wide renter list ----------

    @Query("select tp from TenantProfileEntity tp order by tp.id desc")
    org.springframework.data.domain.Page<TenantProfileEntity> findAllRenters(Pageable pageable);

    @Query("select tp from TenantProfileEntity tp where tp.tenantId = :landlordId order by tp.id desc")
    org.springframework.data.domain.Page<TenantProfileEntity> findRentersByLandlord(
            @Param("landlordId") UUID landlordId, Pageable pageable);

    @Query("select tp from TenantProfileEntity tp where "
            + "(lower(tp.fullName) like lower(concat('%', :search, '%')) "
            + "or lower(tp.email) like lower(concat('%', :search, '%')) "
            + "or lower(tp.phone) like lower(concat('%', :search, '%'))) "
            + "order by tp.id desc")
    org.springframework.data.domain.Page<TenantProfileEntity> findRentersBySearch(
            @Param("search") String search, Pageable pageable);

    @Query("select tp from TenantProfileEntity tp where "
            + "tp.tenantId = :landlordId and "
            + "(lower(tp.fullName) like lower(concat('%', :search, '%')) "
            + "or lower(tp.email) like lower(concat('%', :search, '%')) "
            + "or lower(tp.phone) like lower(concat('%', :search, '%'))) "
            + "order by tp.id desc")
    org.springframework.data.domain.Page<TenantProfileEntity> findRentersByLandlordAndSearch(
            @Param("landlordId") UUID landlordId,
            @Param("search") String search,
            Pageable pageable);

    @Query("select l from LeaseEntity l where l.tenantProfileId = :tenantProfileId and l.status = :status")
    List<LeaseEntity> findLeasesByTenantProfileIdAndStatus(
            @Param("tenantProfileId") UUID tenantProfileId,
            @Param("status") LeaseStatus status);
}