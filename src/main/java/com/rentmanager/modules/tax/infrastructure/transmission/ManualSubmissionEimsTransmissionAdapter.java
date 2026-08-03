package com.rentmanager.modules.tax.infrastructure.transmission;

import com.rentmanager.modules.tax.application.dto.EimsInvoiceSubmission;
import com.rentmanager.modules.tax.application.dto.TransmissionResult;
import com.rentmanager.modules.tax.application.port.EimsTransmissionPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Phase 1 stub for the KRA eTIMS integration.
 *
 * <p>Returns NOT_AVAILABLE so nothing is ever transmitted to KRA until the
 * real integration is built and configured (Phase 2/3). The invoice remains
 * PENDING with its retry bookkeeping intact, ready for the real adapter.
 */
@Slf4j
@Component
public class ManualSubmissionEimsTransmissionAdapter implements EimsTransmissionPort {

    @Override
    public TransmissionResult submitInvoice(EimsInvoiceSubmission submission) {
        log.info("eTIMS transmission requested for invoice {} but the integration is not configured "
                + "(manual submission required)", submission.invoiceId());
        return TransmissionResult.notAvailable(
                "eTIMS integration not configured — manual filing required");
    }
}