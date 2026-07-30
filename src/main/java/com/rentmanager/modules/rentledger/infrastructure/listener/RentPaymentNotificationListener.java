package com.rentmanager.modules.rentledger.infrastructure.listener;

import com.rentmanager.modules.lease.domain.model.Lease;
import com.rentmanager.modules.lease.domain.repository.LeaseRepository;
import com.rentmanager.modules.notification.sms.SmsService;
import com.rentmanager.modules.property.domain.model.Property;
import com.rentmanager.modules.property.domain.repository.PropertyRepository;
import com.rentmanager.modules.rentledger.domain.events.RentPaymentApplied;
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

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class RentPaymentNotificationListener {

    private final LeaseRepository leaseRepository;
    private final UnitRepository unitRepository;
    private final PropertyRepository propertyRepository;
    private final TenantRepository tenantRepository;
    private final TenantProfileRepository tenantProfileRepository;
    private final SmsService smsService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onRentPaymentApplied(RentPaymentApplied event) {
        try {
            UUID tenantId = event.getTenantId();
            UUID leaseId = event.getLeaseId();
            BigDecimal amount = event.getAmountApplied();
            String formattedAmount = NumberFormat.getNumberInstance(Locale.US).format(amount);

            Lease lease = leaseRepository.findByIdAndTenantId(leaseId, tenantId)
                    .orElseThrow(() -> new IllegalArgumentException("Lease not found: " + leaseId));

            TenantProfile renterProfile = tenantProfileRepository.findById(lease.getTenantProfileId())
                    .orElseThrow(() -> new IllegalArgumentException("Tenant profile not found: " + lease.getTenantProfileId()));

            Unit unit = unitRepository.findByIdAndTenantId(lease.getUnitId(), tenantId)
                    .orElseThrow(() -> new IllegalArgumentException("Unit not found: " + lease.getUnitId()));

            Property property = propertyRepository.findByIdAndTenantId(unit.getPropertyId(), tenantId)
                    .orElseThrow(() -> new IllegalArgumentException("Property not found: " + unit.getPropertyId()));

            Tenant landlord = tenantRepository.findById(tenantId)
                    .orElseThrow(() -> new IllegalArgumentException("Landlord not found: " + tenantId));

            String receiptNumber = "RCP-" + event.getTransactionId().toString().substring(0, 8).toUpperCase();

            smsService.sendRentPaymentReceivedConfirmation(
                    renterProfile.getPhone(),
                    formattedAmount,
                    receiptNumber
            );

            if (landlord.getPhoneNumber() != null && !landlord.getPhoneNumber().isBlank()) {
                smsService.sendRentPaymentNotificationToLandlord(
                        landlord.getPhoneNumber(),
                        renterProfile.getFullName(),
                        formattedAmount,
                        unit.getUnitNumber()
                );
            }
        } catch (Exception ex) {
            log.error("Failed to send rent payment notifications for event: {}", event.getEventId(), ex);
        }
    }
}
