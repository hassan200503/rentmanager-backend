package com.rentmanager.modules.rentledger.infrastructure.daraja;

import com.rentmanager.modules.lease.domain.model.Lease;
import com.rentmanager.modules.lease.domain.repository.LeaseRepository;
import com.rentmanager.modules.reservation.infrastructure.daraja.DarajaProperties;
import com.rentmanager.modules.reservation.infrastructure.daraja.DarajaService;
import com.rentmanager.modules.rentledger.domain.exception.RentLedgerStateException;
import com.rentmanager.modules.rentledger.domain.model.RentLedgerEntry;
import com.rentmanager.modules.rentledger.domain.model.RentPaymentRequest;
import com.rentmanager.modules.rentledger.domain.repository.RentLedgerEntryRepository;
import com.rentmanager.modules.rentledger.domain.repository.RentPaymentRequestRepository;
import com.rentmanager.modules.tenant.domain.valueobject.DarajaCredentials;
import com.rentmanager.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Initiates an M-Pesa STK push for an outstanding {@code RentLedgerEntry}.
 * Deliberately reuses {@code DarajaService} and {@code DarajaProperties}
 * directly from the reservation module rather than duplicating a second
 * Daraja API client — both are already generic (DarajaService takes
 * credentials as a parameter; DarajaProperties is a plain
 * {@code @ConfigurationProperties} bean), so this is a reuse of a shared
 * infrastructure utility, not a business-logic coupling between modules.
 * Worth revisiting as a move to a shared/common package if a third
 * Daraja-calling module ever appears — not done here since only two exist.
 *
 * CREDENTIAL SOURCE (per payment architecture — deposits use the
 * landlord's own Daraja credentials, monthly rent flows through the
 * platform's own credentials first before B2C disbursement): builds a
 * {@code DarajaCredentials} instance from {@code DarajaProperties}'
 * platform-level consumerKey/consumerSecret/businessShortCode/passkey,
 * NOT from the landlord's own {@code Tenant.darajaCredentials}.
 *
 * PAYMENT AMOUNT: charges the entry's full {@code getBalanceOwed()}, not a
 * caller-supplied amount — this endpoint is for "pay what's currently
 * owed", not partial/arbitrary payments. A partial-payment path can be
 * added later as a separate, explicit method if needed rather than
 * overloading this one silently.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RentPaymentInitiationService {

    private final RentLedgerEntryRepository rentLedgerEntryRepository;
    private final LeaseRepository leaseRepository;
    private final RentPaymentRequestRepository rentPaymentRequestRepository;
    private final DarajaService darajaService;
    private final DarajaProperties darajaProperties;

    @Transactional
    public RentPaymentRequest initiate(
            UUID tenantId,
            UUID rentLedgerEntryId,
            String mpesaPhone
    ) {
        RentLedgerEntry entry = rentLedgerEntryRepository.findByIdAndTenantId(rentLedgerEntryId, tenantId)
                .orElseThrow(() -> new RentLedgerStateException(
                        "rent ledger entry not found: " + rentLedgerEntryId,
                        ErrorCode.RESOURCE_NOT_FOUND
                ));

        BigDecimal amount = entry.getBalanceOwed();
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RentLedgerStateException(
                    "rent ledger entry has no outstanding balance: " + rentLedgerEntryId,
                    ErrorCode.RENT_LEDGER_ENTRY_ALREADY_SETTLED
            );
        }

        // leaseId is read off the entry itself rather than accepted as a
        // separate caller-supplied parameter — the previous version took
        // rentLedgerEntryId and leaseId independently with no check that
        // they actually belonged together, which is the wrong kind of
        // caller-trust for an endpoint that moves money.
        UUID leaseId = entry.getLeaseId();
        Lease lease = leaseRepository.findByIdAndTenantId(leaseId, tenantId)
                .orElseThrow(() -> new RentLedgerStateException(
                        "lease not found: " + leaseId,
                        ErrorCode.LEASE_NOT_FOUND
                ));

        RentPaymentRequest request = RentPaymentRequest.create(tenantId, leaseId, rentLedgerEntryId, amount);
        request = rentPaymentRequestRepository.save(request);

        DarajaCredentials platformCredentials = DarajaCredentials.of(
                darajaProperties.getConsumerKey(),
                darajaProperties.getConsumerSecret(),
                darajaProperties.getBusinessShortCode(),
                darajaProperties.getPasskey()
        );

        // Explicit rent-payment callback URL — NOT the deposit flow's
        // properties.getCallbackUrl(). See DarajaService's 6-arg
        // initiateSTKPush javadoc for why this distinction is load-bearing.
        String checkoutRequestId = darajaService.initiateSTKPush(
                mpesaPhone,
                amount,
                lease.getLeaseNumber(),
                "Rent payment",
                platformCredentials,
                darajaProperties.getRentPaymentCallbackUrl()
        );

        request.attachCheckoutRequestId(checkoutRequestId);
        request = rentPaymentRequestRepository.save(request);

        log.info("Rent payment STK push initiated. leaseId={} rentLedgerEntryId={} checkoutRequestId={}",
                leaseId, rentLedgerEntryId, checkoutRequestId);

        return request;
    }
}