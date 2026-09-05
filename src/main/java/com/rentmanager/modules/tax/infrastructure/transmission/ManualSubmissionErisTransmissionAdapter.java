package com.rentmanager.modules.tax.infrastructure.transmission;

import com.rentmanager.modules.tax.application.dto.ErisMonthlyFilingSubmission;
import com.rentmanager.modules.tax.application.dto.ErisPropertyRegistrationSubmission;
import com.rentmanager.modules.tax.application.dto.TransmissionResult;
import com.rentmanager.modules.tax.application.port.ErisTransmissionPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Phase 1 stub for the KRA eRITS integration. Returns NOT_AVAILABLE so
 * nothing is transmitted until the real integration is built (Phase 2/3);
 * computed filings remain COMPUTED for the landlord to preview/approve.
 */
@Slf4j
@Component
public class ManualSubmissionErisTransmissionAdapter implements ErisTransmissionPort {

    @Override
    public TransmissionResult registerProperty(ErisPropertyRegistrationSubmission submission) {
        log.info("eRITS property registration requested for {} but the integration is not configured",
                submission.propertyId());
        return TransmissionResult.notAvailable(
                "eRITS integration not configured — manual registration required");
    }

    @Override
    public TransmissionResult submitMonthlyFiling(ErisMonthlyFilingSubmission submission) {
        // The KRA PIN is deliberately not logged — CLAUDE.md forbids it, and
        // filingId already identifies the filing uniquely for diagnostics.
        log.info("eRITS monthly filing requested for {} ({}) but the integration is not configured",
                submission.filingId(), submission.period());
        return TransmissionResult.notAvailable(
                "eRITS integration not configured — manual filing required");
    }
}