package com.rentmanager.modules.tax.infrastructure.listener;

import com.rentmanager.modules.lease.domain.model.Lease;
import com.rentmanager.modules.lease.domain.repository.LeaseRepository;
import com.rentmanager.modules.property.domain.enums.PremisesType;
import com.rentmanager.modules.property.domain.model.Property;
import com.rentmanager.modules.property.domain.repository.PropertyRepository;
import com.rentmanager.modules.rentledger.domain.enums.RentTransactionType;
import com.rentmanager.modules.rentledger.domain.events.RentPaymentApplied;
import com.rentmanager.modules.rentledger.infrastructure.persistence.entity.RentTransactionJpaEntity;
import com.rentmanager.modules.rentledger.infrastructure.persistence.repository.RentTransactionJpaRepository;
import com.rentmanager.modules.tax.application.config.TaxProperties;
import com.rentmanager.modules.tax.application.service.GenerateTaxInvoiceCommand;
import com.rentmanager.modules.tax.application.service.TaxInvoiceGenerationService;
import com.rentmanager.modules.tax.domain.enums.VatTreatment;
import com.rentmanager.modules.tenant.domain.model.Tenant;
import com.rentmanager.modules.tenant.domain.repository.TenantRepository;
import com.rentmanager.modules.tenant.renter.domain.model.TenantProfile;
import com.rentmanager.modules.tenant.renter.domain.repository.TenantProfileRepository;
import com.rentmanager.modules.unit.domain.model.Unit;
import com.rentmanager.modules.unit.domain.repository.UnitRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;

/**
 * Generates one eTIMS tax invoice per residential (and — once the advisor
 * sign-off completes — commercial) rent payment.
 *
 * <p>Only PAYMENT-type transactions are invoiced (WAIVER / CREDIT_APPLIED
 * are not taxable supplies). Idempotency is handled by the generation
 * service (existence check) plus the DB unique key. A missing landlord PIN
 * does NOT block the invoice — it is parked PENDING with a null landlord
 * pin and is only transmittable once the pin is supplied (Phase 2/3).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RentPaymentAppliedTaxInvoiceListener {

    private final RentTransactionJpaRepository rentTransactionJpaRepository;
    private final LeaseRepository leaseRepository;
    private final UnitRepository unitRepository;
    private final PropertyRepository propertyRepository;
    private final TenantRepository tenantRepository;
    private final TenantProfileRepository tenantProfileRepository;
    private final TaxInvoiceGenerationService taxInvoiceGenerationService;
    private final TaxProperties taxProperties;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onRentPaymentApplied(RentPaymentApplied event) {
        try {
            UUID tenantId = event.getTenantId();

            RentTransactionJpaEntity transaction = rentTransactionJpaRepository
                    .findById(event.getTransactionId())
                    .orElse(null);

            if (transaction == null) {
                log.warn("Tax invoice skipped — transaction {} not found", event.getTransactionId());
                return;
            }

            if (transaction.getType() != RentTransactionType.PAYMENT) {
                return; // WAIVER / CREDIT_APPLIED are not taxable supplies
            }

            Lease lease = leaseRepository.findByIdAndTenantId(event.getLeaseId(), tenantId)
                    .orElse(null);
            if (lease == null) {
                log.warn("Tax invoice skipped for transaction {} — lease {} not found",
                        event.getTransactionId(), event.getLeaseId());
                return;
            }

            Unit unit = unitRepository.findByIdAndTenantId(lease.getUnitId(), tenantId)
                    .orElse(null);
            if (unit == null) {
                log.warn("Tax invoice skipped for transaction {} — unit {} not found",
                        event.getTransactionId(), lease.getUnitId());
                return;
            }

            Property property = propertyRepository.findByIdAndTenantId(unit.getPropertyId(), tenantId)
                    .orElse(null);
            if (property == null) {
                log.warn("Tax invoice skipped for transaction {} — property {} not found",
                        event.getTransactionId(), unit.getPropertyId());
                return;
            }

            Tenant landlord = tenantRepository.findById(tenantId).orElse(null);

            TenantProfile renter = lease.getTenantProfileId() != null
                    ? tenantProfileRepository.findById(lease.getTenantProfileId()).orElse(null)
                    : null;

            PremisesType premisesType = property.getPremisesType();

            boolean landlordVatRegistered = landlord != null && landlord.isVatRegistered();
            VatTreatment vatTreatment = taxProperties.isVatBranchEnabled()
                    ? VatTreatment.forPremises(premisesType, landlordVatRegistered)
                    : VatTreatment.VAT_EXEMPT;

            GenerateTaxInvoiceCommand command = new GenerateTaxInvoiceCommand(
                    tenantId,
                    event.getTransactionId(),
                    transaction.getLedgerEntryId(),
                    event.getLeaseId(),
                    lease.getTenantProfileId(),
                    landlord != null ? landlord.getKraPin() : null,
                    renter != null ? renter.getKraPin() : null,
                    premisesType,
                    vatTreatment,
                    transaction.getAmount(),
                    transaction.getExternalReference(),
                    transaction.getSource() != null ? transaction.getSource().name() : "SYSTEM",
                    transaction.getOccurredAt()
            );

            taxInvoiceGenerationService.generate(command);
        } catch (Exception ex) {
            // Never break the payment flow — logging is the Phase-1 surface.
            log.error("Failed to generate tax invoice for event {}", event.getEventId(), ex);
        }
    }
}