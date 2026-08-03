package com.rentmanager.modules.tax.application.port;

import com.rentmanager.modules.tax.application.dto.EimsInvoiceSubmission;
import com.rentmanager.modules.tax.application.dto.TransmissionResult;

/**
 * Outbound port for KRA eTIMS invoice transmission (the "external world"
 * dependency of the tax module). Implementations live in
 * infrastructure/transmission.
 *
 * <p>Phase 1 ships {@code ManualSubmissionEimsTransmissionAdapter} — a
 * stub returning NOT_AVAILABLE so nothing is ever transmitted until the
 * KRA integration is built and configured (Phase 2/3).
 */
public interface EimsTransmissionPort {

    TransmissionResult submitInvoice(EimsInvoiceSubmission submission);
}
