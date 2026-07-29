package com.rentmanager.modules.rentledger.infrastructure.daraja;

import com.rentmanager.modules.lease.domain.model.Lease;
import com.rentmanager.modules.lease.domain.repository.LeaseRepository;
import com.rentmanager.modules.reservation.infrastructure.daraja.DarajaProperties;
import com.rentmanager.modules.reservation.infrastructure.daraja.DarajaService;
import com.rentmanager.modules.rentledger.domain.enums.RentLedgerStatus;
import com.rentmanager.modules.rentledger.domain.enums.RentPaymentRequestStatus;
import com.rentmanager.modules.rentledger.domain.exception.RentLedgerStateException;
import com.rentmanager.modules.rentledger.domain.model.RentLedgerEntry;
import com.rentmanager.modules.rentledger.domain.model.RentPaymentRequest;
import com.rentmanager.modules.rentledger.domain.repository.RentLedgerEntryRepository;
import com.rentmanager.modules.rentledger.domain.repository.RentPaymentRequestRepository;
import com.rentmanager.modules.tenant.domain.valueobject.DarajaCredentials;
import com.rentmanager.shared.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RentPaymentInitiationServiceTest {

    @Mock
    private RentLedgerEntryRepository rentLedgerEntryRepository;
    @Mock
    private LeaseRepository leaseRepository;
    @Mock
    private RentPaymentRequestRepository rentPaymentRequestRepository;
    @Mock
    private DarajaService darajaService;
    @Mock
    private DarajaProperties darajaProperties;
    @Mock
    private Lease lease;

    private RentPaymentInitiationService service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID entryId = UUID.randomUUID();
    private final UUID leaseId = UUID.randomUUID();
    private final String mpesaPhone = "+254712345678";
    private final String checkoutRequestId = "ws_CO_1712345678";
    private final String callbackUrl = "https://api.example.com/api/v1/public/rent-ledger/mpesa/callback/test-secret";

    @BeforeEach
    void setUp() {
        service = new RentPaymentInitiationService(
                rentLedgerEntryRepository, leaseRepository,
                rentPaymentRequestRepository, darajaService, darajaProperties
        );

        lenient().when(rentPaymentRequestRepository.save(any(RentPaymentRequest.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        lenient().when(darajaProperties.getConsumerKey()).thenReturn("test-consumer-key");
        lenient().when(darajaProperties.getConsumerSecret()).thenReturn("test-consumer-secret");
        lenient().when(darajaProperties.getBusinessShortCode()).thenReturn("174379");
        lenient().when(darajaProperties.getPasskey()).thenReturn("test-passkey");
        lenient().when(darajaProperties.getRentPaymentCallbackUrl()).thenReturn(callbackUrl);
    }

    @Nested
    class Initiate {

        private RentLedgerEntry entry;

        @BeforeEach
        void setUp() {
            entry = RentLedgerEntry.create(
                    tenantId, "corr", leaseId, UUID.randomUUID(), UUID.randomUUID(),
                    LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31), LocalDate.of(2026, 7, 1),
                    new BigDecimal("1500.00"), false
            );

            lenient().when(lease.getId()).thenReturn(leaseId);
            lenient().when(lease.getLeaseNumber()).thenReturn("LSE-001");
            lenient().when(darajaService.initiateSTKPush(
                    anyString(), any(), anyString(), anyString(), any(DarajaCredentials.class), anyString()))
                    .thenReturn(checkoutRequestId);
        }

        @Test
        void initiatesPaymentForEntryWithBalance() {
            when(rentLedgerEntryRepository.findByIdAndTenantId(entryId, tenantId))
                    .thenReturn(Optional.of(entry));
            when(leaseRepository.findByIdAndTenantId(leaseId, tenantId))
                    .thenReturn(Optional.of(lease));

            RentPaymentRequest result = service.initiate(tenantId, entryId, mpesaPhone);

            assertThat(result.getStatus()).isEqualTo(RentPaymentRequestStatus.PENDING);
            assertThat(result.getRentLedgerEntryId()).isEqualTo(entryId);
            assertThat(result.getLeaseId()).isEqualTo(leaseId);
            assertThat(result.getAmount()).isEqualByComparingTo("1500.00");
            assertThat(result.getMpesaCheckoutRequestId()).isEqualTo(checkoutRequestId);

            ArgumentCaptor<RentPaymentRequest> requestCaptor = ArgumentCaptor.forClass(RentPaymentRequest.class);
            verify(rentPaymentRequestRepository, times(2)).save(requestCaptor.capture());

            assertThat(requestCaptor.getAllValues().get(1).getMpesaCheckoutRequestId())
                    .isEqualTo(checkoutRequestId);

            ArgumentCaptor<DarajaCredentials> credentialsCaptor = ArgumentCaptor.forClass(DarajaCredentials.class);
            verify(darajaService).initiateSTKPush(
                    eq(mpesaPhone),
                    eq(new BigDecimal("1500.00")),
                    eq("LSE-001"),
                    eq("Rent payment"),
                    credentialsCaptor.capture(),
                    eq(callbackUrl)
            );
            assertThat(credentialsCaptor.getValue().getConsumerKey()).isEqualTo("test-consumer-key");
            assertThat(credentialsCaptor.getValue().getConsumerSecret()).isEqualTo("test-consumer-secret");
        }

        @Test
        void throwsWhenEntryNotFound() {
            when(rentLedgerEntryRepository.findByIdAndTenantId(entryId, tenantId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.initiate(tenantId, entryId, mpesaPhone))
                    .isInstanceOf(RentLedgerStateException.class)
                    .extracting(ex -> ((RentLedgerStateException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);

            verifyNoInteractions(leaseRepository, darajaService);
            verify(rentPaymentRequestRepository, never()).save(any());
        }

        @Test
        void throwsWhenBalanceAlreadySettled() {
            RentLedgerEntry settledEntry = RentLedgerEntry.rehydrate(
                    UUID.randomUUID(), tenantId, leaseId, UUID.randomUUID(), UUID.randomUUID(),
                    LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31), LocalDate.of(2026, 7, 1),
                    new BigDecimal("1500.00"), new BigDecimal("1500.00"),
                    RentLedgerStatus.PAID, false, 1L, Instant.now(), Instant.now()
            );
            when(rentLedgerEntryRepository.findByIdAndTenantId(entryId, tenantId))
                    .thenReturn(Optional.of(settledEntry));

            assertThatThrownBy(() -> service.initiate(tenantId, entryId, mpesaPhone))
                    .isInstanceOf(RentLedgerStateException.class)
                    .extracting(ex -> ((RentLedgerStateException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.RENT_LEDGER_ENTRY_ALREADY_SETTLED);

            verifyNoInteractions(leaseRepository, darajaService);
            verify(rentPaymentRequestRepository, never()).save(any());
        }

        @Test
        void throwsWhenLeaseNotFound() {
            when(rentLedgerEntryRepository.findByIdAndTenantId(entryId, tenantId))
                    .thenReturn(Optional.of(entry));
            when(leaseRepository.findByIdAndTenantId(leaseId, tenantId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.initiate(tenantId, entryId, mpesaPhone))
                    .isInstanceOf(RentLedgerStateException.class)
                    .extracting(ex -> ((RentLedgerStateException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.LEASE_NOT_FOUND);

            verify(rentPaymentRequestRepository, never()).save(any());
            verifyNoInteractions(darajaService);
        }

        @Test
        void leaseIdDerivedFromEntryNotCaller() {
            UUID entryLeaseId = UUID.randomUUID();
            RentLedgerEntry customEntry = RentLedgerEntry.create(
                    tenantId, "corr", entryLeaseId, UUID.randomUUID(), UUID.randomUUID(),
                    LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31), LocalDate.of(2026, 7, 1),
                    new BigDecimal("1500.00"), false
            );
            lenient().when(lease.getId()).thenReturn(entryLeaseId);
            when(rentLedgerEntryRepository.findByIdAndTenantId(entryId, tenantId))
                    .thenReturn(Optional.of(customEntry));
            when(leaseRepository.findByIdAndTenantId(entryLeaseId, tenantId))
                    .thenReturn(Optional.of(lease));

            service.initiate(tenantId, entryId, mpesaPhone);

            verify(leaseRepository).findByIdAndTenantId(entryLeaseId, tenantId);

            ArgumentCaptor<RentPaymentRequest> requestCaptor = ArgumentCaptor.forClass(RentPaymentRequest.class);
            verify(rentPaymentRequestRepository, atLeastOnce()).save(requestCaptor.capture());
            assertThat(requestCaptor.getAllValues().get(0).getLeaseId()).isEqualTo(entryLeaseId);
        }

        @Test
        void amountIsExactlyEntryBalanceOwed() {
            RentLedgerEntry customEntry = RentLedgerEntry.create(
                    tenantId, "corr", leaseId, UUID.randomUUID(), UUID.randomUUID(),
                    LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31), LocalDate.of(2026, 7, 1),
                    new BigDecimal("2750.50"), false
            );
            when(rentLedgerEntryRepository.findByIdAndTenantId(entryId, tenantId))
                    .thenReturn(Optional.of(customEntry));
            when(leaseRepository.findByIdAndTenantId(leaseId, tenantId))
                    .thenReturn(Optional.of(lease));

            service.initiate(tenantId, entryId, mpesaPhone);

            verify(darajaService).initiateSTKPush(
                    anyString(), eq(new BigDecimal("2750.50")), anyString(), anyString(),
                    any(DarajaCredentials.class), anyString()
            );

            ArgumentCaptor<RentPaymentRequest> requestCaptor = ArgumentCaptor.forClass(RentPaymentRequest.class);
            verify(rentPaymentRequestRepository, atLeastOnce()).save(requestCaptor.capture());
            assertThat(requestCaptor.getAllValues().get(0).getAmount()).isEqualByComparingTo("2750.50");
        }
    }

    @Nested
    class InitiateWithAmount {

        private final BigDecimal customAmount = new BigDecimal("500.00");
        private RentLedgerEntry entry;

        @BeforeEach
        void setUp() {
            entry = RentLedgerEntry.create(
                    tenantId, "corr", leaseId, UUID.randomUUID(), UUID.randomUUID(),
                    LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31), LocalDate.of(2026, 7, 1),
                    new BigDecimal("1500.00"), false
            );

            lenient().when(lease.getId()).thenReturn(leaseId);
            lenient().when(lease.getLeaseNumber()).thenReturn("LSE-001");
            lenient().when(darajaService.initiateSTKPush(
                    anyString(), any(), anyString(), anyString(), any(DarajaCredentials.class), anyString()))
                    .thenReturn(checkoutRequestId);
        }

        @Test
        void acceptsCustomAmount() {
            when(rentLedgerEntryRepository.findByIdAndTenantId(entryId, tenantId))
                    .thenReturn(Optional.of(entry));
            when(leaseRepository.findByIdAndTenantId(leaseId, tenantId))
                    .thenReturn(Optional.of(lease));

            RentPaymentRequest result = service.initiateWithAmount(tenantId, entryId, customAmount, mpesaPhone);

            assertThat(result.getAmount()).isEqualByComparingTo(customAmount);
            assertThat(result.getStatus()).isEqualTo(RentPaymentRequestStatus.PENDING);
            verify(darajaService).initiateSTKPush(
                    eq(mpesaPhone), eq(customAmount), anyString(), anyString(),
                    any(DarajaCredentials.class), anyString()
            );
        }

        @Test
        void passesCustomAmountUnchanged() {
            BigDecimal partialAmount = new BigDecimal("750.25");
            when(rentLedgerEntryRepository.findByIdAndTenantId(entryId, tenantId))
                    .thenReturn(Optional.of(entry));
            when(leaseRepository.findByIdAndTenantId(leaseId, tenantId))
                    .thenReturn(Optional.of(lease));

            RentPaymentRequest result = service.initiateWithAmount(tenantId, entryId, partialAmount, mpesaPhone);

            assertThat(result.getAmount()).isEqualByComparingTo("750.25");
        }

        @Test
        void throwsWhenAmountIsNull() {
            assertThatThrownBy(() -> service.initiateWithAmount(tenantId, entryId, null, mpesaPhone))
                    .isInstanceOf(RentLedgerStateException.class)
                    .extracting(ex -> ((RentLedgerStateException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.RENT_TRANSACTION_INVALID_AMOUNT);
            verifyNoInteractions(rentLedgerEntryRepository, leaseRepository, darajaService);
        }

        @Test
        void throwsWhenAmountIsZero() {
            assertThatThrownBy(() -> service.initiateWithAmount(tenantId, entryId, BigDecimal.ZERO, mpesaPhone))
                    .isInstanceOf(RentLedgerStateException.class)
                    .extracting(ex -> ((RentLedgerStateException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.RENT_TRANSACTION_INVALID_AMOUNT);
        }

        @Test
        void throwsWhenAmountIsNegative() {
            assertThatThrownBy(() -> service.initiateWithAmount(tenantId, entryId, new BigDecimal("-100.00"), mpesaPhone))
                    .isInstanceOf(RentLedgerStateException.class)
                    .extracting(ex -> ((RentLedgerStateException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.RENT_TRANSACTION_INVALID_AMOUNT);
        }

        @Test
        void throwsWhenEntryNotFound() {
            when(rentLedgerEntryRepository.findByIdAndTenantId(entryId, tenantId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.initiateWithAmount(tenantId, entryId, customAmount, mpesaPhone))
                    .isInstanceOf(RentLedgerStateException.class)
                    .extracting(ex -> ((RentLedgerStateException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
            verifyNoInteractions(leaseRepository, darajaService);
        }

        @Test
        void throwsWhenLeaseNotFound() {
            when(rentLedgerEntryRepository.findByIdAndTenantId(entryId, tenantId))
                    .thenReturn(Optional.of(entry));
            when(leaseRepository.findByIdAndTenantId(leaseId, tenantId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.initiateWithAmount(tenantId, entryId, customAmount, mpesaPhone))
                    .isInstanceOf(RentLedgerStateException.class)
                    .extracting(ex -> ((RentLedgerStateException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.LEASE_NOT_FOUND);
            verifyNoInteractions(darajaService);
        }

        @Test
        void usesPlatformCredentials() {
            when(rentLedgerEntryRepository.findByIdAndTenantId(entryId, tenantId))
                    .thenReturn(Optional.of(entry));
            when(leaseRepository.findByIdAndTenantId(leaseId, tenantId))
                    .thenReturn(Optional.of(lease));

            service.initiateWithAmount(tenantId, entryId, customAmount, mpesaPhone);

            ArgumentCaptor<DarajaCredentials> credentialsCaptor = ArgumentCaptor.forClass(DarajaCredentials.class);
            verify(darajaService).initiateSTKPush(
                    anyString(), any(), anyString(), anyString(),
                    credentialsCaptor.capture(), anyString()
            );
            assertThat(credentialsCaptor.getValue().getConsumerKey()).isEqualTo("test-consumer-key");
            assertThat(credentialsCaptor.getValue().getConsumerSecret()).isEqualTo("test-consumer-secret");
        }

        @Test
        void savesPendingBeforeStkCall() {
            when(rentLedgerEntryRepository.findByIdAndTenantId(entryId, tenantId))
                    .thenReturn(Optional.of(entry));
            when(leaseRepository.findByIdAndTenantId(leaseId, tenantId))
                    .thenReturn(Optional.of(lease));

            service.initiateWithAmount(tenantId, entryId, customAmount, mpesaPhone);

            InOrder inOrder = inOrder(rentPaymentRequestRepository, darajaService);
            inOrder.verify(rentPaymentRequestRepository).save(any(RentPaymentRequest.class));
            inOrder.verify(darajaService).initiateSTKPush(anyString(), any(), anyString(), anyString(),
                    any(DarajaCredentials.class), anyString());
            inOrder.verify(rentPaymentRequestRepository).save(any(RentPaymentRequest.class));
        }
    }
}
