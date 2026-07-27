package com.rentmanager.modules.rentledger.infrastructure.daraja;

import com.rentmanager.modules.lease.domain.enums.BillingCycle;
import com.rentmanager.modules.lease.domain.enums.LeaseType;
import com.rentmanager.modules.lease.domain.model.Lease;
import com.rentmanager.modules.lease.domain.repository.LeaseRepository;
import com.rentmanager.modules.rentledger.application.service.RentLedgerApplicationService;
import com.rentmanager.modules.rentledger.domain.enums.RentPaymentRequestStatus;
import com.rentmanager.modules.rentledger.domain.enums.RentTransactionType;
import com.rentmanager.modules.rentledger.domain.model.RentLedgerEntry;
import com.rentmanager.modules.rentledger.domain.model.RentPaymentRequest;
import com.rentmanager.modules.rentledger.domain.repository.RentLedgerEntryRepository;
import com.rentmanager.modules.rentledger.domain.repository.RentPaymentRequestRepository;
import com.rentmanager.modules.rentledger.domain.repository.RentTransactionRepository;
import com.rentmanager.modules.reservation.infrastructure.daraja.DarajaProperties;
import com.rentmanager.modules.reservation.infrastructure.daraja.DarajaService;
import com.rentmanager.modules.reservation.infrastructure.daraja.MpesaCallbackPayload;
import com.rentmanager.modules.support.AbstractPostgresIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Integration test for the tenant portal STK-push payment flow at the DB
 * level — proves RentPaymentInitiationService.initiate() and
 * RentPaymentCallbackService.handle() behave correctly against a real
 * Postgres container with only the HTTP-bound DarajaService mocked.
 */
class TenantPortalPaymentIntegrationTest extends AbstractPostgresIntegrationTest {

    @MockBean
    private DarajaService darajaService;

    @MockBean
    private DarajaProperties darajaProperties;

    @Autowired
    private RentPaymentInitiationService rentPaymentInitiationService;

    @Autowired
    private RentPaymentCallbackService rentPaymentCallbackService;

    @Autowired
    private RentLedgerApplicationService rentLedgerApplicationService;

    @Autowired
    private LeaseRepository leaseRepository;

    @Autowired
    private RentLedgerEntryRepository rentLedgerEntryRepository;

    @Autowired
    private RentPaymentRequestRepository rentPaymentRequestRepository;

    @Autowired
    private RentTransactionRepository rentTransactionRepository;

    @Autowired
    private EntityManager entityManager;

    private final UUID tenantId = UUID.randomUUID();
    private UUID entryId;
    private static final String MPESA_PHONE = "254712345678";
    private static final String CHECKOUT_REQUEST_ID = "ws_CO_" + UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(darajaProperties.getConsumerKey()).thenReturn("test-consumer-key");
        when(darajaProperties.getConsumerSecret()).thenReturn("test-consumer-secret");
        when(darajaProperties.getBusinessShortCode()).thenReturn("174379");
        when(darajaProperties.getPasskey()).thenReturn("test-passkey");
        when(darajaProperties.getRentPaymentCallbackUrl())
                .thenReturn("http://localhost:0/api/v1/public/rent-payments/mpesa/callback");

        when(darajaService.initiateSTKPush(
                any(), any(), any(), any(), any(), any()
        )).thenReturn(CHECKOUT_REQUEST_ID);

