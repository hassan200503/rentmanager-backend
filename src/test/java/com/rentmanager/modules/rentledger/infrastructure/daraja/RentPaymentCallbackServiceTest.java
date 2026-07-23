package com.rentmanager.modules.rentledger.infrastructure.daraja;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentmanager.modules.reservation.infrastructure.daraja.MpesaCallbackPayload;
import com.rentmanager.modules.rentledger.application.service.RentLedgerApplicationService;
import com.rentmanager.modules.rentledger.domain.enums.RentPaymentRequestStatus;
import com.rentmanager.modules.rentledger.domain.enums.RentTransactionSource;
import com.rentmanager.modules.rentledger.domain.enums.RentTransactionType;
import com.rentmanager.modules.rentledger.domain.exception.RentLedgerStateException;
import com.rentmanager.modules.rentledger.domain.model.RentPaymentRequest;
import com.rentmanager.modules.rentledger.domain.repository.RentPaymentRequestRepository;
import com.rentmanager.shared.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RentPaymentCallbackServiceTest {

    @Mock
    private RentPaymentRequestRepository rentPaymentRequestRepository;
    @Mock
    private RentLedgerApplicationService rentLedgerApplicationService;

    private RentPaymentCallbackService service;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = new RentPaymentCallbackService(rentPaymentRequestRepository, rentLedgerApplicationService);
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
            when(rentPaymentRequestRepository.findByMpesaCheckoutRequestId(checkoutRequestId))
                    .thenReturn(Optional.of(pendingRequest));
            when(rentPaymentRequestRepository.save(any()))
                    .thenAnswer(inv -> inv.getArgument(0));

            service.handle(successfulPayload());

            assertThat(pendingRequest.getStatus()).isEqualTo(RentPaymentRequestStatus.PAID);
            assertThat(pendingRequest.getMpesaReceiptNumber()).isEqualTo(receiptNumber);

            ArgumentCaptor<RentPaymentRequest> requestCaptor = ArgumentCaptor.forClass(RentPaymentRequest.class);
            verify(rentPaymentRequestRepository).save(requestCaptor.capture());
            assertThat(requestCaptor.getValue().getStatus()).isEqualTo(RentPaymentRequestStatus.PAID);
            assertThat(requestCaptor.getValue().getMpesaReceiptNumber()).isEqualTo(receiptNumber);

            ArgumentCaptor<String> refCaptor = ArgumentCaptor.forClass(String.class);
            verify(rentLedgerApplicationService).applyTransaction(
                    eq(tenantId),
                    eq("rent-payment-" + pendingRequest.getId()),
                    eq(rentLedgerEntryId),
                    eq(RentTransactionType.PAYMENT),
                    eq(new BigDecimal("1500.00")),
                    refCaptor.capture(),
                    eq(RentTransactionSource.MPESA),
                    eq("SYSTEM"),
                    any()
            );
            assertThat(refCaptor.getValue()).isEqualTo(receiptNumber);
        }

        @Test
        void ignoresDuplicateWhenAlreadyPaid() throws Exception {
            pendingRequest.markPaid(receiptNumber);

            when(rentPaymentRequestRepository.findByMpesaCheckoutRequestId(checkoutRequestId))
                    .thenReturn(Optional.of(pendingRequest));

            service.handle(successfulPayload());

            verify(rentLedgerApplicationService, never())
                    .applyTransaction(any(), any(), any(), any(), any(), any(), any(), any(), any());
            verify(rentPaymentRequestRepository, never()).save(any());
        }

        @Test
        void ignoresDuplicateWhenAlreadyFailed() throws Exception {
            pendingRequest.markFailed();

            when(rentPaymentRequestRepository.findByMpesaCheckoutRequestId(checkoutRequestId))
                    .thenReturn(Optional.of(pendingRequest));

            service.handle(successfulPayload());

            verify(rentLedgerApplicationService, never())
                    .applyTransaction(any(), any(), any(), any(), any(), any(), any(), any(), any());
            verify(rentPaymentRequestRepository, never()).save(any());
        }

        @Test
        void marksFailedWhenResultCodeNotZero() throws Exception {
            when(rentPaymentRequestRepository.findByMpesaCheckoutRequestId(checkoutRequestId))
                    .thenReturn(Optional.of(pendingRequest));
            when(rentPaymentRequestRepository.save(any()))
                    .thenAnswer(inv -> inv.getArgument(0));

            service.handle(failedPayload(1));

            assertThat(pendingRequest.getStatus()).isEqualTo(RentPaymentRequestStatus.FAILED);
            verify(rentPaymentRequestRepository).save(pendingRequest);
            verify(rentLedgerApplicationService, never())
                    .applyTransaction(any(), any(), any(), any(), any(), any(), any(), any(), any());
        }

        @Test
        void marksFailedWhenReceiptNumberMissing() throws Exception {
            when(rentPaymentRequestRepository.findByMpesaCheckoutRequestId(checkoutRequestId))
                    .thenReturn(Optional.of(pendingRequest));
            when(rentPaymentRequestRepository.save(any()))
                    .thenAnswer(inv -> inv.getArgument(0));

            service.handle(payloadWithoutReceipt());

            assertThat(pendingRequest.getStatus()).isEqualTo(RentPaymentRequestStatus.FAILED);
            verify(rentPaymentRequestRepository).save(pendingRequest);
            verify(rentLedgerApplicationService, never())
                    .applyTransaction(any(), any(), any(), any(), any(), any(), any(), any(), any());
        }

        @Test
        void throwsWhenCheckoutRequestIdUnknown() throws Exception {
            when(rentPaymentRequestRepository.findByMpesaCheckoutRequestId(checkoutRequestId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.handle(successfulPayload()))
                    .isInstanceOf(RentLedgerStateException.class)
                    .extracting(ex -> ((RentLedgerStateException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);

            verify(rentLedgerApplicationService, never())
                    .applyTransaction(any(), any(), any(), any(), any(), any(), any(), any(), any());
            verify(rentPaymentRequestRepository, never()).save(any());
        }
    }
}
