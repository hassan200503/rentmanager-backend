package com.rentmanager.modules.rentledger.application.service;

import com.rentmanager.modules.lease.domain.model.Lease;
import com.rentmanager.modules.lease.domain.repository.LeaseRepository;
import com.rentmanager.modules.rentledger.api.dto.response.UnmatchedPaymentResponse;
import com.rentmanager.modules.rentledger.api.dto.response.UnmatchedPaymentResponse.SuggestedUnit;
import com.rentmanager.modules.rentledger.domain.enums.RentTransactionSource;
import com.rentmanager.modules.rentledger.domain.enums.RentTransactionType;
import com.rentmanager.modules.rentledger.domain.model.UnmatchedPayment;
import com.rentmanager.modules.rentledger.domain.repository.RentLedgerEntryRepository;
import com.rentmanager.modules.rentledger.domain.repository.UnmatchedPaymentRepository;
import com.rentmanager.modules.rentledger.domain.exception.RentLedgerStateException;
import com.rentmanager.modules.tenant.renter.domain.model.TenantProfile;
import com.rentmanager.modules.tenant.renter.domain.repository.TenantProfileRepository;
import com.rentmanager.modules.unit.domain.model.Unit;
import com.rentmanager.modules.unit.domain.repository.UnitRepository;
import com.rentmanager.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UnmatchedPaymentService {

    private final UnmatchedPaymentRepository unmatchedPaymentRepository;
    private final LeaseRepository leaseRepository;
    private final UnitRepository unitRepository;
    private final TenantProfileRepository tenantProfileRepository;
    private final RentLedgerEntryRepository rentLedgerEntryRepository;
    private final RentLedgerApplicationService rentLedgerApplicationService;

    @Transactional(readOnly = true)
    public List<UnmatchedPaymentResponse> getUnmatchedPayments(UUID tenantId) {
        List<UnmatchedPayment> payments = unmatchedPaymentRepository.findByTenantIdAndResolvedFalse(tenantId);
        List<Lease> activeLeases = leaseRepository.findActiveByTenant(tenantId);

        List<UnmatchedPaymentResponse> responses = new ArrayList<>();
        for (UnmatchedPayment payment : payments) {
            responses.add(toResponse(payment, activeLeases, tenantId));
        }
        return responses;
    }

    @Transactional
    public void resolveUnmatchedPayment(UUID tenantId, UUID transactionId, UUID unitId, String resolvedBy) {
        UnmatchedPayment payment = unmatchedPaymentRepository.findByIdAndTenantId(transactionId, tenantId)
                .orElseThrow(() -> new RentLedgerStateException(
                        "Unmatched payment not found: " + transactionId,
                        ErrorCode.RESOURCE_NOT_FOUND
                ));

        if (payment.isResolved()) {
            log.warn("Unmatched payment already resolved: {}", transactionId);
            return;
        }

        unitRepository.findByIdAndTenantId(unitId, tenantId)
                .orElseThrow(() -> new RentLedgerStateException(
                        "Unit not found: " + unitId,
                        ErrorCode.RESOURCE_NOT_FOUND
                ));

        Lease activeLease = leaseRepository.findActiveLeaseByUnitIdAndTenantId(unitId, tenantId)
                .orElseThrow(() -> new RentLedgerStateException(
                        "No active lease found for unit: " + unitId,
                        ErrorCode.RESOURCE_NOT_FOUND
                ));

        UUID ledgerEntryId = findOrCreateLedgerEntry(tenantId, activeLease);
        if (ledgerEntryId == null) {
            throw new RentLedgerStateException(
                    "No ledger entry found for active lease",
                    ErrorCode.RESOURCE_NOT_FOUND
            );
        }

        String correlationId = "unmatched-payment-" + payment.getId();
        rentLedgerApplicationService.applyTransaction(
                tenantId,
                correlationId,
                ledgerEntryId,
                RentTransactionType.PAYMENT,
                payment.getAmount(),
                payment.getMpesaReceiptNumber() != null ? payment.getMpesaReceiptNumber() : payment.getTransactionId(),
                RentTransactionSource.MPESA,
                resolvedBy,
                payment.getOccurredAt()
        );

        payment.resolve(unitId, resolvedBy);
        unmatchedPaymentRepository.save(payment);

        log.info("Unmatched payment resolved. transactionId={} unitId={} resolvedBy={}",
                transactionId, unitId, resolvedBy);
    }

    private UUID findOrCreateLedgerEntry(UUID tenantId, Lease lease) {
        var entries = rentLedgerEntryRepository.findByLease(tenantId, lease.getId());
        if (!entries.isEmpty()) {
            return entries.get(0).getId();
        }
        return null;
    }

    private UnmatchedPaymentResponse toResponse(UnmatchedPayment payment, List<Lease> activeLeases, UUID tenantId) {
        List<SuggestedUnit> suggestedUnits = new ArrayList<>();
        String matchConfidence = null;

        for (Lease lease : activeLeases) {
            UUID unitId = lease.getUnitId();
            String matchReason = computeMatchReason(payment, lease, tenantId);

            if (matchReason != null) {
                String unitNumber = unitRepository.findByIdAndTenantId(unitId, tenantId)
                        .map(Unit::getUnitNumber)
                        .orElse("unknown");

                String tenantName = tenantProfileRepository.findById(lease.getTenantProfileId())
                        .map(TenantProfile::getFullName)
                        .orElse("unknown");

                if ("exact_unit_ref".equals(matchReason) && matchConfidence == null) {
                    matchConfidence = "exact";
                }

                suggestedUnits.add(new SuggestedUnit(
                        unitId,
                        unitNumber,
                        tenantName,
                        lease.getRentAmount(),
                        matchReason
                ));
            }
        }

        if (suggestedUnits.size() == 1 && matchConfidence == null) {
            matchConfidence = "amount_timing";
        }

        return new UnmatchedPaymentResponse(
                payment.getId(),
                payment.getTransactionId(),
                payment.getAmount(),
                payment.getPhoneNumber(),
                payment.getAccountReference(),
                payment.getOccurredAt(),
                matchConfidence,
                suggestedUnits
        );
    }

    private String computeMatchReason(UnmatchedPayment payment, Lease lease, UUID tenantId) {
        String accountRef = payment.getAccountReference();
        if (accountRef != null && !accountRef.isBlank()) {
            String normalizedAccountRef = normalize(accountRef);
            Unit unit = unitRepository.findByIdAndTenantId(lease.getUnitId(), tenantId).orElse(null);
            if (unit != null) {
                String normalizedUnitNumber = normalize(unit.getUnitNumber());
                if (normalizedAccountRef.equals(normalizedUnitNumber)) {
                    return "exact_unit_ref";
                }
            }
        }

        TenantProfile tenantProfile = tenantProfileRepository.findById(lease.getTenantProfileId()).orElse(null);
        if (tenantProfile != null) {
            String normalizedPaymentPhone = normalizePhone(payment.getPhoneNumber());
            String normalizedTenantPhone = normalizePhone(tenantProfile.getPhone());
            if (normalizedPaymentPhone != null && normalizedPaymentPhone.equals(normalizedTenantPhone)) {
                return "phone_match";
            }
        }

        if (payment.getAmount().compareTo(lease.getRentAmount()) == 0) {
            return "amount_match";
        }

        return null;
    }

    private String normalize(String s) {
        if (s == null) return "";
        return s.trim().toUpperCase()
                .replace("UNIT ", "").replace("APT ", "")
                .replace("HSE ", "").replace("HOUSE ", "")
                .replace("ROOM ", "").replace("NO ", "").replace("#", "")
                .replaceAll("[^A-Z0-9]", "");
    }

    private String normalizePhone(String phone) {
        if (phone == null || phone.isBlank()) return null;
        String cleaned = phone.replaceAll("[^0-9]", "");
        if (cleaned.startsWith("254") && cleaned.length() == 12) return cleaned;
        if (cleaned.startsWith("0") && cleaned.length() == 10) return "254" + cleaned.substring(1);
        if (cleaned.startsWith("7") && cleaned.length() == 9) return "254" + cleaned;
        return cleaned;
    }
}
