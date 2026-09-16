package com.rentmanager.modules.rentledger.api.controller;

import com.rentmanager.modules.deposit.api.controller.DepositRefundCallbackController;
import com.rentmanager.modules.deposit.application.service.DepositCommandService;
import com.rentmanager.modules.rentledger.application.service.B2CDisbursementService;
import com.rentmanager.modules.rentledger.infrastructure.daraja.DarajaB2CProperties;
import com.rentmanager.modules.reservation.infrastructure.daraja.MpesaCallbackPayload;
import com.rentmanager.modules.reservation.infrastructure.daraja.DarajaProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Provider settings are optional at startup, so an environment can run
 * without DARAJA_CALLBACK_SECRET / daraja.b2c.callback-secret. A blank
 * configured secret must then reject every callback — including one whose
 * path segment is also blank — rather than accept them.
 */
class UnconfiguredCallbackSecretTest {

    @Test
    void depositRefundCallbackRejectsWhenSecretIsBlank() {
        DepositCommandService deposits = mock(DepositCommandService.class);
        DarajaProperties props = new DarajaProperties();
        props.setCallbackSecret("");
        DepositRefundCallbackController controller = new DepositRefundCallbackController(deposits, props);

        assertThat(controller.callback("", new MpesaCallbackPayload()).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        verifyNoInteractions(deposits);
    }

    @Test
    void b2cCallbacksRejectWhenSecretIsBlank() {
        B2CDisbursementService disbursements = mock(B2CDisbursementService.class);
        DarajaB2CProperties props = new DarajaB2CProperties();
        props.setCallbackSecret("");
        B2CCallbackController controller = new B2CCallbackController(disbursements, props);

        assertThat(controller.handleResult("", UUID.randomUUID(), Map.of()).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(controller.handleTimeout("", UUID.randomUUID(), Map.of()).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        verifyNoInteractions(disbursements);
    }
}