        UUID leaseId = UUID.randomUUID();
        Lease lease = Lease.create(
                tenantId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "LSE-IT-" + UUID.randomUUID(),
                LeaseType.FIXED_TERM,
                BillingCycle.MONTHLY,
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2027, 7, 1),
                new BigDecimal("5000.00"),
                new BigDecimal("5000.00"),
                BigDecimal.ZERO,
                5,
                false
        );
        lease = leaseRepository.save(lease);
        entityManager.flush();

        RentLedgerEntry entry = rentLedgerApplicationService.postCharge(
                tenantId, "corr-" + UUID.randomUUID(), lease.getId(),
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31), LocalDate.of(2026, 7, 1)
        );
        entityManager.flush();
        entryId = entry.getId();
    }

    @Test
    @Transactional
    void initiateCreatesPersistablePaymentRequest() {
        RentPaymentRequest request = rentPaymentInitiationService.initiate(
                tenantId, entryId, MPESA_PHONE
        );
        entityManager.flush();

        assertThat(request.getId()).isNotNull();
        assertThat(request.getTenantId()).isEqualTo(tenantId);
        assertThat(request.getRentLedgerEntryId()).isEqualTo(entryId);
        assertThat(request.getAmount()).isEqualByComparingTo("5000.00");
        assertThat(request.getMpesaCheckoutRequestId()).isEqualTo(CHECKOUT_REQUEST_ID);
        assertThat(request.getStatus()).isEqualTo(RentPaymentRequestStatus.PENDING);
        assertThat(request.getCreatedAt()).isNotNull();
        assertThat(request.getVersion()).isNotNull();

        RentPaymentRequest reloaded = rentPaymentRequestRepository
                .findById(request.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(RentPaymentRequestStatus.PENDING);
    }

    @Test
    @Transactional
    void initiateTwiceCreatesTwoRequests() {
        RentPaymentRequest first = rentPaymentInitiationService.initiate(
                tenantId, entryId, MPESA_PHONE
        );
        entityManager.flush();

        when(darajaService.initiateSTKPush(any(), any(), any(), any(), any(), any()))
                .thenReturn("ws_CO_second_" + UUID.randomUUID());

        RentPaymentRequest second = rentPaymentInitiationService.initiate(
                tenantId, entryId, MPESA_PHONE
        );
        entityManager.flush();

        assertThat(first.getId()).isNotEqualTo(second.getId());
        assertThat(first.getMpesaCheckoutRequestId()).isNotEqualTo(second.getMpesaCheckoutRequestId());
    }

    @Test
    @Transactional
    void successfulCallbackTransitionsToPaidAndCreatesPaymentTransaction() {
        RentPaymentRequest request = rentPaymentInitiationService.initiate(
                tenantId, entryId, MPESA_PHONE
        );
        entityManager.flush();
        entityManager.clear();

        MpesaCallbackPayload payload = buildSuccessPayload(
                request.getMpesaCheckoutRequestId(), "NLJ7RT61SV"
        );

        rentPaymentCallbackService.handle(payload);
        entityManager.flush();
        entityManager.clear();

        RentPaymentRequest reloaded = rentPaymentRequestRepository
                .findById(request.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(RentPaymentRequestStatus.PAID);
        assertThat(reloaded.getMpesaReceiptNumber()).isEqualTo("NLJ7RT61SV");

        var txns = rentTransactionRepository.findByLedgerEntry(tenantId, entryId);
        assertThat(txns).hasSize(2);
        assertThat(txns).anyMatch(t -> t.getType() == RentTransactionType.PAYMENT
                && "NLJ7RT61SV".equals(t.getExternalReference()));
    }

    @Test
    @Transactional
    void duplicateCallbackIsIdempotent() {
        RentPaymentRequest request = rentPaymentInitiationService.initiate(
                tenantId, entryId, MPESA_PHONE
        );
        entityManager.flush();

        MpesaCallbackPayload payload = buildSuccessPayload(
                request.getMpesaCheckoutRequestId(), "NLJ7RT61SV"
        );

        rentPaymentCallbackService.handle(payload);
        entityManager.flush();

        rentPaymentCallbackService.handle(payload);
        entityManager.flush();
        entityManager.clear();

        RentPaymentRequest reloaded = rentPaymentRequestRepository
                .findById(request.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(RentPaymentRequestStatus.PAID);

        var txns = rentTransactionRepository.findByLedgerEntry(tenantId, entryId);
        long paymentCount = txns.stream()
                .filter(t -> t.getType() == RentTransactionType.PAYMENT)
                .count();
        assertThat(paymentCount).isEqualTo(1);
    }

    @Test
    @Transactional
    void failureCallbackTransitionsToFailedNoTransaction() {
        RentPaymentRequest request = rentPaymentInitiationService.initiate(
                tenantId, entryId, MPESA_PHONE
        );
        entityManager.flush();
        entityManager.clear();

        MpesaCallbackPayload payload = buildFailurePayload(
                request.getMpesaCheckoutRequestId(), "Request cancelled by user"
        );

        rentPaymentCallbackService.handle(payload);
        entityManager.flush();
        entityManager.clear();

        RentPaymentRequest reloaded = rentPaymentRequestRepository
                .findById(request.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(RentPaymentRequestStatus.FAILED);
        assertThat(reloaded.getMpesaReceiptNumber()).isNull();

        var txns = rentTransactionRepository.findByLedgerEntry(tenantId, entryId);
        assertThat(txns).hasSize(1);
    }

    private static MpesaCallbackPayload buildSuccessPayload(
            String checkoutRequestId, String receiptNumber
    ) {
        String json = """
                {
                  "Body": {
                    "stkCallback": {
                      "CheckoutRequestID": "%s",
                      "ResultCode": 0,
                      "ResultDesc": "The service request is processed successfully.",
                      "CallbackMetadata": {
                        "Item": [
                          {"Name": "Amount", "Value": 5000},
                          {"Name": "MpesaReceiptNumber", "Value": "%s"},
                          {"Name": "TransactionDate", "Value": 20260727120000},
                          {"Name": "PhoneNumber", "Value": 254712345678}
                        ]
                      }
                    }
                  }
                }
                """.formatted(checkoutRequestId, receiptNumber);

        return parseCallbackPayload(json);
    }

    private static MpesaCallbackPayload buildFailurePayload(
            String checkoutRequestId, String reason
    ) {
        String json = """
                {
                  "Body": {
                    "stkCallback": {
                      "CheckoutRequestID": "%s",
                      "ResultCode": 1,
                      "ResultDesc": "%s"
                    }
                  }
                }
                """.formatted(checkoutRequestId, reason);

        return parseCallbackPayload(json);
    }

    private static MpesaCallbackPayload parseCallbackPayload(String json) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(json, MpesaCallbackPayload.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse callback payload", e);
        }
    }
}
