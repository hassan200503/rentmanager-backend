package com.rentmanager.modules.rentledger.infrastructure.daraja;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentmanager.modules.reservation.infrastructure.daraja.MpesaCallbackPayload;
import com.rentmanager.modules.rentledger.domain.model.RentPaymentRequest;
import com.rentmanager.modules.rentledger.domain.model.RentTransaction;
import com.rentmanager.modules.tenant.domain.model.Tenant;
import com.rentmanager.modules.tenant.domain.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RentPaymentCallbackServiceTest {

    @Mock
    private RentPaymentCallbackTransactionService txService;
    @Mock
    private TenantRepository tenantRepository;
    @Mock
    private DarajaB2CService darajaB2CService;
    // The automatic payout now delegates here rather than calling Daraja
    // directly and writing the record afterwards — see ADR-0025.
    @Mock
    private com.rentmanager.modules.rentledger.application.service.B2CDisbursementService b2cDisbursementService;

    private RentPaymentCallbackService service;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = new RentPaymentCallbackService(
                txService, tenantRepository, darajaB2CService, b2cDisbursementService
        );
    }

    @Nested
    class Handle {

        private final String checkoutRequestId = "ws_CO_1712345678";
        private final String receiptNumber = "NLJ7RT61SV";
        private UUID tenantId;
        private UUID rentLedgerEntryId;
        private RentPaymentRequest pendingRequest;

        @BeforeEach
        void setUp() {
            tenantId = UUID.randomUUID();
            rentLedgerEntryId = UUID.randomUUID();
            UUID leaseId = UUID.randomUUID();

            pendingRequest = RentPaymentRequest.create(
                    tenantId, leaseId, rentLedgerEntryId, new BigDecimal("1500.00")
            );
            pendingRequest.attachCheckoutRequestId(checkoutRequestId);
        }

        private MpesaCallbackPayload successfulPayload() throws Exception {
            String json = """
                    {
                        "Body": {
                            "stkCallback": {
                                "CheckoutRequestID": "%s",
                                "ResultCode": 0,
                                "ResultDesc": "The service request is processed successfully.",
                                "CallbackMetadata": {
                                    "Item": [
                                        {"Name": "Amount", "Value": 1500},
                                        {"Name": "MpesaReceiptNumber", "Value": "%s"},
                                        {"Name": "TransactionDate", "Value": 20260723102115},
                                        {"Name": "PhoneNumber", "Value": 254708374149}
                                    ]
                                }
                            }
                        }
                    }
                    """.formatted(checkoutRequestId, receiptNumber);
            return objectMapper.readValue(json, MpesaCallbackPayload.class);
        }

        private MpesaCallbackPayload failedPayload(int resultCode) throws Exception {
            String json = """
                    {
                        "Body": {
                            "stkCallback": {
                                "CheckoutRequestID": "%s",
                                "ResultCode": %d,
                                "ResultDesc": "Request cancelled by user"
                            }
                        }
                    }
                    """.formatted(checkoutRequestId, resultCode);
            return objectMapper.readValue(json, MpesaCallbackPayload.class);
        }

        private MpesaCallbackPayload payloadWithoutReceipt() throws Exception {
            String json = """
                    {
                        "Body": {
                            "stkCallback": {
                                "CheckoutRequestID": "%s",
                                "ResultCode": 0,
                                "ResultDesc": "Success",
                                "CallbackMetadata": {
                                    "Item": [
                                        {"Name": "Amount", "Value": 1500},
                                        {"Name": "TransactionDate", "Value": 20260723102115}
                                    ]
                                }
                            }
                        }
                    }
                    """.formatted(checkoutRequestId);
            return objectMapper.readValue(json, MpesaCallbackPayload.class);
        }

        @Test
        void handlesSuccessfulCallback() throws Exception {
            when(txService.processSuccessfulCallback(eq(checkoutRequestId), eq(receiptNumber)))
                    .thenReturn(new RentPaymentCallbackTransactionService.SuccessfulPaymentResult(
                            pendingRequest, null, null, null, null
                    ));

            service.handle(successfulPayload());

            verify(txService).processSuccessfulCallback(checkoutRequestId, receiptNumber);
        }

        @Test
        void ignoresDuplicateWhenAlreadyPaid() throws Exception {
            pendingRequest.markPaid(receiptNumber);

            when(txService.processSuccessfulCallback(eq(checkoutRequestId), eq(receiptNumber)))
                    .thenReturn(null);

            service.handle(successfulPayload());

            verify(txService).processSuccessfulCallback(checkoutRequestId, receiptNumber);
        }

        @Test
        void marksFailedWhenResultCodeNotZero() throws Exception {
            service.handle(failedPayload(1));

            verify(txService).processFailedCallback(checkoutRequestId, "Request cancelled by user");
            verify(txService, never()).processSuccessfulCallback(any(), any());
        }

        @Test
        void marksFailedWhenReceiptNumberMissing() throws Exception {
            service.handle(payloadWithoutReceipt());

            verify(txService).processFailedCallback(checkoutRequestId, "No MpesaReceiptNumber in callback");
            verify(txService, never()).processSuccessfulCallback(any(), any());
        }

        @Test
        void initiatesB2CWhenNetAmountPositive() throws Exception {
            BigDecimal netAmount = new BigDecimal("1425.00");
            when(txService.processSuccessfulCallback(eq(checkoutRequestId), eq(receiptNumber)))
                    .thenReturn(new RentPaymentCallbackTransactionService.SuccessfulPaymentResult(
                            pendingRequest, mock(RentTransaction.class), new BigDecimal("5.00"),
                            new BigDecimal("75.00"), netAmount
                    ));

            Tenant landlord = mock(Tenant.class);
            when(landlord.getPayoutPhoneNumber()).thenReturn("+254700000000");
            when(landlord.getName()).thenReturn("Test Landlord");
            when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(landlord));

            service.handle(successfulPayload());

            verify(txService).processSuccessfulCallback(checkoutRequestId, receiptNumber);

            // Re-pinned for ADR-0025. This used to assert
            // darajaB2CService.initiateB2C(...) followed by
            // txService.createDisbursement(...) — money sent first, record
            // written second. That ordering was the defect (TD-116): a
            // failure between the two left funds gone with nothing recording
            // it. The payout now goes through the same guarded method the
            // manual path uses, which reserves entitlement under a lock and
            // persists INITIATED *before* calling Daraja.
            verify(b2cDisbursementService).initiateDisbursement(
                    eq(tenantId), eq(pendingRequest.getLeaseId()),
                    eq(pendingRequest.getRentLedgerEntryId()),
                    eq(netAmount), eq("BusinessPayment"), anyString()
            );
            // The callback must no longer reach Daraja itself, and must not
            // write the row directly — both are the delegate's job now.
            verifyNoInteractions(darajaB2CService);
            verify(txService, never()).createDisbursement(
                    any(), any(), any(), any(), any(), any(), any());
        }

        @Test
        void skipsB2CWhenNetAmountIsNull() throws Exception {
            when(txService.processSuccessfulCallback(eq(checkoutRequestId), eq(receiptNumber)))
                    .thenReturn(new RentPaymentCallbackTransactionService.SuccessfulPaymentResult(
                            pendingRequest, mock(RentTransaction.class), new BigDecimal("5.00"),
                            new BigDecimal("75.00"), null
                    ));

            service.handle(successfulPayload());

            verify(txService).processSuccessfulCallback(checkoutRequestId, receiptNumber);
            verifyNoInteractions(darajaB2CService);
            verify(txService, never()).createDisbursement(any(), any(), any(), any(), any(), any(), any());
        }

        @Test
        void skipsB2CWhenNetAmountIsZero() throws Exception {
            when(txService.processSuccessfulCallback(eq(checkoutRequestId), eq(receiptNumber)))
                    .thenReturn(new RentPaymentCallbackTransactionService.SuccessfulPaymentResult(
                            pendingRequest, mock(RentTransaction.class), new BigDecimal("5.00"),
                            new BigDecimal("75.00"), BigDecimal.ZERO
                    ));

            service.handle(successfulPayload());

            verify(txService).processSuccessfulCallback(checkoutRequestId, receiptNumber);
            verifyNoInteractions(darajaB2CService);
        }

        @Test
        void skipsB2CWhenLandlordNotFound() throws Exception {
            BigDecimal netAmount = new BigDecimal("1425.00");
            when(txService.processSuccessfulCallback(eq(checkoutRequestId), eq(receiptNumber)))
                    .thenReturn(new RentPaymentCallbackTransactionService.SuccessfulPaymentResult(
                            pendingRequest, mock(RentTransaction.class), new BigDecimal("5.00"),
                            new BigDecimal("75.00"), netAmount
                    ));
            when(tenantRepository.findById(tenantId)).thenReturn(Optional.empty());

            service.handle(successfulPayload());

            verify(txService).processSuccessfulCallback(checkoutRequestId, receiptNumber);
            verifyNoInteractions(darajaB2CService);
        }

        @Test
        void skipsB2CWhenLandlordHasNoPayoutPhone() throws Exception {
            BigDecimal netAmount = new BigDecimal("1425.00");
            when(txService.processSuccessfulCallback(eq(checkoutRequestId), eq(receiptNumber)))
                    .thenReturn(new RentPaymentCallbackTransactionService.SuccessfulPaymentResult(
                            pendingRequest, mock(RentTransaction.class), new BigDecimal("5.00"),
                            new BigDecimal("75.00"), netAmount
                    ));

            Tenant landlord = mock(Tenant.class);
            when(landlord.getPayoutPhoneNumber()).thenReturn(null);
            when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(landlord));

            service.handle(successfulPayload());

            verify(txService).processSuccessfulCallback(checkoutRequestId, receiptNumber);
            verifyNoInteractions(darajaB2CService);
        }

        @Test
        void createsFailedDisbursementWhenB2CInitiationThrows() throws Exception {
            BigDecimal netAmount = new BigDecimal("1425.00");
            when(txService.processSuccessfulCallback(eq(checkoutRequestId), eq(receiptNumber)))
                    .thenReturn(new RentPaymentCallbackTransactionService.SuccessfulPaymentResult(
                            pendingRequest, mock(RentTransaction.class), new BigDecimal("5.00"),
                            new BigDecimal("75.00"), netAmount
                    ));

            Tenant landlord = mock(Tenant.class);
            when(landlord.getPayoutPhoneNumber()).thenReturn("+254700000000");
            when(landlord.getName()).thenReturn("Test Landlord");
            when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(landlord));

            // Same intent as before ADR-0025 — a payout that blows up must
            // still be recorded as FAILED and must not fail the callback,
            // because the rent is already applied and Safaricom needs its
            // 200 or it retries. Only the thing that throws has moved.
            when(b2cDisbursementService.initiateDisbursement(
                    any(), any(), any(), any(), anyString(), anyString()
            )).thenThrow(new RuntimeException("Daraja API error"));

            service.handle(successfulPayload());

            verify(txService).createFailedDisbursement(
                    eq(tenantId), eq(pendingRequest.getLeaseId()),
                    eq(pendingRequest.getRentLedgerEntryId()),
                    eq(netAmount), eq("+254700000000"), eq("Test Landlord"),
                    anyString()
            );
        }
    }
}
